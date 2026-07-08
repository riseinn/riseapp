package com.riseinn.timetable

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.glance.appwidget.updateAll
import com.riseinn.timetable.data.TimetableRepository
import com.riseinn.timetable.data.TimetableWorker
import com.riseinn.timetable.data.dataStore
import com.riseinn.timetable.widget.TimetableWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // SCHEDULE THE 20-MINUTE BACKGROUND REFRESH
        val syncRequest = PeriodicWorkRequestBuilder<TimetableWorker>(20, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TimetableAutoRefresh",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
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
    
    var currentBatch by remember { mutableStateOf<String?>(null) }
    var batchesList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    var showDisclaimer by remember { mutableStateOf(false) }
    
    // NEW: State for the confirmation pop-up
    var batchToConfirm by remember { mutableStateOf<String?>(null) }
    var isUpdatingWidget by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val tutorialShown = context.dataStore.data.map { it[TimetableRepository.TUTORIAL_SHOWN_KEY] ?: false }.first()
        showDisclaimer = !tutorialShown

        currentBatch = TimetableRepository.getSavedBatch(context)
        withContext(Dispatchers.IO) {
            val data = TimetableRepository.fetchDailyData(context)
            if (data.size > 1) {
                batchesList = data.drop(1).mapNotNull { it.firstOrNull() }.filter { it.isNotBlank() }.distinct()
            }
        }
        isLoading = false
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
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
            title = { Text("Change Batch?", fontWeight = FontWeight.Bold, color = Color(0xFF0F766E)) },
            text = { Text("Do you want to set your timetable batch to $batchToConfirm?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isUpdatingWidget = true
                            
                            // 1. SAVE TO STORAGE (Suspend until finished)
                            TimetableRepository.saveBatch(context, batchToConfirm!!)
                            
                            // 2. FORCE WIDGET TO UPDATE (Uses new storage value instantly)
                            TimetableWidget().updateAll(context)
                            
                            // 3. UPDATE APP UI ONLY AFTER WIDGET IS RE-RENDERED
                            currentBatch = batchToConfirm
                            
                            // 4. SHOW SUCCESS NOTIFICATION
                            Toast.makeText(context, "Batch successfully updated in widget!", Toast.LENGTH_SHORT).show()
                            
                            // Clean up
                            isUpdatingWidget = false
                            batchToConfirm = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                ) {
                    if (isUpdatingWidget) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Yes, Change It")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isUpdatingWidget) batchToConfirm = null }
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color.White
        )
    }

    Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
        Text(text = "Timetable", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F766E))
        Text(text = "Real-time Unofficial Viewer", fontSize = 14.sp, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Current Batch", fontSize = 14.sp, color = Color.Gray)
                Text(
                    text = currentBatch ?: "None Selected", 
                    fontSize = 20.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = if (currentBatch == null) Color.Red else Color(0xFF0F766E)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Select your batch to pin it to your Home Screen Widget:", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            CircularProgressIndicator(color = Color(0xFF0F766E), modifier = Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(batchesList) { batch ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (currentBatch == batch) 4.dp else 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Only trigger if they tap a DIFFERENT batch
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