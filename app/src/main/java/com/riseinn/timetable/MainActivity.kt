package com.riseinn.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riseinn.timetable.data.TimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BatchSelectorScreen()
                }
            }
        }
    }
}

@Composable
fun BatchSelectorScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    var currentBatch by remember { mutableStateOf<String?>(null) }
    var batchesList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        currentBatch = TimetableRepository.getSavedBatch(context)
        withContext(Dispatchers.IO) {
            val data = TimetableRepository.fetchDailyData(context)
            if (data.size > 1) {
                batchesList = data.drop(1).mapNotNull { it.firstOrNull() }.distinct()
            }
        }
        isLoading = false
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Timetable Batch Selector", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Current Saved Batch: ${currentBatch ?: "None"}", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Select your batch below to update the Home Screen Widget:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            LazyColumn {
                items(batchesList) { batch ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                scope.launch {
                                    TimetableRepository.saveBatch(context, batch)
                                    currentBatch = batch
                                }
                            }
                    ) {
                        Text(text = batch, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}