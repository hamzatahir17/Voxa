package com.voxa.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.voxa.app.data.local.entity.ItineraryEntity
import java.util.*

object AlarmUtils {
    fun scheduleAlarm(context: Context, item: ItineraryEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Direct Service Intent - Bypassing Receiver for better reliability on Chinese ROMs
        val intent = Intent(context, VoxaAlarmService::class.java).apply {
            putExtra("EXTRA_TITLE", item.title)
            putExtra("EXTRA_ID", item.id)
            action = "ACTION_TRIGGER_ALARM"
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                item.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            PendingIntent.getService(
                context,
                item.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }

        // Create a 'Show Intent' for System UI visibility
        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context, 
            item.id + 1000, 
            showIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val eventTime = parseTime(item.time)
        val leadTimeMillis = item.leadTimeMins * 60 * 1000L
        val triggerTime = eventTime.timeInMillis - leadTimeMillis
        val now = System.currentTimeMillis()

        // Logic Fix: If lead time has passed but meeting is still in the future, 
        // trigger the alarm immediately instead of not scheduling at all.
        val finalTriggerTime = if (triggerTime <= now && eventTime.timeInMillis > now) {
            now + 1000 // Trigger in 1 second
        } else {
            triggerTime
        }

        if (finalTriggerTime > now) {
            Log.d("VoxaAlarm", "Scheduling Alarm for ${item.title} at $finalTriggerTime")
            val info = AlarmManager.AlarmClockInfo(finalTriggerTime, showPendingIntent)
            alarmManager.setAlarmClock(info, pendingIntent)
        }
    }

    fun parseTime(timeStr: String): Calendar {
        val cal = Calendar.getInstance()
        try {
            val cleanTime = timeStr.uppercase().trim()
            val amPm = if (cleanTime.contains("PM")) "PM" else "AM"
            val timeDigits = cleanTime.replace("AM", "").replace("PM", "").trim()

            val timeParts = timeDigits.split(":")
            var hour = timeParts[0].toInt()
            val min = if (timeParts.size > 1) timeParts[1].toInt() else 0

            if ((amPm == "PM") && (hour < 12)) hour += 12
            if ((amPm == "AM") && (hour == 12)) hour = 0

            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, min)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        } catch (e: Exception) {}
        return cal
    }
}
