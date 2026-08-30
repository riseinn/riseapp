package com.riseinn.timetable.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "riseinn_settings")

object TimetableRepository {
    val BATCH_KEY = stringPreferencesKey("riseinn_batch")
    val TUTORIAL_SHOWN_KEY = booleanPreferencesKey("tutorial_shown")
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
}