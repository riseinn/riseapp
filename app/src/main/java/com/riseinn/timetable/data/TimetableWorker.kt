package com.riseinn.timetable.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result

// We will add Large widget here later
import java.util.Calendar

class TimetableWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            
            // For developer testing, we fetch data every time the worker runs (every 15 mins).
            // No throttling.
            
            // Fetch JSON for the new API
            val savedBatch = TimetableRepository.getSavedBatch(applicationContext)
            if (savedBatch != null) {
                val batchInfo = LookupData.batches.find { it.name.equals(savedBatch, ignoreCase = true) }
                if (batchInfo != null) {
                    NewTimetableLogic.fetchAndCacheScheduleForBatch(applicationContext, batchInfo.uuid)
                }
            }
            
            // Force all Home Screen widgets to redraw with the new data
            com.riseinn.timetable.widget.TimetableLargeWidget().updateAll(applicationContext)
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}