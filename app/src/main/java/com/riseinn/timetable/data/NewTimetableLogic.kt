package com.riseinn.timetable.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

val USE_NEW_API_KEY = booleanPreferencesKey("use_new_api")

suspend fun setUseNewApi(context: Context, useNew: Boolean) {
    context.dataStore.edit { it[USE_NEW_API_KEY] = useNew }
}

suspend fun isUsingNewApi(context: Context): Boolean {
    // Defaulting to true for developer testing. To fallback, set this to false.
    return context.dataStore.data.map { it[USE_NEW_API_KEY] ?: true }.first()
}

data class Faculty(val code: String, val name: String, val subject: String)
data class Room(val label: String)
data class TimeSlot(val label: String, val startTime: String, val endTime: String, val sortOrder: Int)

data class BatchInfo(val name: String, val token: String, val uuid: String)

data class TimetableEntry(
    val id: String?,
    val batch_id: String?,
    val time_slot_id: String?,
    val faculty_id: String?,
    val room_id: String?,
    val day_of_week: Int?,
    val subject: String?,
    val custom_title: String?,
    val is_extra: Boolean?
)

data class DisplayCard(
    val timeLabel: String,
    val subject: String?,
    val facultyCode: String?,
    val room: String?,
    val isExtra: Boolean,
    val sortOrder: Int = 0
)

object LookupData {
    val batches = listOf(
        BatchInfo("RF28X", "pkvx9qxyjc", "069b2769-033d-4477-8e76-5ddcbb0dd9af"),
        BatchInfo("RF27A", "wd77h89h9q", "0a9c2fa5-7360-4e58-a516-08a11a0b1f13"),
        BatchInfo("RF27U", "jgavfgw9ht", "002f303c-7172-4ebf-9f27-b3f538a9dbbd"),
        BatchInfo("RF27V", "3rpwsrbfb3", "b7d31b97-3b9c-41b7-98c4-99aef6cfceb0"),
        BatchInfo("RF27Z", "a8zcpg23eb", "ce15a1a0-1061-4aca-82f3-81d7945ddfe6"),
        BatchInfo("RF28A", "jguezn8b3u", "22533def-c42d-459c-be33-19d6ac460c96"),
        BatchInfo("RF28E", "2a3kygns8x", "3d5c7206-e32b-460c-9eae-6188fab4352a"),
        BatchInfo("RF28Y", "62cjs994gr", "dfd84e31-895c-4155-8d6f-e693dd1dc817"),
        BatchInfo("RF28Z", "n8ssweq5sk", "ba008310-bb7a-4311-83b9-2106d47a7d37"),
        BatchInfo("RR27A", "hkzs77yg7p", "fc2448fb-f41b-434a-b1d6-cb34b40ff770"),
        BatchInfo("RB27X", "svcjgrw4r2", "6d2f48f8-e1af-418d-995d-b2f553f8ad5c"),
        BatchInfo("RM27X", "qua49n95n7", "057d1edd-dc76-4f7b-8637-cfdb6dccef91"),
        BatchInfo("RM27Z", "seyh3pubvf", "52eb4f49-dd23-413b-96f0-ad6a0e8e13ba"),
        BatchInfo("RM28X", "tz6pkkkkzp", "495e9cba-e123-4b78-8ad0-34b4ef615a6d"),
        BatchInfo("RM28Y", "wgs5y8m938", "18b01d54-48b2-427f-8e43-274c41fec0b6"),
        BatchInfo("RI29A", "j79z5mknv7", "8a7dc53d-581c-4029-954c-36c5bfb91e21"),
        BatchInfo("RI30A", "83np7ca8gj", "2e280be0-05e9-4748-a2ae-22c87bfffa86")
    )
    
    val timeSlots = mapOf(
        "57121802-b6cf-475b-9669-9ca0b3e2b607" to TimeSlot("9:00 AM", "09:00:00", "10:00:00", 1),
        "77fefed5-d5bf-44ec-832b-03c18be2f9eb" to TimeSlot("10:20 AM", "10:20:00", "11:20:00", 2),
        "0c4a089d-2c74-49f1-84f6-c4d843a40524" to TimeSlot("11:30 AM", "11:30:00", "12:30:00", 3),
        "100f0212-00d4-4079-8087-6424e31c2225" to TimeSlot("12:40 PM", "12:40:00", "13:40:00", 4),
        "2cc4bb24-4c87-482b-a53a-5ef6da34bd50" to TimeSlot("2:00 PM", "14:00:00", "15:15:00", 5),
        "08a80e87-215c-407a-830b-89cc2fe3f2a2" to TimeSlot("3:30 PM", "15:30:00", "16:35:00", 6),
        "5c39d555-896e-4ef0-8ad9-bc6dcbf77753" to TimeSlot("4:45 PM", "16:45:00", "17:50:00", 7),
        "3a0950d4-87b8-457e-a157-cdcd43a708b2" to TimeSlot("6:00 PM", "18:00:00", "19:05:00", 8)
    )

    val faculty = mapOf(
        "01eeee89-f67c-47af-b92d-9dad0a1c0dc0" to Faculty("AF", "Ahtisham Farhat", "Mathematics"),
        "d3ad9eb2-1a3b-4352-b6fc-26c2e1724292" to Faculty("AK", "Azhan Khalid", "Mathematics"),
        "137d2b68-942c-4983-9324-9e421b726d25" to Faculty("AR", "Abdur Rahman Haider", "Chemistry"),
        "7e59824d-bcdd-4f85-bdb7-8ad0dcdd18e7" to Faculty("FB", "Mirza Ali Faisal", "Physics"),
        "bb034958-32cd-45b2-9bfa-fe17b0684f3d" to Faculty("FN", "Faizan Kaleem", "Physics"),
        "e715f71d-0630-4953-93d6-40a62de0d554" to Faculty("JM", "Junaid Maqsood Ganie", "Chemistry"),
        "2e5c5ffd-c173-4050-b328-9bebd593ff44" to Faculty("MA", "Mohd Azhardin Ganayee", "Chemistry"),
        "a0e86167-0137-450d-a44a-953b46c2f04c" to Faculty("NF", "Md Nafees Ur Rahman", "Mathematics"),
        "2a944190-f0b2-4b71-98c2-22939c0f39c5" to Faculty("NS", "Md Nausher", "Physics"),
        "388116d4-83f8-4136-b471-364d676f6a16" to Faculty("RR", "Rakesh Raman", "Biology"),
        "65de972c-4fa7-440a-849a-b5b4c4297eab" to Faculty("SA", "Sumaira Altaf", "Social Studies"),
        "435a58cf-a16d-48bd-890b-e18a2ec06031" to Faculty("ST", "Sahreen Tayubi", "English"),
        "01cb86b9-4c46-4312-9bad-8899d2f654fb" to Faculty("SZ", "Zakir Hussain", "Urdu"),
        "ad0b9943-cf10-4f26-abbe-4177f9acf651" to Faculty("UR", "Usman Rafiqee", "Biology")
    )

    val rooms = mapOf(
        "3b332baf-6c76-4e96-bf52-9aae845fbad7" to Room("R02"),
        "6d71516e-95a4-410f-8b61-8330a1507e5a" to Room("R03"),
        "af554fc9-277c-4d0d-957e-719c34f1b2b2" to Room("R04"),
        "99fab988-f8d1-4c86-8993-4efe52c42827" to Room("R06"),
        "f79007ab-5910-490f-a9a7-f12f004523cc" to Room("R07"),
        "0fe5e2a8-46a0-4b92-85e4-f97cde65017c" to Room("R08"),
        "b0968907-8c62-4b53-bc3e-e4821db94ae9" to Room("R09"),
        "741f0fbc-f4c8-446c-8376-0b6ccf45b9b1" to Room("R10"),
        "593ecb86-8fb4-4eb0-ad31-ff121426fa6d" to Room("R11"),
        "50ee17ad-3bfb-4484-b277-2346f9f2a6b1" to Room("R12")
    )
}

object NewTimetableLogic {
    private val client = OkHttpClient()
    private val gson = Gson()

    fun getDailySchedule(
        rawEntries: List<TimetableEntry>,
        targetBatchId: String,
        targetDayOfWeek: Int
    ): List<DisplayCard> {
        return rawEntries
            .filter { it.batch_id == targetBatchId && it.day_of_week == targetDayOfWeek }
            .sortedBy { LookupData.timeSlots[it.time_slot_id ?: ""]?.sortOrder ?: 99 }
            .mapNotNull { entry ->
                val slot = LookupData.timeSlots[entry.time_slot_id ?: ""]
                val faculty = LookupData.faculty[entry.faculty_id ?: ""]
                val room = LookupData.rooms[entry.room_id ?: ""]

                val timeLabel = slot?.let { 
                    try {
                        "${it.startTime.substring(0, 5)} - ${it.endTime.substring(0, 5)}"
                    } catch (e: Exception) {
                        it.label
                    }
                } ?: "TBD"

                val subjectStr = entry.custom_title ?: entry.subject ?: faculty?.subject ?: "Unknown"

                DisplayCard(
                    timeLabel = timeLabel,
                    subject = subjectStr,
                    facultyCode = faculty?.code,
                    room = room?.label,
                    isExtra = entry.is_extra ?: false,
                    sortOrder = slot?.sortOrder ?: 99
                )
            }
    }

    suspend fun fetchAndCacheScheduleForBatch(context: Context, batchUuid: String): List<TimetableEntry> {
        // Obfuscated Supabase URL and Anon Key to prevent easy tracing/scraping
        val baseUrl = String(charArrayOf('h','t','t','p','s',':','/','/','n','m','u','n','z','z','j','b','t','b','p','p','j','y','c','e','q','c','y','f','.','s','u','p','a','b','a','s','e','.','c','o','/','r','e','s','t','/','v','1','/','s','c','h','e','d','u','l','e','d','_','c','l','a','s','s','e','s'))
        val k1 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
        val k2 = "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5tdW56empidGJwcGp5Y2VxY3lmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzg0Njg5ODYsImV4cCI6MjA5NDA0NDk4Nn0."
        val k3 = "CEc_zlYD095AujcKg4_CV1QkcKm0U_pp9zIfNrSoMgE"
        val fullKey = "$k1$k2$k3"

        val request = Request.Builder()
            .url("$baseUrl?select=*,weeks!inner(status)&weeks.status=eq.published&batch_id=eq.$batchUuid")
            .header("Accept", "application/json")
            .header("apikey", fullKey)
            .header("Authorization", "Bearer $fullKey")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            
            val jsonText = response.body?.string() ?: ""
            
            val oldJson = context.dataStore.data.map { 
                it[androidx.datastore.preferences.core.stringPreferencesKey("cached_json_data")] ?: "" 
            }.first()
            
            if (oldJson.isNotBlank() && oldJson != jsonText) {
                com.riseinn.timetable.NotificationHelper.showUpdateNotification(context, batchUuid)
            }

            context.dataStore.edit { 
                it[androidx.datastore.preferences.core.stringPreferencesKey("cached_json_data")] = jsonText
                it[androidx.datastore.preferences.core.longPreferencesKey("last_refresh_time")] = System.currentTimeMillis()
            }
            val typeToken = object : TypeToken<List<TimetableEntry>>() {}.type
            gson.fromJson(jsonText, typeToken) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            getCachedSchedule(context)
        }
    }

    suspend fun getCachedSchedule(context: Context): List<TimetableEntry> {
        val jsonText = context.dataStore.data.map { 
            it[androidx.datastore.preferences.core.stringPreferencesKey("cached_json_data")] ?: "" 
        }.first()
        if (jsonText.isBlank()) return emptyList()
        return try {
            val typeToken = object : TypeToken<List<TimetableEntry>>() {}.type
            gson.fromJson(jsonText, typeToken) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
