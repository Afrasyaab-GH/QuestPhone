package neth.iecal.questphone.core.utils

import android.content.Context
import android.util.Log
import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.GenerationConfig
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nethical.questphone.backend.TaskValidationClient

class LocalGeminiNanoValidator(private val context: Context) {

    private var generativeModel: GenerativeModel? = null
    private var isPrepared = false

    companion object {
        private const val TAG = "LocalGeminiNano"
    }

    init {
        try {
            val downloadCallback = object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) {
                    Log.i(TAG, "Gemini Nano download started. Total bytes to download: $bytesToDownload")
                }

                override fun onDownloadProgress(progress: Long) {
                    Log.i(TAG, "Gemini Nano download progress: $progress")
                }

                override fun onDownloadCompleted() {
                    Log.i(TAG, "Gemini Nano download completed")
                    CoroutineScope(Dispatchers.IO).launch {
                        prepareEngine()
                    }
                }

                override fun onDownloadFailed(failureStatus: String, e: GenerativeAIException) {
                    Log.e(TAG, "Gemini Nano download failed: $failureStatus", e)
                }
            }

            val downloadConfig = DownloadConfig(downloadCallback)

            val generationConfig = GenerationConfig.Builder().build()

            generativeModel = GenerativeModel(
                generationConfig = generationConfig,
                downloadConfig = downloadConfig
            )
            CoroutineScope(Dispatchers.IO).launch {
                prepareEngine()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "AICore / Gemini Nano not supported on this device: ${e.message}")
            generativeModel = null
        }
    }

    private suspend fun prepareEngine() {
        val model = generativeModel ?: return
        try {
            model.prepareInferenceEngine()
            isPrepared = true
            Log.i(TAG, "Gemini Nano inference engine prepared successfully")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to prepare local inference engine: ${e.message}")
        }
    }

    fun isAvailable(): Boolean {
        return generativeModel != null && isPrepared
    }

    suspend fun validateTaskLocally(
        taskDescription: String,
        detectedFeatures: List<String>
    ): TaskValidationClient.ValidationResult {
        val model = generativeModel
        if (model == null || !isPrepared) {
            return TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Local Gemini Nano is not active or not prepared on this device."
            )
        }

        val prompt = """
            You are a supportive, encouraging local AI Habit Coach.
            The user's goal description: "$taskDescription"
            The objects/features detected in their environment: ${detectedFeatures.joinToString(", ")}.
            
            Determine if the user successfully completed their goal based on the detected objects.
            Respond exactly with a JSON object:
            {
               "is_valid": true/false,
               "reason": "encouraging feedback or specific instructions on what is missing"
            }
        """.trimIndent()

        return try {
            val response = model.generateContent(prompt)
            val responseText = response.text ?: ""
            
            val isValid = responseText.contains("\"is_valid\"\\s*:\\s*true".toRegex(RegexOption.IGNORE_CASE))
            val reasonStart = responseText.indexOf("\"reason\"")
            val reason = if (reasonStart != -1) {
                responseText.substring(reasonStart + 8)
                    .substringAfter(":")
                    .substringBefore("}")
                    .trim('"', ' ', '\n', '\r')
            } else {
                "Goal validated successfully!"
            }
            TaskValidationClient.ValidationResult(isValid, reason)
        } catch (e: Throwable) {
            Log.e(TAG, "Local reasoning execution failed", e)
            TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "On-device AI reasoning error: ${e.message}"
            )
        }
    }
}
