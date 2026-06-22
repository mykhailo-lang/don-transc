package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.SettingsManager
import com.example.network.GeminiTranscriber
import com.example.network.NetworkClient
import com.example.network.SQLProxyRequest
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class UploadWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "UploadWorker"
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = AppDatabase.getDatabase(context)
        val dao = db.queueDao()
        val settings = SettingsManager(context)

        val pendingItems = dao.getPendingItems()
        if (pendingItems.isEmpty()) {
            return Result.success()
        }

        var hasFailure = false

        for (item in pendingItems) {
            try {
                // Update status to processing
                item.status = "PROCESSING"
                dao.updateItem(item)

                val file = File(item.filePath)

                // High-priority check: if the item ALREADY has local transcription text,
                // we skip file existence checks & brand new transcription, and directly try to push it to the remote API.
                if (item.transcription.isNotBlank()) {
                    val apiService = NetworkClient.createApiService(settings.serverUrl)
                    val requestPayload = SQLProxyRequest(
                        userId = item.userId,
                        clientName = item.clientName,
                        timestamp = item.timestamp,
                        transcription = item.transcription,
                        language = settings.language
                    )
                    try {
                        val response = apiService.sendLocalTranscription(requestPayload)
                        if (response.isSuccessful && response.body()?.success == true) {
                            item.status = "SENT"
                            item.errorMessage = null
                            dao.updateItem(item)
                            
                            // Delete WAV file if it still exists
                            if (file.exists()) {
                                file.delete()
                            }
                        } else {
                            val errMsg = response.body()?.message ?: "Помилка сервера: код ${response.code()}"
                            item.status = "SAVED_LOCAL"
                            item.errorMessage = "MSSQL не підключено: $errMsg. Збережено в додатку."
                            dao.updateItem(item)
                        }
                    } catch (e: Exception) {
                        item.status = "SAVED_LOCAL"
                        item.errorMessage = "MSSQL не підключено: немає зв'язку. Збережено в додатку."
                        dao.updateItem(item)
                    }
                    continue
                }

                if (!file.exists()) {
                    item.status = "ERROR"
                    item.errorMessage = "Файл запису не знайдено на пристрої"
                    dao.updateItem(item)
                    continue
                }

                if (item.mode == "LOCAL") {
                    // Step 1: Speak recognition using our robust local fallback transcriber
                    val transcriptionText = GeminiTranscriber.transcribeAudio(file, settings.language)
                    
                    if (transcriptionText.startsWith("Помилка")) {
                        item.status = "ERROR"
                        item.errorMessage = transcriptionText
                        dao.updateItem(item)
                        hasFailure = true
                        continue
                    }

                    // Step 2: Push completed transcription to manager's custom remote rest API
                    val apiService = NetworkClient.createApiService(settings.serverUrl)
                    val requestPayload = SQLProxyRequest(
                        userId = item.userId,
                        clientName = item.clientName,
                        timestamp = item.timestamp,
                        transcription = transcriptionText,
                        language = settings.language
                    )

                    try {
                        val response = apiService.sendLocalTranscription(requestPayload)
                        if (response.isSuccessful && response.body()?.success == true) {
                            item.status = "SENT"
                            item.transcription = transcriptionText
                            item.errorMessage = null
                            dao.updateItem(item)
                            
                            // Safe file deletion to spare storage blocks
                            file.delete()
                        } else {
                            val errMsg = response.body()?.message ?: "Помилка сервера: код ${response.code()}"
                            Log.w(TAG, "MSSQL proxy responded with error: $errMsg. Saving transcription locally inside the app.")
                            item.status = "SAVED_LOCAL"
                            item.transcription = transcriptionText
                            item.errorMessage = "MSSQL не підключено: $errMsg. Збережено в додатку."
                            dao.updateItem(item)
                            file.delete()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed connection to MSSQL proxy: ${e.localizedMessage}. Saving transcription locally inside the app.")
                        item.status = "SAVED_LOCAL"
                        item.transcription = transcriptionText
                        item.errorMessage = "MSSQL не підключено: немає зв'язку. Збережено в додатку."
                        dao.updateItem(item)
                        file.delete()
                    }

                } else {
                    // Mode is SERVER: Multipart audio file upload
                    val apiService = NetworkClient.createApiService(settings.serverUrl)
                    
                    val fileBody = file.asRequestBody("audio/wav".toMediaTypeOrNull())
                    val audioPart = MultipartBody.Part.createFormData("audio", file.name, fileBody)
                    
                    val userIdBody = item.userId.toRequestBody("text/plain".toMediaTypeOrNull())
                    val clientNameBody = item.clientName.toRequestBody("text/plain".toMediaTypeOrNull())
                    val languageBody = settings.language.toRequestBody("text/plain".toMediaTypeOrNull())

                    try {
                        val uploadResponse = apiService.uploadAudio(audioPart, userIdBody, clientNameBody, languageBody)
                        if (uploadResponse.isSuccessful && uploadResponse.body() != null) {
                            val taskId = uploadResponse.body()!!.taskId
                            
                            // Start polling statuses
                            var isCompleted = false
                            var pollCount = 0
                            val maxPolls = 60 // Max 5 mins

                            while (!isCompleted && pollCount < maxPolls) {
                                delay(5000)
                                pollCount++

                                val statusResponse = apiService.checkServerStatus(taskId)
                                if (statusResponse.isSuccessful && statusResponse.body() != null) {
                                    val body = statusResponse.body()!!
                                    when (body.status.lowercase()) {
                                        "completed", "done", "success" -> {
                                            item.status = "SENT"
                                            item.transcription = body.transcription ?: "Транскрибований текст відсутній"
                                            item.errorMessage = null
                                            dao.updateItem(item)
                                            
                                            // Purge audio file from storage limits
                                            file.delete()
                                            isCompleted = true
                                        }
                                        "failed", "error" -> {
                                            Log.w(TAG, "Server transcription failed. Trying local Gemini fallback...")
                                            val localText = GeminiTranscriber.transcribeAudio(file, settings.language)
                                            if (!localText.startsWith("Помилка")) {
                                                item.status = "SAVED_LOCAL"
                                                item.transcription = localText
                                                item.errorMessage = "Сервер не зміг розпізнати: ${body.message}. Збережено локальну розшифровку."
                                                dao.updateItem(item)
                                                file.delete()
                                            } else {
                                                item.status = "ERROR"
                                                item.errorMessage = "Сервер не розпізнав аудіо: ${body.message}. Локальний відкат не вдався."
                                                dao.updateItem(item)
                                                hasFailure = true
                                            }
                                            isCompleted = true
                                        }
                                        else -> {
                                            // server still performing speech recognition (processing)
                                        }
                                    }
                                } else {
                                    Log.e(TAG, "Status polling got connection code: ${statusResponse.code()}")
                                }
                            }

                            if (!isCompleted) {
                                Log.w(TAG, "Server polling timed out. Trying local Gemini fallback...")
                                val localText = GeminiTranscriber.transcribeAudio(file, settings.language)
                                if (!localText.startsWith("Помилка")) {
                                    item.status = "SAVED_LOCAL"
                                    item.transcription = localText
                                    item.errorMessage = "Таймаут сервера. Збережено локальну розшифровку у додатку."
                                    dao.updateItem(item)
                                    file.delete()
                                } else {
                                    item.status = "ERROR"
                                    item.errorMessage = "Таймаут очікування закінчення обробки на сервері. Локальний відкат не вдався."
                                    dao.updateItem(item)
                                    hasFailure = true
                                }
                            }

                        } else {
                            val errStr = "Код ${uploadResponse.code()}"
                            Log.w(TAG, "Server rejected upload ($errStr). Trying local Gemini fallback...")
                            val localText = GeminiTranscriber.transcribeAudio(file, settings.language)
                            if (!localText.startsWith("Помилка")) {
                                item.status = "SAVED_LOCAL"
                                item.transcription = localText
                                item.errorMessage = "Сервер відхилив завантаження ($errStr). Збережено локальну розшифровку."
                                dao.updateItem(item)
                                file.delete()
                            } else {
                                item.status = "ERROR"
                                item.errorMessage = "Сервер відхилив завантаження: $errStr. Локальний відкат не вдався."
                                dao.updateItem(item)
                                hasFailure = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Server connection failed: ${e.localizedMessage}. Trying local Gemini fallback...")
                        val localText = GeminiTranscriber.transcribeAudio(file, settings.language)
                        if (!localText.startsWith("Помилка")) {
                            item.status = "SAVED_LOCAL"
                            item.transcription = localText
                            item.errorMessage = "MSSQL не підключено: немає зв'язку. Збережено локальну розшифровку."
                            dao.updateItem(item)
                            file.delete()
                        } else {
                            item.status = "ERROR"
                            item.errorMessage = "Помилка завантаження/з'єднання із сервером: ${e.localizedMessage}. Локальний відкат не вдався."
                            dao.updateItem(item)
                            hasFailure = true
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "UploadWorker crashed on execution on $item", e)
                item.status = "ERROR"
                item.errorMessage = "Критична помилка: ${e.localizedMessage}"
                dao.updateItem(item)
                hasFailure = true
            }
        }

        return if (hasFailure) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
