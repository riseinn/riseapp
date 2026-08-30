package com.riseinn.timetable.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.riseinn.timetable.data.LookupData
import com.riseinn.timetable.data.NewTimetableLogic
import com.riseinn.timetable.data.TimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import androidx.glance.appwidget.updateAll

class AutoSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val savedBatch = TimetableRepository.getSavedBatch(context)
                if (savedBatch != null) {
                    val batchInfo = LookupData.batches.find { it.name.equals(savedBatch, ignoreCase = true) }
                    if (batchInfo != null) {
                        NewTimetableLogic.fetchAndCacheScheduleForBatch(context, batchInfo.uuid)
                        TimetableLargeWidget().updateAll(context)
                        TimetableSmallWidget().updateAll(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
