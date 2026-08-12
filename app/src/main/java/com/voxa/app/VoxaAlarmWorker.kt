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
            
            items.filter { !it.isCompleted }.forEach { item ->
                // Only reschedule if it's actually in the future
                val eventTime = AlarmUtils.parseTime(item.time)
                if (eventTime.timeInMillis > now) {
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
