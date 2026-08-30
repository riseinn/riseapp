package com.riseinn.timetable.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.riseinn.timetable.data.LookupData
import com.riseinn.timetable.data.NewTimetableLogic
import com.riseinn.timetable.data.TimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.glance.appwidget.updateAll

class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val savedBatch = TimetableRepository.getSavedBatch(context)
        if (savedBatch != null) {
            val batchInfo = LookupData.batches.find { it.name.equals(savedBatch, ignoreCase = true) }
            if (batchInfo != null) {
                withContext(Dispatchers.IO) {
                    NewTimetableLogic.fetchAndCacheScheduleForBatch(context, batchInfo.uuid)
                }
                TimetableLargeWidget().updateAll(context)
                TimetableSmallWidget().updateAll(context)
            }
        }
    }
}
