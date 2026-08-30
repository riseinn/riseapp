package com.riseinn.timetable.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.Image
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.riseinn.timetable.R
import com.riseinn.timetable.MainActivity
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
        setOf(androidx.compose.ui.unit.DpSize(250.dp, 30.dp))
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
                    
                    val cards = NewTimetableLogic.getDailySchedule(rawEntries, batchInfo.uuid, targetDayInt)
                    targetCards = cards
                }
            }
        }

        provideContent {
            val iterCal = Calendar.getInstance()
            if (isTomorrow) {
                iterCal.add(Calendar.DAY_OF_YEAR, 1)
            }
            val dateStr = SimpleDateFormat("d MMM", Locale.getDefault()).format(iterCal.time)
            val firstRoom = targetCards.firstOrNull()?.room?.takeIf { it.isNotBlank() && it != "TBD" }
            val roomStr = if (firstRoom != null) " • $firstRoom" else ""
            val titleStr = savedBatch?.let { "$it$roomStr" } ?: "Setup needed"

            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ImageProvider(R.drawable.glass_widget_bg))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable(actionStartActivity<MainActivity>())
                ) {
                    // Header
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = titleStr,
                            style = TextStyle(
                                color = androidx.glance.color.ColorProvider(day = Color(0xFF0F766E), night = Color.White),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "\"$dateStr | ",
                                style = TextStyle(
                                    color = androidx.glance.color.ColorProvider(day = Color.DarkGray, night = Color.LightGray),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Image(
                                provider = ImageProvider(R.drawable.ic_refresh),
                                contentDescription = "Refresh",
                                modifier = GlanceModifier.size(24.dp).padding(4.dp).clickable(actionRunCallback<RefreshAction>())
                            )
                            Text(
                                text = timeString,
                                style = TextStyle(
                                    color = androidx.glance.color.ColorProvider(day = Color.DarkGray, night = Color.LightGray),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    if (targetCards.isEmpty()) {
                        Text("Holiday! \u2615", style = TextStyle(color = androidx.glance.color.ColorProvider(day = Color.DarkGray, night = Color.LightGray), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    } else {
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            targetCards.forEachIndexed { index, card ->
                                val lowerSub = (card.subject ?: "").lowercase()
                                // Vibrant colors with white text as per the image
                                val bgColor = when {
                                    lowerSub.contains("mat") -> Color(0xFF22C55E) // Green
                                    lowerSub.contains("phy") -> Color(0xFF3B82F6) // Blue
                                    lowerSub.contains("che") -> Color(0xFFF97316) // Orange
                                    else -> Color(0xFF64748B) // Slate
                                }
                                val subName = card.subject?.take(3)?.replaceFirstChar { it.uppercase() } ?: ""
                                val facCode = card.facultyCode ?: ""
                                val displayTitle = if (subName.isNotBlank() && facCode.isNotBlank()) "$subName($facCode)" else card.subject ?: ""

                                Column(
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .background(androidx.glance.color.ColorProvider(day = bgColor, night = bgColor))
                                        .cornerRadius(8.dp)
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "S${card.sortOrder}",
                                        style = TextStyle(color = androidx.glance.color.ColorProvider(day = Color(0xE6FFFFFF), night = Color(0xE6FFFFFF)), fontSize = 8.sp, fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = displayTitle,
                                        style = TextStyle(fontWeight = FontWeight.Bold, color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White), fontSize = 10.sp),
                                    )
                                }
                                
                                if (index < targetCards.size - 1) {
                                    Spacer(modifier = GlanceModifier.width(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
