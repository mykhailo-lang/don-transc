package com.example.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object TranscriberWorkManager {
    fun scheduleQueueWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15, // 15 seconds backoff start
                TimeUnit.SECONDS
            )
            .addTag("UploadQueueWork")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "UploadQueueWork",
            ExistingWorkPolicy.REPLACE,
            uploadWorkRequest
        )
    }
}
