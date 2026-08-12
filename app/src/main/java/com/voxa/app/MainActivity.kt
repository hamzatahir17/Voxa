package com.voxa.app

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voxa.app.ui.screens.AlertsScreen
import com.voxa.app.ui.screens.DashboardScreen
import com.voxa.app.ui.screens.InteractionConfirmScreen
import com.voxa.app.ui.screens.NotificationsScreen
import com.voxa.app.ui.screens.OnboardingScreen
import com.voxa.app.ui.screens.RecordingScreen
import com.voxa.app.ui.screens.SettingsScreen
import com.voxa.app.ui.theme.VoxaTheme
import com.voxa.app.ui.viewmodel.VoxaViewModel
import com.voxa.app.ui.components.VoxaShaderCache

class MainActivity : ComponentActivity() {
    private lateinit var voxaViewModel: VoxaViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Pre-warm AGSL Shaders in background to remove Alert screen lag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Thread {
                VoxaShaderCache.getBackgroundShader()
                VoxaShaderCache.getOrbShader()
                VoxaShaderCache.getWaveformShader()
            }.start()
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        @Suppress("DEPRECATION")
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        
        enableEdgeToEdge()
        
        // Request Permissions for Background Reliability
        checkExactAlarmPermission()
        checkOverlayPermission()
        setupBackgroundBackup()

        setContent {
            voxaViewModel = viewModel()

            VoxaTheme {
                LaunchedEffect(intent) {
                    handleAlarmIntent(intent)
                }

                VoxaApp(voxaViewModel, onCloseApp = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlarmIntent(intent)
    }

    private fun handleAlarmIntent(intent: Intent?) {
        val alarmTriggered = intent?.getBooleanExtra("ALARM_TRIGGERED", false) ?: false
        val alarmItemId = intent?.getIntExtra("ALARM_ITEM_ID", -1) ?: -1

        if (alarmTriggered && alarmItemId != -1) {
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
            val isLocked = keyguardManager.isKeyguardLocked
            voxaViewModel.triggerAlarmFromIntent(alarmItemId, isLocked)
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                } catch (e: Exception) {}
            }
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (e: Exception) {}
            }
        }
    }

    private fun setupBackgroundBackup() {
        val workRequest = PeriodicWorkRequestBuilder<VoxaAlarmWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "VoxaAlarmBackup",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

@Composable
fun VoxaApp(voxaViewModel: VoxaViewModel, onCloseApp: () -> Unit) {
    val navController = rememberNavController()
    val uiState by voxaViewModel.uiState.collectAsState()

    // Monitor for alerts and navigate
    LaunchedEffect(uiState.activeAlertItem) {
        if (uiState.activeAlertItem != null) {
            navController.navigate("alerts") {
                launchSingleTop = true
            }
        }
    }

    if (!uiState.isDataLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else {
        NavHost(
            navController = navController,
            startDestination = if (uiState.isOnboardingCompleted) "dashboard" else "onboarding",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("onboarding") {
                OnboardingScreen(viewModel = voxaViewModel) {
                    navController.navigate("dashboard") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            }
            composable("dashboard") {
                DashboardScreen(
                    viewModel = voxaViewModel,
                    onTriggerAssistant = {
                        voxaViewModel.startListening()
                        navController.navigate("recording")
                    },
                    onNavigateToNotifications = {
                        navController.navigate("notifications")
                    },
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    }
                )
            }
            composable("notifications") {
                NotificationsScreen(
                    viewModel = voxaViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("recording") {
                RecordingScreen(
                    viewModel = voxaViewModel,
                    onTranscriptionComplete = { navController.navigate("confirm") },
                    onClose = {
                        voxaViewModel.resetState()
                        navController.popBackStack()
                    }
                )
            }
            composable("confirm") {
                InteractionConfirmScreen(viewModel = voxaViewModel) {
                    voxaViewModel.confirmAction()
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = voxaViewModel,
                    onTriggerAssistant = {
                        voxaViewModel.startListening()
                        navController.navigate("recording")
                    },
                    onNavigateToDashboard = {
                        navController.navigate("dashboard") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    }
                )
            }
            composable("alerts") {
                AlertsScreen(
                    viewModel = voxaViewModel,
                    onDismiss = {
                        val wasLocked = uiState.isTriggeredOnLockScreen
                        voxaViewModel.dismissAlert()
                        if (wasLocked) {
                            onCloseApp()
                        } else {
                            navController.navigate("dashboard") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                        }
                    },
                    onSnooze = {
                        val wasLocked = uiState.isTriggeredOnLockScreen
                        voxaViewModel.snoozeAlert()
                        if (wasLocked) {
                            onCloseApp()
                        } else {
                            navController.navigate("dashboard") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }
        }
    }
}
