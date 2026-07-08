package com.riseinn.timetable.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.riseinn.timetable.widget.TimetableWidget

class TimetableWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // 1. Fetch fresh data from the Google Sheet
            TimetableRepository.fetchDailyData(applicationContext)
            
            // 2. Force all Home Screen widgets to redraw with the new data
            TimetableWidget().updateAll(applicationContext)
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}