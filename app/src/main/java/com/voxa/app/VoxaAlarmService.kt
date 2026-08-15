package com.voxa.app

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat

class VoxaAlarmService : Service() {
    private var vibrator: Vibrator? = null
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("VoxaAlarm", "Service received action: $action")

        when (action) {
            "ACTION_TRIGGER_ALARM" -> {
                val itemId = intent.getIntExtra("EXTRA_ID", -1)
                
                // VERIFICATION: Check if this item still exists and is not completed
                val database = com.voxa.app.data.local.VoxaDatabase.getDatabase(this)
                val dao = database.itineraryDao()
                
                // Run on a background thread to prevent blocking main
                Thread {
                    val item = dao.getAllItemsSync().find { it.id == itemId }
                    if (item == null || item.isCompleted) {
                        Log.d("VoxaAlarm", "Ghost alarm detected (ID: $itemId deleted). Stopping service.")
                        stopSelf()
                    } else {
                        // All good, continue with alarm
                        val title = item.title
                        Log.d("VoxaAlarm", "Triggering REAL Alarm: $title")
                        
                        Handler(Looper.getMainLooper()).post {
                            acquireWakeLock()
                            showForegroundNotification(title, itemId)
                            
                            // DELAYED AUDIO: We wait 500ms before starting sound
                            // This ensures the Activity has time to bypass the lockscreen
                            Handler(Looper.getMainLooper()).postDelayed({
                                startAlarmEffects()
                            }, 500)
                        }
                    }
                }.start()
            }
            "ACTION_STOP_ALARM" -> {
                Log.d("VoxaAlarm", "Stopping Alarm Effects")
                stopAlarmEffects()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
            "ACTION_RESCHEDULE_ALARMS" -> {
                acquireWakeLock()
                showRescheduleNotification()
                rescheduleAlarms()
            }
            else -> {
                acquireWakeLock()
                val title = intent?.getStringExtra("EXTRA_TITLE") ?: "Voxa Alert"
                val itemId = intent?.getIntExtra("EXTRA_ID", -1) ?: -1
                showForegroundNotification(title, itemId)
                startAlarmEffects()
            }
        }

        return START_STICKY
    }

    private fun stopAlarmEffects() {
        vibrator?.cancel()
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset() // Critical to clear events
                it.release()
            }
        } catch (e: Exception) {
            Log.e("VoxaAlarm", "Error stopping MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Voxa:ServiceWakeLock").apply {
                acquire(3 * 60 * 1000L /* 3 minutes safety */)
            }
            Log.d("VoxaAlarm", "WakeLock Acquired by Service")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d("VoxaAlarm", "WakeLock Released by Service")
            }
        } catch (e: Exception) {
            Log.e("VoxaAlarm", "Error releasing WakeLock", e)
        } finally {
            wakeLock = null
        }
    }

    private fun showRescheduleNotification() {
        val channelId = "voxa_system_v1"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Voxa System", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Voxa")
            .setContentText("Updating your schedule...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(999, notification)
    }

    private fun rescheduleAlarms() {
        Log.d("VoxaAlarm", "Rescheduling alarms in background...")
        
        val database = com.voxa.app.data.local.VoxaDatabase.getDatabase(this)
        val dao = database.itineraryDao()

        // Room operations should be on a background thread
        Thread {
            try {
                val items = dao.getAllItemsSync()
                items.filter { !it.isCompleted }.forEach { item ->
                    AlarmUtils.scheduleAlarm(this, item)
                }
                Log.d("VoxaAlarm", "Rescheduled ${items.size} items.")
            } catch (e: Exception) {
                Log.e("VoxaAlarm", "Reschedule failed", e)
            } finally {
                Handler(Looper.getMainLooper()).post {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                }
            }
        }.start()
    }

    @SuppressLint("ForegroundServiceType")
    private fun showForegroundNotification(title: String, itemId: Int) {
        val channelId = "voxa_alarm_v2" // Updated ID to force system update
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // SILENT CHANNEL: We handle audio via MediaPlayer to prevent system clash/lag
            val channel = NotificationChannel(channelId, "Voxa Alarm Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Critical Voice Assistant Alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // Explicitly silence to prevent double-audio jank
            }
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("ALARM_TRIGGERED", true)
            putExtra("ALARM_ITEM_ID", itemId)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 
            itemId.coerceAtLeast(100), 
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Voxa: $title")
            .setContentText("Ongoing Meeting Alert")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true) // Force popup
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true) // We use our own MediaPlayer
            .setAutoCancel(false)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val alarmTypeField = ServiceInfo::class.java.getField("FOREGROUND_SERVICE_TYPE_ALARM")
                val alarmType = alarmTypeField.getInt(null)
                startForeground(itemId.coerceAtLeast(1), notification, alarmType)
            } catch (e: Exception) {
                startForeground(itemId.coerceAtLeast(1), notification)
            }
        } else {
            startForeground(itemId.coerceAtLeast(1), notification)
        }
    }

    private fun startAlarmEffects() {
        // Cleanup existing player if any to prevent "unhandled events"
        stopAlarmEffects()

        // Start Vibration - Can stay on Main as it's just a system service call
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 1000, 500, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        // Start Sound - MOVING TO BACKGROUND THREAD to prevent UI Jank (Disk I/O)
        Thread {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val player = MediaPlayer().apply {
                    setDataSource(this@VoxaAlarmService, uri)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    }
                    isLooping = true
                    prepare() // Sync prepare is fine on background thread
                }
                
                // Synchronize setting the global reference
                synchronized(this) {
                    mediaPlayer = player
                    player.start()
                }
                Log.d("VoxaAlarm", "Alarm sound started from background thread")
            } catch (e: Exception) {
                Log.e("VoxaAlarm", "Failed to play alarm sound", e)
            }
        }.start()
    }

    override fun onDestroy() {
        Log.d("VoxaAlarm", "Foreground Service Destroyed")
        stopAlarmEffects()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
