package com.riseinn.timetable.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
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

class TimetableSmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableSmallWidget()
}

class TimetableSmallWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 100.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val savedBatch = TimetableRepository.getSavedBatch(context)
        val lastRefreshMs = TimetableRepository.getLastRefreshTime(context)
        
        val timeString = if (lastRefreshMs > 0L) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastRefreshMs))
        } else "Never"

        var targetCards = listOf<DisplayCard>()
        var targetDayInt = 1
        var isTomorrow = false

        withContext(Dispatchers.IO) {
            if (savedBatch != null) {
                val batchInfo = LookupData.batches.find { it.name.equals(savedBatch, ignoreCase = true) }
                if (batchInfo != null) {
                    val rawEntries = NewTimetableLogic.getCachedSchedule(context)
                    
                    val calendar = Calendar.getInstance()
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    val javaDay = calendar.get(Calendar.DAY_OF_WEEK)
                    val todayInt = if (javaDay == Calendar.SUNDAY) 7 else javaDay - 1
                    
                    if (hour >= 18) {
                        isTomorrow = true
                        targetDayInt = if (todayInt == 7) 1 else todayInt + 1
                    } else {
                        targetDayInt = todayInt
                    }
                    
                    var cards = NewTimetableLogic.getDailySchedule(rawEntries, batchInfo.uuid, targetDayInt)
                    if (cards.isEmpty()) {
                        cards = listOf(DisplayCard("All Day", "Holiday ???", null, null, false))
                    }
                    targetCards = cards
                }
            }
        }

        provideContent {
            val iterCal = Calendar.getInstance()
            if (isTomorrow) {
                iterCal.add(Calendar.DAY_OF_YEAR, 1)
            }
            val dateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(iterCal.time)
            val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            val rawDayName = if (targetDayInt in 1..7) daysOfWeek[targetDayInt - 1] else ""
            val headerPrefix = if (isTomorrow) "Tomorrow" else "Today"
            val displayDayName = "$headerPrefix - $rawDayName ($dateStr)"

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
                        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = savedBatch?.let { "$it ($displayDayName)" } ?: "Setup needed",
                            style = TextStyle(
                                color = ColorProvider(day = Color(0xFF0F766E), night = Color(0xFF0F766E)),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
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
                                modifier = GlanceModifier.size(28.dp).padding(4.dp).clickable(actionRunCallback<RefreshAction>())
                            )
                        }
                    }

                    if (targetCards.isEmpty()) {
                        Text("No schedule available.", style = TextStyle(color = ColorProvider(day = Color.DarkGray, night = Color.LightGray), fontSize = 12.sp))
                    } else {
                        LazyRow(modifier = GlanceModifier.fillMaxSize()) {
                            items(targetCards) { card ->
                                val lowerSub = (card.subject ?: "").lowercase()
                                val (bgColor, textColor) = when {
                                    lowerSub.contains("mat") -> Pair(Color(0xFFECFDF5), Color(0xFF047857))
                                    lowerSub.contains("phy") -> Pair(Color(0xFFEFF6FF), Color(0xFF1D4ED8))
                                    lowerSub.contains("che") -> Pair(Color(0xFFFFF7ED), Color(0xFFC2410C))
                                    else -> Pair(Color(0xFFF8FAFC), Color(0xFF475569))
                                }

                                Column(
                                    modifier = GlanceModifier
                                        .padding(end = 6.dp)
                                        .background(ColorProvider(day = bgColor, night = bgColor))
                                        .cornerRadius(8.dp)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = card.subject ?: "",
                                        style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(day = textColor, night = textColor), fontSize = 11.sp),
                                        modifier = GlanceModifier.padding(bottom = 2.dp)
                                    )
                                    Text(
                                        text = card.timeLabel,
                                        style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(day = textColor, night = textColor), fontSize = 10.sp),
                                    )
                                    if (!card.room.isNullOrEmpty()) {
                                        Text(
                                            text = card.room,
                                            style = TextStyle(color = ColorProvider(day = textColor, night = textColor), fontSize = 10.sp)
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
