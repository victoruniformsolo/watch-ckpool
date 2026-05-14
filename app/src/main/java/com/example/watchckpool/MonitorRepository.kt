package com.example.watchckpool

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.dataStore by preferencesDataStore("settings")

class MonitorRepository(private val context: Context) {
    private val BTC_KEY = stringPreferencesKey("btc_address")
    private val URL_KEY = stringPreferencesKey("selected_url")
    private val THEME_KEY = stringPreferencesKey("theme_mode")
    private val LAST_DATA = stringPreferencesKey("last_data")
    private val PREV_DATA = stringPreferencesKey("prev_data")
    private val LOGS_KEY = stringPreferencesKey("system_logs")
    private val LAST_SYNC_KEY = longPreferencesKey("last_sync_time")
    private val REFRESH_INTERVAL = intPreferencesKey("refresh_interval")
    private val BEST_EVER_WORKER = stringPreferencesKey("best_ever_worker")
    private val BEST_SHARE_WORKER = stringPreferencesKey("best_share_worker")

    // Log & Notify Keys
    private val LOG_MINER_WORKERS = booleanPreferencesKey("log_miner_workers")
    private val LOG_MINER_BEST_SHARE = booleanPreferencesKey("log_miner_best_share")
    private val LOG_MINER_BEST_EVER = booleanPreferencesKey("log_miner_best_ever")
    private val NOTIFY_MINER_WORKERS = booleanPreferencesKey("notify_miner_workers")
    private val NOTIFY_MINER_BEST_SHARE = booleanPreferencesKey("notify_miner_best_share")
    private val NOTIFY_MINER_BEST_EVER = booleanPreferencesKey("notify_miner_best_ever")

    private val LOG_WORKER_BEST_SHARE = booleanPreferencesKey("log_worker_best_share")
    private val LOG_WORKER_BEST_EVER = booleanPreferencesKey("log_worker_best_ever")
    private val LOG_WORKER_HASHRATE = booleanPreferencesKey("log_worker_hashrate")
    private val NOTIFY_WORKER_BEST_SHARE = booleanPreferencesKey("notify_worker_best_share")
    private val NOTIFY_WORKER_BEST_EVER = booleanPreferencesKey("notify_worker_best_ever")
    private val NOTIFY_WORKER_HASHRATE = booleanPreferencesKey("notify_worker_hashrate")

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        try {
            AppSettings(
                btcAddress = p[BTC_KEY] ?: "",
                selectedUrl = p[URL_KEY] ?: "https://solo.ckpool.org/users/",
                themeMode = try {
                    ThemeMode.valueOf(p[THEME_KEY] ?: ThemeMode.SYSTEM.name)
                } catch (e: Exception) {
                    ThemeMode.SYSTEM
                },

                logMinerWorkers = p[LOG_MINER_WORKERS] ?: false,
                logMinerBestShare = p[LOG_MINER_BEST_SHARE] ?: false,
                logMinerBestEver = p[LOG_MINER_BEST_EVER] ?: false,
                notifyMinerWorkers = p[NOTIFY_MINER_WORKERS] ?: false,
                notifyMinerBestShare = p[NOTIFY_MINER_BEST_SHARE] ?: false,
                notifyMinerBestEver = p[NOTIFY_MINER_BEST_EVER] ?: false,

                logWorkerBestShare = p[LOG_WORKER_BEST_SHARE] ?: false,
                logWorkerBestEver = p[LOG_WORKER_BEST_EVER] ?: false,
                logWorkerHashrate = p[LOG_WORKER_HASHRATE] ?: false,
                notifyWorkerBestShare = p[NOTIFY_WORKER_BEST_SHARE] ?: false,
                notifyWorkerBestEver = p[NOTIFY_WORKER_BEST_EVER] ?: false,
                notifyWorkerHashrate = p[NOTIFY_WORKER_HASHRATE] ?: false,
                refreshInterval = p[REFRESH_INTERVAL] ?: 15,
                bestEverWorker = p[BEST_EVER_WORKER] ?: "",
                bestShareWorker = p[BEST_SHARE_WORKER] ?: ""
            )
        } catch (e: Exception) {
            AppSettings() // Safe fallback
        }
    }

    val lastSyncTime: Flow<Long> = context.dataStore.data.map { it[LAST_SYNC_KEY] ?: 0L }

    val lastData: Flow<String?> = context.dataStore.data.map { it[LAST_DATA] }
    val prevData: Flow<String?> = context.dataStore.data.map { it[PREV_DATA] }

    val logs: Flow<List<LogEntry>> = context.dataStore.data.map { p ->
        val json = p[LOGS_KEY] ?: "[]"
        try {
            Json.decodeFromString<List<LogEntry>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    val latestLog: Flow<LogEntry?> = logs.map { it.firstOrNull() }

    suspend fun updateBtc(addr: String) = context.dataStore.edit { it[BTC_KEY] = addr }
    suspend fun updateUrl(url: String) = context.dataStore.edit { it[URL_KEY] = url }
    suspend fun updateTheme(mode: ThemeMode) = context.dataStore.edit { it[THEME_KEY] = mode.name }
    suspend fun updateRefreshInterval(interval: Int) = context.dataStore.edit { it[REFRESH_INTERVAL] = interval }
    suspend fun updateBestEverWorker(name: String) = context.dataStore.edit { it[BEST_EVER_WORKER] = name }
    suspend fun updateBestShareWorker(name: String) = context.dataStore.edit { it[BEST_SHARE_WORKER] = name }

    suspend fun updateSetting(key: String, value: Boolean) = context.dataStore.edit { p ->
        when (key) {
            "logMinerWorkers" -> p[LOG_MINER_WORKERS] = value
            "logMinerBestShare" -> p[LOG_MINER_BEST_SHARE] = value
            "logMinerBestEver" -> p[LOG_MINER_BEST_EVER] = value
            "notifyMinerWorkers" -> p[NOTIFY_MINER_WORKERS] = value
            "notifyMinerBestShare" -> p[NOTIFY_MINER_BEST_SHARE] = value
            "notifyMinerBestEver" -> p[NOTIFY_MINER_BEST_EVER] = value
            "logWorkerBestShare" -> p[LOG_WORKER_BEST_SHARE] = value
            "logWorkerBestEver" -> p[LOG_WORKER_BEST_EVER] = value
            "logWorkerHashrate" -> p[LOG_WORKER_HASHRATE] = value
            "notifyWorkerBestShare" -> p[NOTIFY_WORKER_BEST_SHARE] = value
            "notifyWorkerBestEver" -> p[NOTIFY_WORKER_BEST_EVER] = value
            "notifyWorkerHashrate" -> p[NOTIFY_WORKER_HASHRATE] = value
        }
    }

    suspend fun saveResponse(json: String) = context.dataStore.edit { p ->
        p[PREV_DATA] = p[LAST_DATA] ?: ""
        p[LAST_DATA] = json
        p[LAST_SYNC_KEY] = System.currentTimeMillis()
    }

    suspend fun appendPhysicalLog(fileName: String, message: String) {
        try {
            val file = File(context.filesDir, fileName)
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val line = "[$time]|$message\n"
            FileOutputStream(file, true).use { it.write(line.toByteArray()) }
        } catch (e: Exception) {
            addLog("Failed to write to $fileName: ${e.localizedMessage}")
        }
    }

    fun getPhysicalLogContent(fileName: String): String {
        return try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) file.readText() else "Log $fileName is empty"
        } catch (e: Exception) {
            "Error reading log: ${e.localizedMessage}"
        }
    }


    fun clearPhysicalLog(fileName: String, filterKey: String? = null) {
        try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return

            if (filterKey == null) {
                file.delete()
            } else {
                val lines = file.readLines()
                val filteredLines = lines.filterNot { it.contains("|$filterKey|") }
                if (filteredLines.isEmpty()) {
                    file.delete()
                } else {
                    file.writeText(filteredLines.joinToString("\n") + "\n")
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun getLogFile(fileName: String): File? {
        val file = File(context.filesDir, fileName)
        return if (file.exists()) file else null
    }

    suspend fun addLog(msg: String) = context.dataStore.edit { p ->
        val currentJson = p[LOGS_KEY] ?: "[]"
        val list = try {
            Json.decodeFromString<MutableList<LogEntry>>(currentJson)
        } catch (e: Exception) {
            mutableListOf()
        }
        list.add(0, LogEntry(System.currentTimeMillis(), msg))
        val limited = list.take(100) // Auto-clean: Keep last 100 entries
        p[LOGS_KEY] = Json.encodeToString(limited)
    }

    suspend fun clearLogs() = context.dataStore.edit { it.remove(LOGS_KEY) }
}
