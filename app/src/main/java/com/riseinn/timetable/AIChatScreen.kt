package com.riseinn.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.UUID

data class ChatMessage(val id: String = UUID.randomUUID().toString(), val text: String, val isUser: Boolean)

// The production URL where the Next.js app is deployed
const val AI_API_URL = "https://riseinn.vercel.app/api/ai-chat"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(savedBatch: String?) {
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var input by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var deviceId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val client = remember {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    // Add welcome message
    LaunchedEffect(Unit) {
        messages = listOf(
            ChatMessage(
                text = "Hello! I'm your Rise Timetable Assistant. I can check free slots, room numbers, or today's schedule for ${savedBatch ?: "your batch"}. What do you need?",
                isUser = false
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA))) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "AI Assistant", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                Text(text = "Ask about timetable & free slots", fontSize = 12.sp, color = Color.Gray)
            }
            if (savedBatch != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(text = savedBatch, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        // CHAT AREA
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (msg.isUser) 16.dp else 4.dp,
                            bottomEnd = if (msg.isUser) 4.dp else 16.dp
                        ),
                        color = if (msg.isUser) Color.Black else Color.White,
                        border = if (msg.isUser) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E5E5)),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = msg.text,
                            color = if (msg.isUser) Color.White else Color(0xFF1A1A1A),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }
            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E5E5))
                        ) {
                            Text(
                                text = "Thinking...",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        // INPUT AREA
        Surface(
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E5E5)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Ask something...", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF4F4F5),
                        unfocusedContainerColor = Color(0xFFF4F4F5),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        val query = input.trim()
                        if (query.isNotEmpty() && !isLoading) {
                            input = ""
                            messages = messages + ChatMessage(text = query, isUser = true)
                            isLoading = true
                            scope.launch {
                                // Auto scroll to bottom
                                listState.animateScrollToItem(messages.size)
                                
                                val reply = fetchAIResponse(client, query, savedBatch, deviceId)
                                messages = messages + ChatMessage(text = reply, isUser = false)
                                isLoading = false
                                
                                // Scroll to new message
                                listState.animateScrollToItem(messages.size)
                            }
                        }
                    },
                    enabled = input.trim().isNotEmpty() && !isLoading,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                    modifier = Modifier.size(50.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("↑", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

suspend fun fetchAIResponse(client: OkHttpClient, query: String, batch: String?, deviceId: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val jsonObject = JSONObject().apply {
                put("message", query)
                put("savedBatch", batch ?: "")
                put("deviceId", deviceId)
                put("history", JSONArray())
            }

            val requestBody = jsonObject.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(AI_API_URL)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val responseJson = JSONObject(responseBody)
                if (responseJson.optBoolean("success", false)) {
                    responseJson.optString("reply", "No reply found.")
                } else {
                    responseJson.optString("error", "Failed to connect to AI.")
                }
            } else {
                "Server error: \${response.code}"
            }
        } catch (e: Exception) {
            "Network error: \${e.message}. Are you connected to the internet?"
        }
    }
}
