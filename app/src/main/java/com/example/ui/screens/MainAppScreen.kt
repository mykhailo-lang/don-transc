package com.example.ui.screens

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.QueueItem
import com.example.ui.viewmodel.TranscriberViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: TranscriberViewModel) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordedSeconds by viewModel.recordedSeconds.collectAsStateWithLifecycle()
    val currentVolumeDb by viewModel.currentVolumeDb.collectAsStateWithLifecycle()
    val queueItems by viewModel.queueItems.collectAsStateWithLifecycle()
    val batteryOptimizedIgnored by viewModel.batteryOptimizedIgnored.collectAsStateWithLifecycle()

    // Observe settings inputs mapped inside viewmodel
    val clientName by viewModel.clientNameInput.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrlInput.collectAsStateWithLifecycle()
    val userId by viewModel.userIdInput.collectAsStateWithLifecycle()
    val mode by viewModel.modeInput.collectAsStateWithLifecycle()
    val language by viewModel.languageInput.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = "Транскрибатор",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        // User Avatar representation from HTML design
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEADDFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "User info",
                                tint = Color(0xFF21005D),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = currentScreen == TranscriberViewModel.Screen.RECORDER,
                    onClick = { viewModel.setScreen(TranscriberViewModel.Screen.RECORDER) },
                    icon = { Icon(Icons.Default.Mic, "Запис") },
                    label = { Text("Головна", fontSize = 11.sp, fontWeight = if (currentScreen == TranscriberViewModel.Screen.RECORDER) FontWeight.Bold else FontWeight.Medium) },
                    modifier = Modifier.testTag("nav_recording")
                )
                NavigationBarItem(
                    selected = currentScreen == TranscriberViewModel.Screen.QUEUE,
                    onClick = { viewModel.setScreen(TranscriberViewModel.Screen.QUEUE) },
                    icon = {
                        BadgedBox(badge = {
                            val waitingCount = queueItems.count { it.status == "WAITING" || it.status == "PROCESSING" }
                            if (waitingCount > 0) {
                                Badge { Text(waitingCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.History, "Черга")
                        }
                    },
                    label = { Text("Історія", fontSize = 11.sp, fontWeight = if (currentScreen == TranscriberViewModel.Screen.QUEUE) FontWeight.Bold else FontWeight.Medium) },
                    modifier = Modifier.testTag("nav_queue")
                )
                NavigationBarItem(
                    selected = currentScreen == TranscriberViewModel.Screen.SETTINGS,
                    onClick = { viewModel.setScreen(TranscriberViewModel.Screen.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, "Налаштування") },
                    label = { Text("Налаштування", fontSize = 11.sp, fontWeight = if (currentScreen == TranscriberViewModel.Screen.SETTINGS) FontWeight.Bold else FontWeight.Medium) },
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    TranscriberViewModel.Screen.RECORDER -> {
                        RecorderScreenView(
                            isRecording = isRecording,
                            recordedSeconds = recordedSeconds,
                            volumeDb = currentVolumeDb,
                            clientName = clientName,
                            onClientNameChange = { viewModel.clientNameInput.value = it },
                            onStartClick = { viewModel.startRecording(clientName) },
                            onStopClick = { viewModel.stopRecording() },
                            managerId = userId
                        )
                    }
                    TranscriberViewModel.Screen.QUEUE -> {
                        QueueScreenView(
                            queueItems = queueItems,
                            onDelete = { viewModel.deleteItem(it) },
                            onRetry = { viewModel.retryItem(it) },
                            onForceSync = { viewModel.forceSyncQueue() }
                        )
                    }
                    TranscriberViewModel.Screen.SETTINGS -> {
                        SettingsScreenView(
                            serverUrl = serverUrl,
                            onServerUrlChange = { viewModel.serverUrlInput.value = it },
                            userId = userId,
                            onUserIdChange = { viewModel.userIdInput.value = it },
                            mode = mode,
                            onModeChange = { viewModel.modeInput.value = it },
                            language = language,
                            onLanguageChange = { viewModel.languageInput.value = it },
                            isBatteryOptimizedExempt = batteryOptimizedIgnored,
                            onRequestBatteryExemption = { viewModel.requestBatteryOptimizationExempt() },
                            onSave = {
                                viewModel.saveSettings()
                                Toast.makeText(context, "Параметри успішно збережено!", Toast.LENGTH_SHORT).show()
                            },
                            onReset = { viewModel.resetSettingsInputs() }
                        )
                    }
                }
            }
        }
    }
}

// FORMAT TIME UTILITY
fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

// FORMAT MILLISECONDS UTILITY
fun formatMsDuration(ms: Long): String {
    val totalSecs = (ms / 1000).toInt()
    return formatDuration(totalSecs)
}

// FORMAT DATETIME UTILITY
fun formatTimestamp(timeMs: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timeMs))
}

@Composable
fun AudioWaveformVisualizer(isActive: Boolean, volumeDb: Float) {
    val animatedVolume = remember { Animatable(30f) }
    LaunchedEffect(volumeDb, isActive) {
        if (isActive) {
            animatedVolume.animateTo(
                targetValue = volumeDb.coerceIn(30f, 95f),
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)
            )
        } else {
            animatedVolume.animateTo(30f, animationSpec = tween(300))
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "wave_offset")
    val phaseOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val maxAmplitude = (animatedVolume.value - 30f) * 1.3f

        // Draw center horizontal baseline
        drawLine(
            color = Color.LightGray.copy(alpha = 0.2f),
            start = androidx.compose.ui.geometry.Offset(0f, centerY),
            end = androidx.compose.ui.geometry.Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        val path = androidx.compose.ui.graphics.Path()
        path.moveTo(0f, centerY)

        for (x in 0..width.toInt() step 6) {
            val radians = (x.toFloat() / width) * 3f * Math.PI.toFloat() + phaseOffset
            val sine = Math.sin(radians.toDouble()).toFloat()
            val y = centerY + sine * maxAmplitude
            path.lineTo(x.toFloat(), y)
        }

        drawPath(
            path = path,
            color = if (isActive) Color(0xFF10B981) else Color.Gray.copy(alpha = 0.5f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw secondary slightly shifted wave for richer feel
        val path2 = androidx.compose.ui.graphics.Path()
        path2.moveTo(0f, centerY)
        for (x in 0..width.toInt() step 6) {
            val radians = (x.toFloat() / width) * 3f * Math.PI.toFloat() - phaseOffset + (Math.PI / 2).toFloat()
            val sine = Math.sin(radians.toDouble()).toFloat()
            val y = centerY + sine * (maxAmplitude * 0.5f)
            path2.lineTo(x.toFloat(), y)
        }

        drawPath(
            path = path2,
            color = if (isActive) Color(0xFF34D399).copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.3f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun RecorderScreenView(
    isRecording: Boolean,
    recordedSeconds: Int,
    volumeDb: Float,
    clientName: String,
    onClientNameChange: (String) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    managerId: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section with manager / client input
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant background indicator from the HTML design
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .background(Color(0xFFE6F4EA), RoundedCornerShape(50.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF137333).copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRecording) "Запис триває в фоні" else "Фонова робота активна",
                        color = Color(0xFF137333),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Minimalist Client Name & Manager Info Block
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "👤 Дані розмови",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Менеджер: $managerId",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = clientName,
                        onValueChange = onClientNameChange,
                        placeholder = { Text("Введіть ПІБ або ID Клієнта", fontSize = 14.sp) },
                        singleLine = true,
                        enabled = !isRecording,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("client_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                    )
                }
            }
        }

        // Center section: Waveform, Timer, Trigger
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            // Live Waveform Visualizer
            AudioWaveformVisualizer(isActive = isRecording, volumeDb = volumeDb)

            Spacer(modifier = Modifier.height(16.dp))

            // Timer (tabular-nums, normal weight, elegant tracker)
            Text(
                text = formatDuration(recordedSeconds),
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1.5).sp,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Large tactile start button
            Box(
                modifier = Modifier
                    .size(192.dp)
                    .shadow(elevation = 12.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) Color(0xFFB3261E) else MaterialTheme.colorScheme.primary
                    )
                    .clickable {
                        if (isRecording) onStopClick() else onStartClick()
                    }
                    .testTag("record_trigger_btn"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        // Stop square icon
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.White, RoundedCornerShape(4.dp))
                        )
                    } else {
                        // Record circle core
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }

            // Minimal layout text label matching HTML "ПОЧАТИ ЗАПИС" style
            Text(
                text = if (isRecording) "ЗУПИНИТИ" else "ПОЧАТИ ЗАПИС",
                modifier = Modifier.padding(top = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontSize = 11.sp
            )
        }

        // Bottom disclaimer/status info hints
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (recordedSeconds > 300) { // 5 minutes split warning
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEC)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1E3AD))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Warning", tint = Color(0xFFA07800), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Система оптимізує пам'ять при перевищенні 5-ти хвилин ліміту.",
                            fontSize = 11.sp,
                            color = Color(0xFFA07800),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Text(
                    text = "Запис здійснюється в PCM 16kHz mono. Розмова автоматично збережеться в історію.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

@Composable
fun QueueScreenView(
    queueItems: List<QueueItem>,
    onDelete: (QueueItem) -> Unit,
    onRetry: (QueueItem) -> Unit,
    onForceSync: () -> Unit
) {
    var expandedItem by remember { mutableStateOf<QueueItem?>(null) }
    var itemToDelete by remember { mutableStateOf<QueueItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Queue status header block
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Черга розмов",
                    fontWeight = FontWeight.Normal,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Записів у списку: ${queueItems.size}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Button(
                onClick = onForceSync,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("force_sync_btn")
            ) {
                Icon(Icons.Default.Sync, contentDescription = "Sync", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Оновити", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (queueItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.CloudQueue,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Черга пуста",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Збережені розмови відображатимуться тут.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("queue_items_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(queueItems, key = { it.id }) { item ->
                    QueueItemCard(
                        item = item,
                        isExpanded = expandedItem?.id == item.id,
                        onExpandToggle = {
                            expandedItem = if (expandedItem?.id == item.id) null else item
                        },
                        onDeleteClick = { itemToDelete = item },
                        onRetryClick = { onRetry(item) }
                    )
                }
            }
        }
    }

    // Modal dialogue for reading copy text
    expandedItem?.let { item ->
        if (item.transcription.isNotBlank()) {
            TranscriptionDetailDialog(
                item = item,
                onDismiss = { expandedItem = null },
                onRetry = { onRetry(item) }
            )
        }
    }

    // Confirm Delete Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Видалити запис?", fontWeight = FontWeight.Bold) },
            text = { Text("Ви впевнені, що хочете видалити розмову з клієнтом «${item.clientName}» та стерти її з пристрою?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(item)
                        itemToDelete = null
                    }
                ) {
                    Text("Так, видалити", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Скасувати")
                }
            }
        )
    }
}

@Composable
fun QueueItemCard(
    item: QueueItem,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onDeleteClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    val progressRotation = rememberInfiniteTransition(label = "spin")
    val angle by progressRotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandToggle() }
            .testTag("queue_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Visual Tag
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            when (item.status) {
                                "SENT" -> Color(0xFFD1FAE5)
                                "SAVED_LOCAL" -> Color(0xFFDBEAFE)
                                "PROCESSING" -> Color(0xFFFEF3C7)
                                "ERROR" -> Color(0xFFFEE2E2)
                                else -> Color(0xFFE5E7EB)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (item.status) {
                        "SENT" -> Icon(Icons.Default.Check, contentDescription = "Sent", tint = Color(0xFF059669))
                        "SAVED_LOCAL" -> Icon(Icons.Default.Folder, contentDescription = "Saved Local", tint = Color(0xFF2563EB))
                        "PROCESSING" -> Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Processing",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.rotate(angle)
                        )
                        "ERROR" -> Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = Color(0xFFDC2626))
                        else -> Icon(Icons.Default.HourglassEmpty, contentDescription = "Waiting", tint = Color(0xFF4B5563))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.clientName.ifBlank { "Відомий Клієнт" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (item.mode == "LOCAL") "Режим: Локально" else "Режим: Сервер",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatMsDuration(item.audioDurationMs),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = formatTimestamp(item.timestamp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    // Ukrainian Status Label
                    val statusText = when (item.status) {
                        "SENT" -> "Надіслано"
                        "SAVED_LOCAL" -> "Збережено"
                        "PROCESSING" -> "Обробляється"
                        "ERROR" -> "Помилка"
                        else -> "В черзі"
                    }
                    val statusColor = when (item.status) {
                        "SENT" -> Color(0xFF059669)
                        "SAVED_LOCAL" -> Color(0xFF2563EB)
                        "PROCESSING" -> Color(0xFFD97706)
                        "ERROR" -> Color(0xFFDC2626)
                        else -> Color(0xFF4B5563)
                    }
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            // Expose error or details when expanded
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                ) {
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (item.errorMessage != null) {
                        Text(
                            text = "❌ Причина проблеми:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                        Text(
                            text = item.errorMessage ?: "Невідома помилка",
                            fontSize = 12.sp,
                            color = Color.Red.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if ((item.status == "SENT" || item.status == "SAVED_LOCAL") && item.transcription.isNotBlank()) {
                        Text(
                            text = "📝 Результат транскрибації (Клікніть для перегляду розмови):",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = item.transcription,
                            fontSize = 12.sp,
                            maxLines = 3,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (item.status == "ERROR" || item.status == "WAITING" || item.status == "SAVED_LOCAL") {
                            IconButton(onClick = onRetryClick) {
                                Icon(Icons.Default.Send, "Повторити", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = onDeleteClick) {
                            Icon(Icons.Default.DeleteOutline, "Видалити", tint = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TranscriptionDetailDialog(
    item: QueueItem,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val durationSec = item.audioDurationMs / 1000.0
    val audioCost = durationSec * 0.00002 // $0.00002 per second of audio input (Gemini 1.5 Flash)
    val responseTokens = item.transcription.length / 4.0
    val textCost = responseTokens * (0.30 / 1_000_000.0) // $0.30 per 1M characters/tokens output
    val totalCostUSD = audioCost + textCost
    val totalCostUAH = totalCostUSD * 40.5 // Approximate exchange rate

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Розшифровка розмови",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Dismiss")
                    }
                }
                Text(
                    text = "Клієнт: ${item.clientName} | Менеджер: ${item.userId}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Stats and estimated costs card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Тривалість розмови", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = formatMsDuration(item.audioDurationMs),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Орієнтовна вартість (Gemini Flash)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = String.format("$%.5f (≈%.3f грн)", totalCostUSD, totalCostUAH),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Error Message block if present
                if (!item.errorMessage.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Проблема з відправкою в БД MSSQL:",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626),
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.errorMessage ?: "",
                                color = Color(0xFF991B1B),
                                fontSize = 11.sp,
                                maxLines = 4,
                                modifier = Modifier.padding(start = 20.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "📝 Текст розмови:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Scrollable Transcription Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = item.transcription,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Транскрипція", item.transcription)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Текст скопійовано!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Копіювати", overflow = TextOverflow.Ellipsis, maxLines = 1)
                    }

                    if (item.status == "ERROR" || item.status == "SAVED_LOCAL") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onRetry?.invoke()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Send, "Send", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("В MSSQL", overflow = TextOverflow.Ellipsis, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Закрити")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreenView(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    userId: String,
    onUserIdChange: (String) -> Unit,
    mode: String,
    onModeChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    isBatteryOptimizedExempt: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Налаштування додатку",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = onReset) {
                Icon(Icons.Default.SettingsBackupRestore, "Reset", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large settings Scroll block
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AUTHORIZATION SECTION
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "🔧 ІДЕНТИФІКАТОР КОРИСТУВАЧА",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = userId,
                            onValueChange = onUserIdChange,
                            label = { Text("ID Менеджера") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("settings_username_input"),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Транскрибовані записи будуть прив'язані до цього ID у базах даних.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // OPERATING MODE
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⚙️ РЕЖИМ РОБОТИ ТРАНСКРИБАЦІЇ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onModeChange("LOCAL") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (mode == "LOCAL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.weight(1f).testTag("select_mode_local"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Локальний",
                                    color = if (mode == "LOCAL") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onModeChange("SERVER") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (mode == "SERVER") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.weight(1f).testTag("select_mode_server"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Серверний",
                                    color = if (mode == "SERVER") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (mode == "LOCAL") {
                                "💡 Локальний режим: на телефоні виконується Whisper-STT. Отриманий готовий текст надсилається в MSSQL базу через ваш REST-проксі."
                            } else {
                                "💡 Серверний режим: аудіофайл стискається в опус-wav та завантажується на віддалений сервер. Сервер перебирає Whisper та діаризацію."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // LANGUAGE TARGET
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "🗣️ МОВА РОЗПІЗНАВАННЯ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable { onLanguageChange("uk") }
                                    .weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = language == "uk", onClick = { onLanguageChange("uk") })
                                Text("Українська 🇺🇦")
                            }
                            Row(
                                modifier = Modifier
                                    .clickable { onLanguageChange("ru") }
                                    .weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = language == "ru", onClick = { onLanguageChange("ru") })
                                Text("Російська")
                            }
                        }
                    }
                }
            }

            // NETWORK SETTINGS
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "🌐 МЕРЕЖЕВИЙ REST API PROXY / URL",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = onServerUrlChange,
                            label = { Text("Адреса вашого сервера") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("settings_server_url"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Приклад: https://api.mycompany.com/stt-proxy",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // BATTERY LOGICS
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "🔋 ОПТИМІЗАЦІЯ БАТАРЕЇ ТА ФОН",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (isBatteryOptimizedExempt) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = if (isBatteryOptimizedExempt) Color(0xFF10B981) else Color(0xFFDC2626)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isBatteryOptimizedExempt) "Без обмежень" else "Запуск обмежений системою",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isBatteryOptimizedExempt) "Завдання у черзі виконуватимуться без затримок у фоні." else "Android вбиває чергу для економії. Будь ласка, дозвольте роботу в фоні.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        if (!isBatteryOptimizedExempt) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onRequestBatteryExemption,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Дозволити фонову роботу", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SAVE BUTTON ACTIONS
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("save_settings_btn"),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = "Save")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Зберегти зміни", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
