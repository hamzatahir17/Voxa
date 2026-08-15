package com.voxa.app

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.voxa.app.data.local.VoxaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VoxaAlarmWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("VoxaAlarm", "WorkManager: Rescheduling alarms as backup...")
            val database = VoxaDatabase.getDatabase(applicationContext)
            val dao = database.itineraryDao()
            
            val items = dao.getAllItemsSync()
            val now = System.currentTimeMillis()
            val safetyBufferMillis = 2 * 60 * 1000L // 2 minutes
            
            items.filter { !it.isCompleted }.forEach { item ->
                val eventTime = AlarmUtils.parseTime(item.time)
                val triggerTime = eventTime.timeInMillis - (item.leadTimeMins * 60 * 1000L)
                
                // Only reschedule if the trigger time is at least 2 minutes in the future.
                // If it's sooner, let the existing AlarmManager handle it to avoid double triggers.
                if (triggerTime > (now + safetyBufferMillis)) {
                    AlarmUtils.scheduleAlarm(applicationContext, item)
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("VoxaAlarm", "WorkManager: Reschedule failed", e)
            Result.retry()
        }
    }
}
