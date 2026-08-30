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
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.riseinn.timetable.R
import com.riseinn.timetable.data.TimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import com.riseinn.timetable.data.NewTimetableLogic
import com.riseinn.timetable.data.LookupData
import com.riseinn.timetable.data.DisplayCard

class TimetableLargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableLargeWidget()
}

class TimetableLargeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 250.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val savedBatch = TimetableRepository.getSavedBatch(context)
        val lastRefreshMs = TimetableRepository.getLastRefreshTime(context)
        
        val timeString = if (lastRefreshMs > 0L) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastRefreshMs))
        } else "Never"

        var weeklyData = emptyMap<Int, List<DisplayCard>>()

        withContext(Dispatchers.IO) {
            if (savedBatch != null) {
                val batchInfo = LookupData.batches.find { it.name.equals(savedBatch, ignoreCase = true) }
                if (batchInfo != null) {
                    val rawEntries = NewTimetableLogic.getCachedSchedule(context)
                    
                    // Group by day 1 to 7
                    val dataMap = mutableMapOf<Int, List<DisplayCard>>()
                    for (day in 1..7) {
                        val cards = NewTimetableLogic.getDailySchedule(rawEntries, batchInfo.uuid, day)
                        if (cards.isNotEmpty()) {
                            dataMap[day] = cards
                        } else {
                            dataMap[day] = listOf(DisplayCard(
                                timeLabel = "All Day",
                                subject = "Holiday 🏝️",
                                facultyCode = null,
                                room = null,
                                isExtra = false
                            ))
                        }
                    }
                    weeklyData = dataMap
                }
            }
        }

        provideContent {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ImageProvider(R.drawable.glass_widget_bg))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Header
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = savedBatch?.let { "$it (Week)" } ?: "Setup needed",
                            style = TextStyle(
                                color = ColorProvider(day = Color(0xFF0F766E), night = Color(0xFF0F766E)),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Sync: $timeString",
                                style = TextStyle(
                                    color = ColorProvider(day = Color(0xFF94A3B8), night = Color.LightGray),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = GlanceModifier.padding(end = 4.dp)
                            )
                            Image(
                                provider = ImageProvider(R.drawable.ic_refresh),
                                contentDescription = "Refresh",
                                modifier = GlanceModifier.size(28.dp).padding(4.dp).clickable(androidx.glance.appwidget.action.actionRunCallback<RefreshAction>())
                            )
                        }
                    }

                    if (weeklyData.isEmpty()) {
                        Text("No schedule available yet. 🏝️", style = TextStyle(color = ColorProvider(day = Color.DarkGray, night = Color.LightGray), fontSize = 12.sp))
                    } else {
                        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                            
                            val calendar = java.util.Calendar.getInstance()
                            val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                            val todayRealInt = when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
                                java.util.Calendar.MONDAY -> 1
                                java.util.Calendar.TUESDAY -> 2
                                java.util.Calendar.WEDNESDAY -> 3
                                java.util.Calendar.THURSDAY -> 4
                                java.util.Calendar.FRIDAY -> 5
                                java.util.Calendar.SATURDAY -> 6
                                java.util.Calendar.SUNDAY -> 7
                                else -> 1
                            }
                            
                            // If it's 6 PM or later, highlight tomorrow
                            val targetDayInt = if (currentHour >= 18) {
                                if (todayRealInt == 7) 1 else todayRealInt + 1
                            } else {
                                todayRealInt
                            }

                            val sortedEntries = weeklyData.entries.sortedWith(Comparator { a, b ->
                                if (a.key == targetDayInt) -1
                                else if (b.key == targetDayInt) 1
                                else a.key.compareTo(b.key)
                            })
                            
                            sortedEntries.forEach { (dayInt, cards) ->
                                val diff = dayInt - todayRealInt
                                val iterCal = java.util.Calendar.getInstance()
                                iterCal.add(java.util.Calendar.DAY_OF_YEAR, diff)
                                val dateStr = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(iterCal.time)
                                
                                val rawDayName = if (dayInt in 1..7) daysOfWeek[dayInt - 1] else "Day $dayInt"
                                val isTargetDay = dayInt == targetDayInt
                                val prefix = if (isTargetDay) {
                                    if (currentHour >= 18 && diff == 1) "Tomorrow - " else if (diff == 0) "Today - " else ""
                                } else ""
                                val displayDayName = "$prefix$rawDayName ($dateStr)"
                                
                                val baseModifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                                val columnModifier = if (isTargetDay) {
                                    baseModifier.background(ColorProvider(day = Color(0xFFFDE68A), night = Color(0xFFB45309)))
                                        .cornerRadius(12.dp)
                                        .padding(8.dp)
                                } else {
                                    baseModifier
                                }

                                item {
                                    Column(modifier = columnModifier) {
                                        Text(
                                            text = displayDayName,
                                            style = TextStyle(
                                                color = if (isTargetDay) ColorProvider(day = Color(0xFF854D0E), night = Color.White) else ColorProvider(day = Color(0xFF334155), night = Color.White),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            ),
                                            modifier = GlanceModifier.padding(bottom = 4.dp)
                                        )
                                        
                                        cards.forEach { card ->
                                            val lowerSub = (card.subject ?: "").lowercase()
                                            val (bgColor, textColor) = when {
                                                lowerSub.contains("mat") -> Pair(Color(0xFFECFDF5), Color(0xFF047857))
                                                lowerSub.contains("phy") -> Pair(Color(0xFFEFF6FF), Color(0xFF1D4ED8))
                                                lowerSub.contains("che") -> Pair(Color(0xFFFFF7ED), Color(0xFFC2410C))
                                                else -> Pair(Color(0xFFF8FAFC), Color(0xFF475569))
                                            }

                                            Row(
                                                modifier = GlanceModifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                                    .background(ColorProvider(day = bgColor, night = bgColor))
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = card.timeLabel,
                                                    style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(day = textColor, night = textColor), fontSize = 11.sp),
                                                    modifier = GlanceModifier.defaultWeight()
                                                )
                                                Text(
                                                    text = card.subject ?: "",
                                                    style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(day = textColor, night = textColor), fontSize = 11.sp),
                                                    modifier = GlanceModifier.defaultWeight()
                                                )
                                                Text(
                                                    text = card.room ?: "",
                                                    style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(day = textColor, night = textColor), fontSize = 11.sp)
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
        }
    }
}
