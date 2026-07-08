package com.riseinn.timetable.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.riseinn.timetable.R
import com.riseinn.timetable.data.TimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableWidget()
}

class TimetableWidget : GlanceAppWidget() {
    // Ultra compact size target for 4x1
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 50.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val savedBatch = TimetableRepository.getSavedBatch(context)
        val lastRefreshMs = TimetableRepository.getLastRefreshTime(context)
        
        val data = withContext(Dispatchers.IO) { TimetableRepository.fetchDailyData(context) }
        val batchRow = if (savedBatch != null) TimetableRepository.filterForBatch(data, savedBatch) else null

        val timeString = if (lastRefreshMs > 0L) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastRefreshMs))
        } else "Never"

        provideContent {
            // Fills the grid cell but centers the tight glass box
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // The tightly wrapped Glass Box
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(ImageProvider(R.drawable.glass_widget_bg))
                        .padding(horizontal = 12.dp, vertical = 6.dp) // Tighter vertical padding for 4x1
                ) {
                    
                    val allSlots = batchRow?.drop(1)?.filter { it.isNotBlank() && it != "-" } ?: emptyList()
                    val roomRegex = Regex("(?i)^r[-\\s]?\\d+.*|.*room.*")
                    val roomSlot = allSlots.find { it.matches(roomRegex) }
                    val subjectSlots = allSlots.filter { it != roomSlot }

                    // === HEADER (No Refresh Button) ===
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = savedBatch ?: "Setup needed",
                            style = TextStyle(
                                color = ColorProvider(day = Color(0xFF0F766E), night = Color(0xFF0F766E)),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )

                        if (roomSlot != null) {
                            Text(
                                text = roomSlot.uppercase(),
                                style = TextStyle(
                                    color = ColorProvider(day = Color(0xFF334155), night = Color.White),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                modifier = GlanceModifier.padding(end = 6.dp)
                            )
                        }

                        Text(
                            text = "Sync: $timeString",
                            style = TextStyle(
                                color = ColorProvider(day = Color(0xFF94A3B8), night = Color.LightGray),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    // === BODY ===
                    if (batchRow == null && savedBatch != null) {
                        Text("No classes today! 🏝️", style = TextStyle(color = ColorProvider(day = Color.DarkGray, night = Color.LightGray), fontSize = 11.sp))
                    } else if (batchRow != null) {
                        if (subjectSlots.isEmpty()) {
                            Text("Holiday! ☕", style = TextStyle(color = ColorProvider(day = Color.DarkGray, night = Color.LightGray), fontSize = 12.sp, fontWeight = FontWeight.Medium))
                        } else {
                            val chunkedSlots = subjectSlots.chunked(4)
                            
                            chunkedSlots.forEachIndexed { rowIndex, rowSlots ->
                                Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                    rowSlots.forEachIndexed { colIndex, slot ->
                                        val absoluteIndex = (rowIndex * 4) + colIndex
                                        val lowerSlot = slot.lowercase()
                                        
                                        val (bgColor, textColor) = when {
                                            lowerSlot.contains("mat") -> Pair(Color(0xFFECFDF5), Color(0xFF047857))
                                            lowerSlot.contains("phy") -> Pair(Color(0xFFEFF6FF), Color(0xFF1D4ED8))
                                            lowerSlot.contains("che") -> Pair(Color(0xFFFFF7ED), Color(0xFFC2410C))
                                            else -> Pair(Color(0xFFF8FAFC), Color(0xFF475569))
                                        }

                                        Column(
                                            modifier = GlanceModifier
                                                .defaultWeight()
                                                .background(ColorProvider(day = bgColor, night = bgColor))
                                                .padding(vertical = 2.dp, horizontal = 2.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "S${absoluteIndex + 1}",
                                                style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(day = textColor, night = textColor), fontSize = 9.sp)
                                            )
                                            Text(
                                                text = slot,
                                                style = TextStyle(color = ColorProvider(day = textColor, night = textColor), fontWeight = FontWeight.Medium, fontSize = 10.sp, textAlign = TextAlign.Center),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    
                                    repeat(4 - rowSlots.size) {
                                        Spacer(modifier = GlanceModifier.defaultWeight())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}