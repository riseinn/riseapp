package com.riseinn.timetable.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.riseinn.timetable.data.TimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableWidget()
}

class TimetableWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val savedBatch = TimetableRepository.getSavedBatch(context)
        val data = withContext(Dispatchers.IO) {
            TimetableRepository.fetchDailyData(context)
        }
        val batchRow = if (savedBatch != null) {
            TimetableRepository.filterForBatch(data, savedBatch)
        } else null

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Riseinn: ${savedBatch ?: "No Batch Set"}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.padding(bottom = 8.dp)
                    )

                    if (savedBatch == null) {
                        Text("Open app to set your batch.", style = TextStyle(color = GlanceTheme.colors.onBackground))
                    } else if (batchRow == null) {
                        Text("No classes found today! 🏝️", style = TextStyle(color = GlanceTheme.colors.onBackground))
                    } else {
                        val slotsOnly = batchRow.drop(1).filter { it.isNotBlank() && it != "-" }
                        if (slotsOnly.isEmpty()) {
                            Text("Holiday! ☕", style = TextStyle(color = GlanceTheme.colors.onBackground))
                        } else {
                            slotsOnly.forEachIndexed { index, slot ->
                                Row(modifier = GlanceModifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = "Slot ${index + 1}: ",
                                        style = TextStyle(fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onBackground)
                                    )
                                    Text(
                                        text = slot,
                                        style = TextStyle(color = GlanceTheme.colors.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}