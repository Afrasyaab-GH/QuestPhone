package neth.iecal.questphone.app.screens.quest.view.ai_snap

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import neth.iecal.questphone.AiSnapQuestViewVM
import neth.iecal.questphone.R
import nethical.questphone.data.EvaluationStep
import java.io.File
import android.content.Context
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import neth.iecal.questphone.app.screens.quest.setup.ai_snap.model.ModelDownloadDialog

@SuppressLint("DefaultLocale")
@Composable
fun AiEvaluationScreen(
    onDismiss:()-> Unit,
    viewModel: AiSnapQuestViewVM
) {
    val context = LocalContext.current
    val photoFile = File(context.filesDir, AI_SNAP_PIC)

    // State variables
    val currentStep by viewModel.currentStep.collectAsState()
    val error by viewModel.error.collectAsState()
    val results by viewModel.results.collectAsState()
    val modelDownloadDialogVisible = remember { mutableStateOf(false) }

    if (modelDownloadDialogVisible.value) {
        ModelDownloadDialog(
            allowSkipping = true,
            modelDownloadDialogVisible = modelDownloadDialogVisible
        )
    }

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.error.value = null
        }
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = viewModel.commonQuestInfo.title,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom= 8.dp).fillMaxWidth()

        )

        if (photoFile.exists()) {
            ScanningImageCard(
                photoFile = photoFile,
                isScanning = currentStep != EvaluationStep.COMPLETED && error == null,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Status Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            when {
                currentStep != EvaluationStep.COMPLETED && error == null -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Progress bar
                            LinearProgressIndicator(
                                progress = currentStep.progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Circular progress indicator
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Current step text
                            Text(
                                text = currentStep.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Progress percentage
                            Text(
                                text = "${(currentStep.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                results != null -> {
                    val isSuccess = results!!.isValid
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSuccess)
                                Color(0xFF4CAF50).copy(alpha = 0.1f)
                            else
                                Color(0xFFFF9800).copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = if (isSuccess) "Valid" else "Invalid",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFFF9800)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Result: ${isSuccess}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Reason: ${results?.reason}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            if (!isSuccess) {
                                val settingsSp = context.getSharedPreferences("private_settings", android.content.Context.MODE_PRIVATE)
                                val initialEngine = settingsSp.getString("validation_engine", "cloud") ?: "cloud"
                                val storedApiKey = settingsSp.getString("gemini_api_key", "") ?: ""

                                var selectedEngine by remember { mutableStateOf(initialEngine) }
                                var apiKeyInput by remember { mutableStateOf(storedApiKey) }

                                val modelsSp = context.getSharedPreferences("models", Context.MODE_PRIVATE)
                                val currentModel = modelsSp.getString("selected_one_shot_model", "online") ?: "online"

                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "AI Validation Engine",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val engines = listOf(
                                    "cloud" to "QuestPhone Cloud Server",
                                    "gemini_api" to "Private Gemini API Key",
                                    "local" to "Local On-Device AI"
                                )

                                Column(Modifier.selectableGroup()) {
                                    engines.forEach { (engineKey, label) ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .selectable(
                                                    selected = (selectedEngine == engineKey),
                                                    onClick = { selectedEngine = engineKey },
                                                    role = Role.RadioButton
                                                ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = (selectedEngine == engineKey),
                                                onClick = null
                                            )
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }

                                if (selectedEngine == "gemini_api") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = apiKeyInput,
                                        onValueChange = { apiKeyInput = it },
                                        label = { Text("Gemini API Key") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else if (selectedEngine == "local") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Model: $currentModel",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = { modelDownloadDialogVisible.value = true },
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text("Select Model")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        if (selectedEngine == "gemini_api" && apiKeyInput.isBlank()) {
                                            Toast.makeText(context, "Please enter a valid API Key", Toast.LENGTH_SHORT).show()
                                        } else {
                                            settingsSp.edit().apply {
                                                putString("validation_engine", selectedEngine)
                                                if (selectedEngine == "gemini_api") {
                                                    putString("gemini_api_key", apiKeyInput.trim())
                                                }
                                            }.apply()
                                            Toast.makeText(context, "Engine settings updated! Retrying...", Toast.LENGTH_SHORT).show()
                                            viewModel.resetResults()
                                            viewModel.evaluateQuest(onDismiss)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Save & Retry Validation")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                viewModel.resetResults()
                                onDismiss() }) {
                                Text(text = if(isSuccess) "Close" else "Retake")
                            }
                        }
                    }
                }
                error != null -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.baseline_error_24),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = error!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { onDismiss() }) {
                                Text(text = "Close")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanningImageCard(
    photoFile: File,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val primary = MaterialTheme.colorScheme.primary
    val pulseAnimation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    val glowAnimation = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = modifier
            .size(180.dp)
    ) {
        // Main Image
        Card(
            elevation = CardDefaults.cardElevation(8.dp),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            val painter = rememberAsyncImagePainter(
                model = photoFile,
                contentScale = ContentScale.Crop
            )
            Image(
                painter = painter,
                contentDescription = "Captured Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Radial Pulse Animation Overlay
        if (isScanning) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                val maxRadius = size.width * 0.8f

                // Pulsating circle
                drawCircle(
                    color = primary.copy(alpha = 0.3f * (1f - pulseAnimation.value)),
                    radius = maxRadius * pulseAnimation.value,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 4.dp.toPx())
                )

                // Inner glow effect
                drawCircle(
                    color = primary.copy(alpha = glowAnimation.value),
                    radius = maxRadius * 0.3f * pulseAnimation.value,
                    center = Offset(centerX, centerY)
                )

                // Subtle grid lines (sci-fi effect)
                val gridSpacing = 20.dp.toPx()
                var x = 0f
                while (x <= size.width) {
                    drawLine(
                        color = primary.copy(alpha = 0.1f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    x += gridSpacing
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(
                        color = primary.copy(alpha = 0.1f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += gridSpacing
                }
            }

            // Pulsating overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        primary.copy(alpha = 0.05f * (1f - pulseAnimation.value))
                    )
            )
        }
    }
}

fun getBitmapFromPath(path: String): Bitmap? {
    return BitmapFactory.decodeFile(path)
}