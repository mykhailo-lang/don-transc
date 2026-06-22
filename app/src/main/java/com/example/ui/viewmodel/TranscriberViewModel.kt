package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.QueueItem
import com.example.data.SettingsManager
import com.example.service.AudioRecorderService
import com.example.worker.TranscriberWorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TranscriberViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val queueDao = db.queueDao()
    val settingsManager = SettingsManager(application)

    // Observable Flows from Service
    val isRecording = AudioRecorderService.isRecording
    val recordedSeconds = AudioRecorderService.recordedSeconds
    val currentVolumeDb = AudioRecorderService.currentVolumeDb

    // Local Queue flow
    val queueItems: StateFlow<List<QueueItem>> = queueDao.getAllItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _batteryOptimizedIgnored = MutableStateFlow(false)
    val batteryOptimizedIgnored = _batteryOptimizedIgnored.asStateFlow()

    // Screen navigation state (simple enum)
    enum class Screen {
        RECORDER, QUEUE, SETTINGS
    }
    private val _currentScreen = MutableStateFlow(Screen.RECORDER)
    val currentScreen = _currentScreen.asStateFlow()

    // Temporary input fields
    val clientNameInput = MutableStateFlow(settingsManager.lastClientName)
    val serverUrlInput = MutableStateFlow(settingsManager.serverUrl)
    val userIdInput = MutableStateFlow(settingsManager.userId)
    val modeInput = MutableStateFlow(settingsManager.mode) // LOCAL or SERVER
    val languageInput = MutableStateFlow(settingsManager.language) // uk or ru

    init {
        checkBatteryOptimizationStatus()
    }

    fun setScreen(screen: Screen) {
        _currentScreen.value = screen
    }

    fun startRecording(clientName: String) {
        val finalName = clientName.ifBlank { "Клієнт" }
        settingsManager.lastClientName = finalName
        
        val context = getApplication<Application>()
        val intent = Intent(context, AudioRecorderService::class.java).apply {
            action = AudioRecorderService.ACTION_START
            putExtra("CLIENT_NAME", finalName)
        }
        context.startService(intent)
    }

    fun stopRecording() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioRecorderService::class.java).apply {
            action = AudioRecorderService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun saveSettings() {
        settingsManager.serverUrl = serverUrlInput.value
        settingsManager.userId = userIdInput.value
        settingsManager.mode = modeInput.value
        settingsManager.language = languageInput.value
    }

    fun resetSettingsInputs() {
        serverUrlInput.value = settingsManager.serverUrl
        userIdInput.value = settingsManager.userId
        modeInput.value = settingsManager.mode
        languageInput.value = settingsManager.language
    }

    fun checkBatteryOptimizationStatus() {
        val context = getApplication<Application>()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        _batteryOptimizedIgnored.value = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestBatteryOptimizationExempt() {
        val context = getApplication<Application>()
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to regular settings intent
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    }

    fun forceSyncQueue() {
        TranscriberWorkManager.scheduleQueueWorker(getApplication())
    }

    fun deleteItem(item: QueueItem) {
        viewModelScope.launch {
            queueDao.deleteItem(item)
            // If file still there, delete too
            try {
                val f = java.io.File(item.filePath)
                if (f.exists()) f.delete()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun retryItem(item: QueueItem) {
        viewModelScope.launch {
            item.status = "WAITING"
            item.errorMessage = null
            queueDao.updateItem(item)
            forceSyncQueue()
        }
    }
}
