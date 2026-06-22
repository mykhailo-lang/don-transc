package com.example.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.QueueItem
import com.example.data.SettingsManager
import com.example.utils.PcmToWavConverter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorderService : Service() {

    companion object {
        private const val TAG = "AudioRecorderService"
        private const val CHANNEL_ID = "RecorderServiceChannel"
        private const val NOTIFICATION_ID = 101

        const val ACTION_START = "com.example.service.START"
        const val ACTION_STOP = "com.example.service.STOP"

        private val _isRecording = MutableStateFlow(false)
        val isRecording = _isRecording.asStateFlow()

        private val _recordedSeconds = MutableStateFlow(0)
        val recordedSeconds = _recordedSeconds.asStateFlow()

        private val _currentVolumeDb = MutableStateFlow(0f)
        val currentVolumeDb = _currentVolumeDb.asStateFlow()

        // Max segment duration (e.g., 10 minutes) before auto-split to prevent memory or thermals overload
        private const val MAX_SEGMENT_MS = 10 * 60 * 1000L 
    }

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + job)
    
    private var audioRecord: AudioRecord? = null
    private val recordingInProgress = AtomicBoolean(false)
    private var rawFile: File? = null
    private var startTimeMillis = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val clientName = intent.getStringExtra("CLIENT_NAME") ?: "Клієнт"
                startRecordingService(clientName)
            }
            ACTION_STOP -> {
                stopRecordingService()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecordingService(clientName: String) {
        if (_isRecording.value) return

        _isRecording.value = true
        _recordedSeconds.value = 0
        _currentVolumeDb.value = 0f
        recordingInProgress.set(true)
        startTimeMillis = System.currentTimeMillis()

        // Battery optimization / Doze mode WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TranscriberApp::RecordingWakeLock").apply {
            acquire(MAX_SEGMENT_MS + 2000L) // Safe limit
        }

        // Run Foreground service
        val notification = createNotification("Запис розпочато: розмова з $clientName...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ requires specification of foregroundServiceType
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startWorkRecordThread(clientName)
    }

    private fun startWorkRecordThread(clientName: String) {
        serviceScope.launch {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size parameters")
                stopRecordingService()
                return@launch
            }

            try {
                @SuppressLint("MissingPermission")
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                ).also { recorder ->
                    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "AudioRecord could not be initialized")
                        stopRecordingService()
                        return@launch
                    }
                    recorder.startRecording()
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Microphone permission missing in record thread", e)
                stopRecordingService()
                return@launch
            }

            // Create temp raw files
            val baseName = "rec_${System.currentTimeMillis()}"
            rawFile = File(cacheDir, "$baseName.raw")
            val wavFile = File(filesDir, "$baseName.wav")

            val buffer = ShortArray(bufferSize)
            
            // Duration timer job
            val timerJob = launch(Dispatchers.Main) {
                while (recordingInProgress.get() && isActive) {
                    delay(1000)
                    _recordedSeconds.value += 1
                    updateNotification("Запис... Тривалість: ${_recordedSeconds.value}с | $clientName")
                }
            }

            try {
                FileOutputStream(rawFile).use { fos ->
                    while (recordingInProgress.get()) {
                        val currentTime = System.currentTimeMillis()
                        // Safe Segment cutoff check (Do not blow memory, auto-segment long recordings)
                        if (currentTime - startTimeMillis > MAX_SEGMENT_MS) {
                            Log.w(TAG, "Max segment reached, auto cutting.")
                            break
                        }

                        val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                        if (readCount > 0) {
                            // Convert ShortArray to ByteArray
                            val byteBuffer = ByteArray(readCount * 2)
                            var maxAbs = 0
                            for (i in 0 until readCount) {
                                val shortVal = buffer[i]
                                val absVal = java.lang.Math.abs(shortVal.toInt())
                                if (absVal > maxAbs) maxAbs = absVal

                                byteBuffer[i * 2] = (shortVal.toInt() and 0xff).toByte()
                                byteBuffer[i * 2 + 1] = ((shortVal.toInt() shr 8) and 0xff).toByte()
                            }
                            // Calculate volume dB
                            val volumeDb = if (maxAbs > 0) {
                                20 * Math.log10(maxAbs / 32767.0) + 90
                            } else 0.0
                            _currentVolumeDb.value = volumeDb.toFloat()

                            fos.write(byteBuffer)
                        } else {
                            delay(10)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing raw PCM file", e)
            } finally {
                timerJob.cancel()
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed stopping/releasing AudioRecord", e)
                }
                audioRecord = null
                
                // Convert PCM (WAV converter takes RAW PCM and saves to high-quality WAV)
                val raw = rawFile
                if (raw != null && raw.exists() && raw.length() > 0) {
                    PcmToWavConverter.rawToWav(raw, wavFile, sampleRate)
                    
                    // Save queue item to local Database
                    val durationMs = System.currentTimeMillis() - startTimeMillis
                    saveToQueueDatabase(wavFile.absolutePath, wavFile.name, durationMs, clientName)
                    
                    // Clean raw file
                    raw.delete()
                }
            }
            
            stopSelf()
        }
    }

    private fun saveToQueueDatabase(filePath: String, fileName: String, durationMs: Long, clientName: String) {
        val settings = SettingsManager(this)
        val queueItem = QueueItem(
            filePath = filePath,
            fileName = fileName,
            userId = settings.userId,
            clientName = clientName,
            status = "WAITING",
            mode = settings.mode,
            audioDurationMs = durationMs
        )
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@AudioRecorderService)
                db.queueDao().insertItem(queueItem)
                
                // Enqueue immediate WorkManager task
                scheduleSyncQueue(this@AudioRecorderService)
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting item into database", e)
            }
        }
    }

    private fun scheduleSyncQueue(context: Context) {
        // We'll call workmanager scheduler from a separate coordinator or directly via reflection/intent
        val intent = Intent(context, AudioRecorderService::class.java).apply {
            // Trigger background queue sync
        }
        // WorkManager kickoff trigger context
        com.example.worker.TranscriberWorkManager.scheduleQueueWorker(context)
    }

    private fun stopRecordingService() {
        if (!_isRecording.value) return
        recordingInProgress.set(false)
        _isRecording.value = false
        _currentVolumeDb.value = 0f

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Диктофон та Транскрибатор")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Працює запис розмови розпізнавачем",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopRecordingService()
        job.cancel()
        super.onDestroy()
    }
}
