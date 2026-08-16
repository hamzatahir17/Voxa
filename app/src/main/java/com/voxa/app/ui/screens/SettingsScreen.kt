package com.voxa.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxa.app.ui.components.VoxaBackgroundShader
import com.voxa.app.ui.viewmodel.VoxaUiState
import com.voxa.app.ui.viewmodel.VoxaViewModel

@Composable
fun SettingsScreen(
    viewModel: VoxaViewModel,
    onTriggerAssistant: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var isBatteryOptimizationIgnored by remember { mutableStateOf(true) }
    var canScheduleExactAlarms by remember { mutableStateOf(true) }
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val state by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    LaunchedEffect(state) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isBatteryOptimizationIgnored = pm.isIgnoringBatteryOptimizations(context.packageName)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            canScheduleExactAlarms = am.canScheduleExactAlarms()
        }
    }

    SettingsScreenContent(
        uiState = uiState,
        onTriggerAssistant = onTriggerAssistant,
        onNavigateToDashboard = onNavigateToDashboard,
        onToggleNotifications = { viewModel.toggleNotifications(it) },
        onToggleHapticFeedback = { viewModel.toggleHapticFeedback(it) },
        onToggleAlarmVibration = { viewModel.toggleAlarmVibration(it) },
        onUpdateSnoozeLength = { viewModel.updateSnoozeLength(it) },
        isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
        canScheduleExactAlarms = canScheduleExactAlarms,
        onRequestIgnoreBatteryOptimization = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        },
        onRequestExactAlarmPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
    )
}

@Composable
fun SettingsScreenContent(
    uiState: VoxaUiState,
    onTriggerAssistant: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleHapticFeedback: (Boolean) -> Unit,
    onToggleAlarmVibration: (Boolean) -> Unit,
    onUpdateSnoozeLength: (Int) -> Unit,
    isBatteryOptimizationIgnored: Boolean,
    canScheduleExactAlarms: Boolean,
    onRequestIgnoreBatteryOptimization: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit
) {
    var showAutoLaunchInstructions by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            DashboardBottomNav(
                onTriggerAssistant = onTriggerAssistant,
                onNavigateToDashboard = onNavigateToDashboard,
                onNavigateToSettings = {},
                currentTab = "settings"
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            VoxaBackgroundShader()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                SettingsSection(title = "Notifications") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    ) {
                        if (!isBatteryOptimizationIgnored) {
                            SettingsItem(
                                icon = Icons.Default.BatteryAlert,
                                title = "Disable Battery Optimization",
                                subtitle = "Highly recommended for reliable alarms",
                                onClick = onRequestIgnoreBatteryOptimization,
                                iconColor = MaterialTheme.colorScheme.error
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 64.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }

                        if (!canScheduleExactAlarms) {
                            SettingsItem(
                                icon = Icons.Default.AlarmOn,
                                title = "Allow Exact Alarms",
                                subtitle = "Required to trigger alerts precisely on time",
                                onClick = onRequestExactAlarmPermission,
                                iconColor = MaterialTheme.colorScheme.primary
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 64.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }

                        // Auto-Launch Instruction Item
                        SettingsItem(
                            icon = Icons.Default.RocketLaunch,
                            title = "Auto-Launch Guide",
                            subtitle = "How to enable background alarms manually",
                            onClick = { showAutoLaunchInstructions = true },
                            iconColor = Color(0xFFFFB74D)
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        SettingsToggleItem(
                            icon = Icons.Default.Notifications,
                            title = "Enable Notifications",
                            subtitle = "Receive alerts for upcoming meetings",
                            isChecked = uiState.isNotificationsEnabled,
                            onCheckedChange = onToggleNotifications
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SnoozeLengthPicker(
                            currentLength = uiState.snoozeLengthMins,
                            onLengthSelected = onUpdateSnoozeLength
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                SettingsSection(title = "Appearance") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    ) {
                        SettingsItem(
                            icon = Icons.Default.Palette,
                            title = "Theme",
                            subtitle = "System Default (Dark)",
                            onClick = {}
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsToggleItem(
                            icon = Icons.Default.Vibration,
                            title = "Haptic Feedback",
                            subtitle = "Subtle vibrations on interaction",
                            isChecked = uiState.isHapticFeedbackEnabled,
                            onCheckedChange = onToggleHapticFeedback
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsToggleItem(
                            icon = Icons.Default.Vibration,
                            title = "Alarm Vibration",
                            subtitle = "Vibrate when alert triggers",
                            isChecked = uiState.isAlarmVibrationEnabled,
                            onCheckedChange = onToggleAlarmVibration
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                SettingsSection(title = "About") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    ) {
                        SettingsItem(
                            icon = Icons.Default.Info,
                            title = "Version",
                            subtitle = "1.0.0 (Production Build)",
                            onClick = {}
                        )
                    }
                }
            }
        }

        if (showAutoLaunchInstructions) {
            AutoLaunchInstructionsDialog(onDismiss = { showAutoLaunchInstructions = false })
        }
    }
}

@Composable
fun AutoLaunchInstructionsDialog(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1A1C1E),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.RocketLaunch,
                    contentDescription = null,
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Enable Auto-Launch",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "To ensure Voxa can wake up your phone for alarms, please follow these steps:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                InstructionStep(number = "1", text = "Open phone Settings")
                InstructionStep(number = "2", text = "Go to Apps / App Management")
                InstructionStep(number = "3", text = "Select Voxa from the list")
                InstructionStep(number = "4", text = "Find 'Auto-start' or 'App Launch'")
                InstructionStep(number = "5", text = "Enable it for Voxa")
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("GOT IT", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun InstructionStep(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
        )
        content()
    }
}

@Composable
fun SnoozeLengthPicker(
    currentLength: Int,
    onLengthSelected: (Int) -> Unit
) {
    val lengths = listOf(5, 10, 15, 20)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceBright, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Snooze,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Snooze Length",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Minutes to wait after snooze",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            lengths.forEach { length ->
                val isSelected = currentLength == length
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onLengthSelected(length) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${length}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}


@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceBright, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceBright, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    com.voxa.app.ui.theme.VoxaTheme {
        SettingsScreenContent(
            uiState = VoxaUiState(
                isNotificationsEnabled = true,
                isHapticFeedbackEnabled = true,
                isAlarmVibrationEnabled = false,
                snoozeLengthMins = 10
            ),
            onTriggerAssistant = {},
            onNavigateToDashboard = {},
            onToggleNotifications = {},
            onToggleHapticFeedback = {},
            onToggleAlarmVibration = {},
            onUpdateSnoozeLength = {},
            isBatteryOptimizationIgnored = false,
            canScheduleExactAlarms = true,
            onRequestIgnoreBatteryOptimization = {},
            onRequestExactAlarmPermission = {}
        )
    }
}
