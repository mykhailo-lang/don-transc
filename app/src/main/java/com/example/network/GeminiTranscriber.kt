package com.example.network

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object GeminiTranscriber {
    private const val TAG = "GeminiTranscriber"

    suspend fun transcribeAudio(audioFile: File, language: String): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }
        
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is empty or placeholder")
            return@withContext "Помилка: Відсутній унікальний ключ API (GEMINI_API_KEY). Додайте його в панель Secrets для повноцінної транскрибації."
        }

        try {
            val bytes = audioFile.readBytes()
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
            
            // Using gemini-3.5-flash as default for structured text and audio transcription
            val modelName = "gemini-3.5-flash"
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            
            val systemInstruction = """
                Транскрибуй цей аудіозапис розмови між Менеджером та Клієнтом.
                Мова запису: ${if (language == "uk") "Українська" else "Російська"}. Спілкування ведеться українською або російською мовою.
                Будь ласка, розподіли репліки по черзі у форматі:
                Менеджер: ...
                Клієнт: ...
                
                Вкажи результати максимально точно, видали тексти-паразити та фонові шуми. Поверни суто транскрибований розбір розмови українською чи російською відповідно до оригінального аудіо.
            """.trimIndent()
            
            val jsonRequest = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemInstruction)
                            })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "audio/wav")
                                    put("data", base64Data)
                                })
                            })
                        }
                        put("parts", partsArray)
                    })
                }
                put("contents", contentsArray)
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            val requestBody = jsonRequest.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBodyStr = response.body?.string() ?: ""
                val responseJson = JSONObject(responseBodyStr)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val contentObj = candidate.optJSONObject("content")
                    if (contentObj != null) {
                        val parts = contentObj.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return@withContext parts.getJSONObject(0).optString("text", "Без виявленого тексту розмови.")
                        }
                    }
                }
                return@withContext "Помилка розбору результатів транскрибації."
            } else {
                val errBody = response.body?.string() ?: ""
                Log.e(TAG, "Gemini API Error: ${response.code} msg: $errBody")
                return@withContext "Помилка розпізнавання: API код ${response.code}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini transcription failed", e)
            return@withContext "Помилка під час транскрибації: ${e.localizedMessage}"
        }
    }
}
