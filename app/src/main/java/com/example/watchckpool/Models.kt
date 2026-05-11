package com.example.watchckpool

import kotlinx.serialization.Serializable
import java.lang.Exception

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val btcAddress: String = "",
    val selectedUrl: String = "https://solo.ckpool.org/users/",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val refreshInterval: Int = 15,

    // Miner Logging & Notifications
    val logMinerWorkers: Boolean = false,
    val logMinerBestShare: Boolean = false,
    val logMinerBestEver: Boolean = false,
    val notifyMinerWorkers: Boolean = false,
    val notifyMinerBestShare: Boolean = false,
    val notifyMinerBestEver: Boolean = false,

    // Workers Logging & Notifications
    val logWorkerBestShare: Boolean = false,
    val logWorkerBestEver: Boolean = false,
    val logWorkerHashrate: Boolean = false,
    val notifyWorkerBestShare: Boolean = false,
    val notifyWorkerBestEver: Boolean = false,
    val notifyWorkerHashrate: Boolean = false
)

@Serializable
data class LogEntry(val time: Long, val msg: String)

data class TimelineEvent(
    val fullTime: String,
    val time: String,
    val source: String,
    val name: String,
    val key: String,
    val newValue: String,
    val change: String,
    val isPositive: Boolean
)

object LogParser {
    fun parseAllLogs(minerContent: String, workerContent: String): List<TimelineEvent> {
        val minerEvents = parseLogLines(minerContent)
        val workerEvents = parseLogLines(workerContent)

        // Merge and sort by full timestamp string descending (newest at top)
        return (minerEvents + workerEvents).sortedByDescending { it.fullTime }
    }

    private fun parseLogLines(content: String): List<TimelineEvent> {
        if (content.isBlank() || content.startsWith("Log ") || content.startsWith("Error")) return emptyList()

        return content.lines().filter { it.contains("|") }.mapNotNull { line ->
            try {
                val parts = line.split("|")
                if (parts.size < 6) return@mapNotNull null

                val rawTime = parts[0]   // [2026-05-07 16:24:21]
                val source = parts[1]    // minerlog / workerslog
                val name = parts[2]      // minerName / workerName
                val key = parts[3]       // workers / hashrate1m / etc
                val oldVal = parts[4]
                val newVal = parts[5]

                val timeClean = rawTime.removeSurrounding("[", "]")
                val displayTime = timeClean.substringAfter(" ") // Just the HH:mm:ss

                val (change, isPositive) = calculateDiff(key, oldVal, newVal)

                TimelineEvent(
                    fullTime = timeClean,
                    time = timeClean,
                    source = source,
                    name = name,
                    key = key,
                    newValue = newVal,
                    change = change,
                    isPositive = isPositive
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun calculateDiff(key: String, old: String, new: String): Pair<String, Boolean> {
        return when (key) {
            "workers" -> {
                val diff = (new.toIntOrNull() ?: 0) - (old.toIntOrNull() ?: 0)
                val sign = if (diff >= 0) "+" else ""
                Pair("$sign$diff", diff >= 0)
            }

            "bestshare", "bestever" -> {
                // Logged only on increase, so always positive
                val oldD = old.toDoubleOrNull() ?: 0.0
                val newD = new.toDoubleOrNull() ?: 0.0
                Pair(WatchUtils.calculatePercentChange(oldD, newD), true)
            }

            "hashrate1m" -> {
                val oldD = WatchUtils.parseHashrate(old)
                val newD = WatchUtils.parseHashrate(new)
                val diffPercent = WatchUtils.calculatePercentChange(oldD, newD)
                Pair(diffPercent, newD >= oldD)
            }

            else -> Pair("", true)
        }
    }
}


