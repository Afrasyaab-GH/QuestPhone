package nethical.questphone.backend

import android.util.Base64
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiValidator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GeminiValidator"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    fun validateTask(
        imageFile: File,
        description: String,
        features: String,
        apiKey: String,
        callback: (Result<TaskValidationClient.ValidationResult>) -> Unit
    ) {
        val base64Image = try {
            Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
        } catch (e: Exception) {
            callback(Result.failure(IOException("Failed to read/encode image: ${e.message}")))
            return
        }

        // Construct the prompt
        val promptText = "You are an encouraging, supportive automated AI Quest Validator and Habit Coach for a productivity/app blocker. " +
                "The user has a quest task with the description: \"$description\" and features: \"$features\". " +
                "Evaluate the uploaded image to determine if the user has successfully completed this task. " +
                "If the task is completed, mark 'is_valid' as true and write a warm, encouraging, celebratory message congratulating them. " +
                "If the task is NOT completed or some features are missing, mark 'is_valid' as false and provide a friendly, helpful, and highly specific checklist or feedback detailing exactly what is missing, what remains to be done, or what they can improve to complete their quest (e.g., 'I see you\\'ve cleared the books, but the empty coffee cup is still on the desk. Just put it away to finish your quest!'). " +
                "Respond with a JSON object containing: 'is_valid' (boolean) and 'reason' (string)."

        // Build Gemini request body
        val requestJson = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", promptText)
                        })
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("responseSchema", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("is_valid", JSONObject().apply { put("type", "BOOLEAN") })
                        put("reason", JSONObject().apply { put("type", "STRING") })
                    })
                    put("required", org.json.JSONArray().apply {
                        put("is_valid")
                        put("reason")
                    })
                })
            })
        }

        val requestBody = requestJson.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Gemini API request failed: ${e.message}", e)
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "No details"
                        Log.e(TAG, "Gemini API server error: $errorBody")
                        callback(Result.failure(IOException("Gemini API error: ${response.code} - $errorBody")))
                        return
                    }

                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        Log.e(TAG, "Empty response from Gemini API")
                        callback(Result.failure(IOException("Empty response from Gemini API")))
                        return
                    }

                    try {
                        val rootJson = JSONObject(responseBody)
                        val candidates = rootJson.getJSONArray("candidates")
                        if (candidates.length() == 0) {
                            callback(Result.failure(IOException("No completion candidates returned by Gemini")))
                            return
                        }
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        if (parts.length() == 0) {
                            callback(Result.failure(IOException("No content parts returned by Gemini")))
                            return
                        }
                        val textResult = parts.getJSONObject(0).getString("text")

                        val resultJson = JSONObject(textResult)
                        val validationResult = TaskValidationClient.ValidationResult(
                            isValid = resultJson.getBoolean("is_valid"),
                            reason = resultJson.getString("reason")
                        )
                        Log.i(TAG, "Gemini validation result: $validationResult")
                        callback(Result.success(validationResult))
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON parsing error: ${e.message}", e)
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }
}
