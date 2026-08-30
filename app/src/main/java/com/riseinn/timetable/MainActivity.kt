package com.riseinn.timetable

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.riseinn.timetable.data.LookupData
import com.riseinn.timetable.data.TimetableRepository
import com.riseinn.timetable.data.TimetableWorker
import com.riseinn.timetable.data.dataStore
import com.riseinn.timetable.widget.TimetableLargeWidget

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            NotificationHelper.scheduleDailyNotifications(this)
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                NotificationHelper.scheduleDailyNotifications(this)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            NotificationHelper.scheduleDailyNotifications(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        NotificationHelper.createNotificationChannel(this)
        askNotificationPermission()
        
        val workManager = WorkManager.getInstance(applicationContext)
        val syncRequest = PeriodicWorkRequestBuilder<TimetableWorker>(15, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(
            "TimetableSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        // Setup 2-minute repeating alarm for widget updates
        val intent = android.content.Intent(this, com.riseinn.timetable.widget.AutoSyncReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, 
            0, 
            intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.setRepeating(
            android.app.AlarmManager.RTC, 
            System.currentTimeMillis(), 
            2 * 60 * 1000, 
            pendingIntent
        )

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8FAFC)) {
                    RiseinnDashboard()
                }
            }
        }
    }
}

@Composable
fun RiseinnDashboard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Student, 1: AI
    
    var currentBatch by remember { mutableStateOf<String?>(null) }
    
    // We can now just get batches instantly from LookupData!
    val batchesList = remember { LookupData.batches.map { it.name }.sorted() }
    
    var showDisclaimer by remember { mutableStateOf(false) }
    
    // Confirmation Dialog States
    var batchToConfirm by remember { mutableStateOf<String?>(null) }
    var isUpdatingWidget by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val tutorialShown = context.dataStore.data.map { it[TimetableRepository.TUTORIAL_SHOWN_KEY] ?: false }.first()
        showDisclaimer = !tutorialShown
        currentBatch = TimetableRepository.getSavedBatch(context)
    }

    // MANDATORY DISCLAIMER DIALOG
    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss */ },
            title = { Text("Welcome to Riseinn", fontWeight = FontWeight.Bold) },
            text = { Text("This is an unofficial timetable viewer. We do not guarantee 100% accuracy. Kindly check the official sources for major updates.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            context.dataStore.edit { it[TimetableRepository.TUTORIAL_SHOWN_KEY] = true }
                            showDisclaimer = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text("I Understand")
                }
            },
            containerColor = Color.White
        )
    }

    // BATCH CHANGE CONFIRMATION DIALOG
    if (batchToConfirm != null) {
        AlertDialog(
            onDismissRequest = { if (!isUpdatingWidget) batchToConfirm = null },
            title = { Text("Change Batch?", fontWeight = FontWeight.Bold, color = Color.Black) },
            text = { Text("Do you want to set your timetable batch to $batchToConfirm?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isUpdatingWidget = true
                            TimetableRepository.saveBatch(context, batchToConfirm!!)
                            
                            // Fetch fresh data immediately
                            val batchInfo = com.riseinn.timetable.data.LookupData.batches.find { it.name.equals(batchToConfirm!!, ignoreCase = true) }
                            if (batchInfo != null) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    com.riseinn.timetable.data.NewTimetableLogic.fetchAndCacheScheduleForBatch(context, batchInfo.uuid)
                                }
                            }

                            // Update all widgets to reflect new batch
                            TimetableLargeWidget().updateAll(context)
                            com.riseinn.timetable.widget.TimetableSmallWidget().updateAll(context)
                            
                            currentBatch = batchToConfirm
                            Toast.makeText(context, "Batch successfully updated in widget!", Toast.LENGTH_SHORT).show()
                            isUpdatingWidget = false
                            batchToConfirm = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    if (isUpdatingWidget) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Yes, Change It")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isUpdatingWidget) batchToConfirm = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color.White
        )
    }

    Column(modifier = Modifier.padding(top = 24.dp).fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), 
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Timetable", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                Text(text = "Real-time Unofficial Viewer", fontSize = 14.sp, color = Color.Gray)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // TAB SELECTOR
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Color.Black,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            indicator = { tabPositions -> 
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Color.Black
                )
            },
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Student", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("AI Assist", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 1) {
            AIChatScreen(savedBatch = currentBatch)
        } else if (selectedTab == 0) {
            // STUDENT MODE UI
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E5E5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Current Batch", fontSize = 14.sp, color = Color.Gray)
                        Text(
                            text = currentBatch ?: "None Selected", 
                            fontSize = 20.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = if (currentBatch == null) Color.Red else Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "Select your batch to pin it to your Home Screen Widgets:", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(batchesList) { batch ->
                        Surface(
                            color = if (currentBatch == batch) Color(0xFFF4F4F5) else Color.White,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (currentBatch == batch) Color.Black else Color(0xFFE5E5E5)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (currentBatch != batch) {
                                        batchToConfirm = batch
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = batch, fontWeight = if (currentBatch == batch) FontWeight.Bold else FontWeight.Normal)
                                if (currentBatch == batch) {
                                    Text("✅", fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}