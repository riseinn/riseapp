package com.riseinn.timetable.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

val Context.dataStore by preferencesDataStore(name = "riseinn_settings")

object TimetableRepository {
    private val client = OkHttpClient()
    private const val DAILY_CSV_URL = "https://docs.google.com/spreadsheets/d/e/2PACX-1vRbgw-2QiguaDpy7rl9AZUQxtPV3T55TDseLAHBQE3z7ef0niqrasuil7Bg0V-KDzvBLCTfb5BnH-7Z/pub?gid=1952632243&single=true&output=csv"
    
    val BATCH_KEY = stringPreferencesKey("riseinn_batch")
    val CACHED_DATA_KEY = stringPreferencesKey("cached_daily_data")
    val TUTORIAL_SHOWN_KEY = booleanPreferencesKey("tutorial_shown")
    
    // NEW: Key to track the exact minute the data was updated
    val LAST_REFRESH_KEY = longPreferencesKey("last_refresh_time")

    suspend fun getSavedBatch(context: Context): String? {
        return context.dataStore.data.map { it[BATCH_KEY] }.first()
    }

    suspend fun getLastRefreshTime(context: Context): Long {
        return context.dataStore.data.map { it[LAST_REFRESH_KEY] ?: 0L }.first()
    }

    suspend fun saveBatch(context: Context, batch: String) {
        context.dataStore.edit { it[BATCH_KEY] = batch }
    }

    suspend fun fetchDailyData(context: Context): List<List<String>> {
        val request = Request.Builder()
            .url("$DAILY_CSV_URL&t=${System.currentTimeMillis()}")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            
            val csvText = response.body?.string() ?: ""
            val parsed = csvText.lines()
                .filter { it.isNotBlank() }
                .map { it.split(",") }
            
            // Save the data AND the current time
            context.dataStore.edit { 
                it[CACHED_DATA_KEY] = csvText 
                it[LAST_REFRESH_KEY] = System.currentTimeMillis()
            }
            parsed
        } catch (e: Exception) {
            val cachedCsv = context.dataStore.data.map { it[CACHED_DATA_KEY] }.first() ?: ""
            cachedCsv.lines().filter { it.isNotBlank() }.map { it.split(",") }
        }
    }

    fun filterForBatch(data: List<List<String>>, batchName: String): List<String>? {
        if (data.isEmpty()) return null
        return data.find { it.isNotEmpty() && it[0].equals(batchName, ignoreCase = true) }
    }
}