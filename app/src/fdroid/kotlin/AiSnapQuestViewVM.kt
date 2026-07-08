package neth.iecal.questphone

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import neth.iecal.questphone.app.screens.quest.view.ViewQuestVM
import neth.iecal.questphone.app.screens.quest.view.ai_snap.AI_SNAP_PIC
import neth.iecal.questphone.app.screens.quest.view.ai_snap.getBitmapFromPath
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.StatsRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import nethical.questphone.ai.padTokenIds
import nethical.questphone.ai.preprocessBitmapToFloatBuffer
import nethical.questphone.ai.tokenizeText
import nethical.questphone.backend.TaskValidationClient
import nethical.questphone.data.EvaluationStep
import nethical.questphone.data.json
import nethical.questphone.data.quest.ai.snap.AiSnap
import java.io.File
import java.nio.LongBuffer
import javax.inject.Inject
import neth.iecal.questphone.core.Supabase
import io.github.jan.supabase.auth.auth
import androidx.core.graphics.scale
import kotlin.random.Random
import kotlinx.coroutines.delay


const val MINIMUM_ZERO_SHOT_THRESHOLD = 0.08

@HiltViewModel
class AiSnapQuestViewVM @Inject constructor(
    questRepository: QuestRepository,
    userRepository: UserRepository,
    statsRepository: StatsRepository,
    application: Application,
) : ViewQuestVM(
    questRepository, userRepository, statsRepository, application,
){
    val isAiEvaluating = MutableStateFlow(false)
    val isCameraScreen = MutableStateFlow(false)
    var aiQuest = AiSnap()


    val currentStep = MutableStateFlow(EvaluationStep.INITIALIZING)
    val error = MutableStateFlow<String?>(null)
    val results = MutableStateFlow<TaskValidationClient.ValidationResult?>(null)
    val isModelDownloaded = MutableStateFlow(true)


    private var modelSession: OrtSession? = null
    private var env: OrtEnvironment? = null
    private var isModelLoaded = false

    private lateinit var modelId: String

    private var isOnlineInferencing = false

    private val client = TaskValidationClient()
    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadModel()
        }
    }

    fun setAiSnap(){
        aiQuest = json.decodeFromString<AiSnap>(commonQuestInfo.quest_json)
    }

    fun onAiSnapQuestDone(){
        saveMarkedQuestToDb()
        isCameraScreen.value = false
    }


    fun loadModel(): Boolean {
        return try {
            val sp = application.getSharedPreferences("models", Context.MODE_PRIVATE)
            val currentSelectedModel = sp.getString("selected_one_shot_model", "online") ?: "online"
            if (isModelLoaded && ::modelId.isInitialized && modelId == currentSelectedModel) return true

            currentStep.value = EvaluationStep.CHECKING_MODEL
            env = OrtEnvironment.getEnvironment()
            modelId = currentSelectedModel
            Log.d("Loading mode", modelId)
            if (modelId == "online") {
                isModelLoaded = true
                isOnlineInferencing = true
                return true
            } else {
                isOnlineInferencing = false
            }

            Log.d("Loading Model", "Starting to load model $modelId ")
            val modelFile = File(application.filesDir, "$modelId.onnx")
            if (!modelFile.exists()) {
                isModelDownloaded.value = false
                val reasonMsg = if (sp.contains("downloading")) {
                    "Please wait until the model fully downloads"
                } else {
                    "Model not found. Please click the model icon in the top right to download it"
                }
                results.value = TaskValidationClient.ValidationResult(
                    isValid = false,
                    reason = reasonMsg
                )
                currentStep.value = EvaluationStep.COMPLETED
                isModelLoaded = false
                return false
            }
            currentStep.value = EvaluationStep.LOADING_MODEL
            val opts = SessionOptions().apply {
                addNnapi()
                setExecutionMode(SessionOptions.ExecutionMode.SEQUENTIAL)
                setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)
            }

            modelSession = env!!.createSession(modelFile.absolutePath, opts)
            isModelLoaded = true
            return true
        } catch (e: Exception) {
            results.value = TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Failed to load model: ${e.message}"
            )
            currentStep.value = EvaluationStep.COMPLETED
            isModelLoaded = false
            false
        }
    }

    fun evaluateQuest(onEvaluationComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isModelLoaded && !loadModel()) return@launch
            if(!isOnlineInferencing) {
                runOfflineInference(onEvaluationComplete)
            } else {
                runOnlineInference(onEvaluationComplete)
            }

        }
    }

    private suspend fun runOnlineInference(onEvaluationComplete: () -> Unit) {
        currentStep.value = EvaluationStep.INITIALIZING
        currentStep.value = EvaluationStep.LOADING_MODEL
        val photoFile = java.io.File(application.filesDir, AI_SNAP_PIC)
        val compressedFile = resizeAndCompressImage(photoFile, 1080, 50)

        val settingsSp = application.getSharedPreferences("private_settings", android.content.Context.MODE_PRIVATE)
        val geminiKey = settingsSp.getString("gemini_api_key", null)

        if (!geminiKey.isNullOrBlank()) {
            val geminiValidator = nethical.questphone.backend.GeminiValidator()
            geminiValidator.validateTask(
                compressedFile,
                aiQuest.taskDescription,
                aiQuest.features.joinToString(","),
                geminiKey
            ) { result ->
                results.value = result.getOrNull() ?: TaskValidationClient.ValidationResult(
                    isValid = false,
                    reason = "Gemini validation failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                )
                currentStep.value = EvaluationStep.COMPLETED
                if (results.value?.isValid == true) {
                    onEvaluationComplete()
                }
            }
        } else {
            val token = if (userRepository.userInfo.isAnonymous) {
                ""
            } else {
                Supabase.supabase.auth.currentAccessTokenOrNull()?.toString() ?: ""
            }

            if (token.isEmpty()) {
                results.value = TaskValidationClient.ValidationResult(
                    isValid = false,
                    reason = "Authentication required. Please configure a private Gemini API Key in your Profile settings to validate quests offline."
                )
                currentStep.value = EvaluationStep.COMPLETED
                return
            }

            client.validateTask(
                compressedFile,
                aiQuest.taskDescription,
                aiQuest.features.joinToString(","),
                token
            ) { result ->
                results.value = result.getOrNull() ?: TaskValidationClient.ValidationResult(
                    isValid = false,
                    reason = "Online validation failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                )
                currentStep.value = EvaluationStep.COMPLETED
                if (results.value?.isValid == true) {
                    onEvaluationComplete()
                }
            }
        }

        val allSteps = EvaluationStep.entries
        var currentStepInt = 0
        while (results.value == null) {
            delay(Random.nextInt(500, 2000).toLong())
            currentStep.value = EvaluationStep.valueOf(allSteps[currentStepInt].name)
            if (currentStepInt != EvaluationStep.EVALUATING.ordinal) currentStepInt++
        }
    }

    fun resetResults(){
        isAiEvaluating.value = true
        results.value = null
    }

    private suspend fun runOfflineInference(onEvaluationComplete: () -> Unit){
        try {
            currentStep.value = EvaluationStep.INITIALIZING


            val photoFile = File(application.filesDir, AI_SNAP_PIC)
            currentStep.value = EvaluationStep.LOADING_IMAGE

            if (!photoFile.exists()) {
                error.value = "Image file not found at ${photoFile.absolutePath}"
                Log.d("Not Found",photoFile.absolutePath)
                return
            }

            val bitmap = getBitmapFromPath(photoFile.absolutePath)

            currentStep.value = EvaluationStep.PREPROCESSING

            val queries = aiQuest.features.ifEmpty { listOf(aiQuest.taskDescription) }
            val processedQueries = queries.map { "$it </s>" }

            currentStep.value = EvaluationStep.TOKENIZING

            val tokenIdsList = try {
                tokenizeText(application, processedQueries)
            } catch (e: Exception) {
                error.value = "Tokenization failed: ${e.message}"
                return
            }

            val imageTensor = OnnxTensor.createTensor(
                env,
                preprocessBitmapToFloatBuffer(bitmap!!),
                longArrayOf(1, 3, 224, 224)
            )

            val maxLength = 64
            val padTokenId = 0
            val paddedTokenIdsList = tokenIdsList.map { padTokenIds(it, maxLength, padTokenId) }
            val flatTokenIds =
                paddedTokenIdsList.flatMap { it.asList() }.map { it.toLong() }.toLongArray()

            val textTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(flatTokenIds),
                longArrayOf(paddedTokenIdsList.size.toLong(), maxLength.toLong())
            )

            currentStep.value = EvaluationStep.EVALUATING

            val inputs = mapOf(
                "pixel_values" to imageTensor,
                "input_ids" to textTensor
            )

            val output = modelSession?.run(inputs)
            val logitsTensor = output?.get(0) as? OnnxTensor
            val logitsArray = logitsTensor?.floatBuffer?.array() ?: return

            val probs = logitsArray.map { 1f / (1f + kotlin.math.exp(-it)) }
            val sorted = queries.mapIndexed { i, q -> q to probs[i] }
            val detectedFeatures = sorted.filter { it.second > MINIMUM_ZERO_SHOT_THRESHOLD }.map { it.first }

            // Local Gemini Nano reasoning pipeline integration
            val localNano = neth.iecal.questphone.core.utils.LocalGeminiNanoValidator(application)
            if (localNano.isAvailable()) {
                val nanoResult = localNano.validateTaskLocally(aiQuest.taskDescription, detectedFeatures)
                results.value = nanoResult
                currentStep.value = EvaluationStep.COMPLETED
                if (nanoResult.isValid) {
                    onEvaluationComplete()
                }
                return
            }

            // Fallback: Default SigLIP verification logic
            val isSuccess = if (aiQuest.features.isEmpty()) {
                sorted.isNotEmpty() && sorted[0].second > MINIMUM_ZERO_SHOT_THRESHOLD
            } else {
                detectedFeatures.isNotEmpty()
            }

            results.value = TaskValidationClient.ValidationResult(
                isSuccess,
                if (isSuccess) {
                    "Detected: " + detectedFeatures.joinToString(", ")
                } else {
                    "Validation failed. No matching features detected."
                }
            )
            currentStep.value = EvaluationStep.COMPLETED

            if (isSuccess) {
                onEvaluationComplete()
            }

        } catch (e: Exception) {
            error.value = "Evaluation failed: ${e.message}"
        }
    }
    override fun onCleared() {
        super.onCleared()
        try {
            modelSession?.close()
            env?.close()
            modelSession = null
            env = null
            isModelLoaded = false
        } catch (e: Exception) {
            Log.e("AiEvaluation", "Failed to close resources", e)
        }
    }


}
fun resizeAndCompressImage(file: java.io.File, maxSize: Int = 1080, quality: Int = 70): java.io.File {
    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)

    // Maintain aspect ratio
    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
    val width: Int
    val height: Int
    if (ratio > 1) {
        width = maxSize
        height = (maxSize / ratio).toInt()
    } else {
        height = maxSize
        width = (maxSize * ratio).toInt()
    }

    val scaledBitmap = bitmap.scale(width, height)

    val compressedFile = java.io.File(file.parent, "compressed_upload.jpg")
    val out = java.io.FileOutputStream(compressedFile)
    scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
    out.flush()
    out.close()

    return compressedFile
}