package com.example.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

data class SQLProxyRequest(
    val userId: String,
    val clientName: String,
    val timestamp: Long,
    val transcription: String,
    val language: String
)

data class SQLProxyResponse(
    val success: Boolean,
    val message: String?
)

data class UploadResponse(
    val taskId: String,
    val status: String,
    val message: String?
)

data class StatusResponse(
    val taskId: String,
    val status: String, // "processing", "completed", "failed"
    val transcription: String?,
    val isDiarized: Boolean?,
    val message: String?
)

interface ApiService {
    @POST("api/transcription")
    suspend fun sendLocalTranscription(
        @Body request: SQLProxyRequest
    ): Response<SQLProxyResponse>

    @Multipart
    @POST("api/upload")
    suspend fun uploadAudio(
        @Part audio: MultipartBody.Part,
        @Part("userId") userId: RequestBody,
        @Part("clientName") clientName: RequestBody,
        @Part("language") language: RequestBody
    ): Response<UploadResponse>

    @GET("api/status/{taskId}")
    suspend fun checkServerStatus(
        @Path("taskId") taskId: String
    ): Response<StatusResponse>
}
