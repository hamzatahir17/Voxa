package com.voxa.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class VoxaAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("VoxaAlarm", "Receiver triggered with action: $action")

        // Handle Boot or Quickboot actions to reschedule alarms
        if ((action == Intent.ACTION_BOOT_COMPLETED) || (action == "android.intent.action.QUICKBOOT_POWERON")) {
            Log.d("VoxaAlarm", "Device rebooted. Starting service to reschedule alarms.")
            val rescheduleIntent = Intent(context, VoxaAlarmService::class.java)
            rescheduleIntent.action = "ACTION_RESCHEDULE_ALARMS"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(rescheduleIntent)
            } else {
                context.startService(rescheduleIntent)
            }
            return
        }

        // Only process our specific alarm action
        if (action != "com.voxa.app.ACTION_ALARM" && action != "ACTION_TRIGGER_ALARM") return

        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Voxa Alert"
        val itemId = intent.getIntExtra("EXTRA_ID", -1)

        Log.d("VoxaAlarm", "Alarm Triggered for: $title, Starting Service...")
        
        val serviceIntent = Intent(context, VoxaAlarmService::class.java)
        serviceIntent.action = "ACTION_TRIGGER_ALARM"
        serviceIntent.putExtra("EXTRA_TITLE", title)
        serviceIntent.putExtra("EXTRA_ID", itemId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}

