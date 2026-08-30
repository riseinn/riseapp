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
import java.util.Calendar
import com.riseinn.timetable.data.NewTimetableLogic
import com.riseinn.timetable.data.LookupData
import com.riseinn.timetable.data.DisplayCard
import androidx.compose.runtime.Composable

class TimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableWidget()
}

class TimetableWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 50.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val savedBatch = TimetableRepository.getSavedBatch(context)
        val lastRefreshMs = TimetableRepository.getLastRefreshTime(context)
        
        val timeString = if (lastRefreshMs > 0L) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastRefreshMs))
        } else "Never"

        var newApiData: List<DisplayCard>? = null
        var displayDateStr = ""

        withContext(Dispatchers.IO) {
            if (savedBatch != null) {
                val batchInfo = LookupData.batches.find { it.name.equals(savedBatch, ignoreCase = true) }
                if (batchInfo != null) {
                    val rawEntries = NewTimetableLogic.getCachedSchedule(context)
                    
                    val calendar = Calendar.getInstance()
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    
                    // After 6 PM (18:00), shift to tomorrow
                    if (hour >= 18) {
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                        displayDateStr = SimpleDateFormat("dd MMM (Tomorrow)", Locale.getDefault()).format(calendar.time)
                    } else {
                        displayDateStr = SimpleDateFormat("dd MMM (Today)", Locale.getDefault()).format(calendar.time)
                    }
                    
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    val apiDayOfWeek = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1
                    
                    newApiData = NewTimetableLogic.getDailySchedule(rawEntries, batchInfo.uuid, apiDayOfWeek)
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
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(ImageProvider(R.drawable.glass_widget_bg))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    NewApiWidgetContent(savedBatch, displayDateStr, timeString, newApiData)
                }
            }
        }
    }
}

@Composable
fun NewApiWidgetContent(savedBatch: String?, displayDateStr: String, timeString: String, newApiData: List<DisplayCard>?) {
    // === HEADER ===
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

        // Room from first slot if available
        val firstRoom = newApiData?.firstOrNull()?.room
        if (firstRoom != null && firstRoom != "TBD") {
            Text(
                text = firstRoom.uppercase(),
                style = TextStyle(
                    color = ColorProvider(day = Color(0xFF334155), night = Color.White),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                modifier = GlanceModifier.padding(end = 6.dp)
            )
        }

        Text(
            text = "$displayDateStr  •  Sync: $timeString",
            style = TextStyle(
                color = ColorProvider(day = Color(0xFF94A3B8), night = Color.LightGray),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }

    // === BODY ===
    if (newApiData == null && savedBatch != null) {
        Text("No classes today! 🏝️", style = TextStyle(color = ColorProvider(day = Color.DarkGray, night = Color.LightGray), fontSize = 11.sp))
    } else if (newApiData != null) {
        if (newApiData.isEmpty()) {
            Text("Holiday! ☕", style = TextStyle(color = ColorProvider(day = Color.DarkGray, night = Color.LightGray), fontSize = 12.sp, fontWeight = FontWeight.Medium))
        } else {
            val chunkedSlots = newApiData.chunked(4)
            
            chunkedSlots.forEach { rowSlots ->
                Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    rowSlots.forEach { card ->
                        val lowerSub = (card.subject ?: "").lowercase()
                        val (bgColor, textColor) = when {
                            lowerSub.contains("mat") -> Pair(Color(0xFFECFDF5), Color(0xFF047857))
                            lowerSub.contains("phy") -> Pair(Color(0xFFEFF6FF), Color(0xFF1D4ED8))
                            lowerSub.contains("che") -> Pair(Color(0xFFFFF7ED), Color(0xFFC2410C))
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
                                text = card.timeLabel.replace(" - ", "\n"),
                                style = TextStyle(fontWeight = FontWeight.Bold, color = ColorProvider(day = textColor, night = textColor), fontSize = 8.sp, textAlign = TextAlign.Center)
                            )
                            Text(
                                text = card.subject ?: "",
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