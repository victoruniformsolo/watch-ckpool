package com.example.watchckpool

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

object WatchUtils {
    private val suffixes = mapOf('K' to 1e3, 'M' to 1e6, 'G' to 1e9, 'T' to 1e12, 'P' to 1e15)

    fun formatTimestamp(seconds: Long, fullDate: Boolean = false): String {
        if (seconds == 0L) return "Never"
        val date = Date(seconds * 1000)
        val pattern = if (fullDate) "MMM dd, yyyy" else "HH:mm:ss"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    }

    fun shortUsersAddy (inputLongString: String): String {
//    var shorterString = " "

        if (inputLongString.length > 12)
        {
            val shorterString = "${inputLongString.take(6)}...${inputLongString.takeLast(6)}"
            return shorterString
        }else
        {
            return inputLongString
        }
    }

    fun formatLargeNumber(number: Any?): String {
        val value = when (number) {
            is Number -> number.toDouble()
            is String -> number.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

        val formatter = NumberFormat.getInstance(Locale.US)
        formatter.maximumFractionDigits = 0 // <--- This strips all decimals
        formatter.isGroupingUsed = true      // <--- This keeps the commas

        return formatter.format(value)
    }



    fun parseHashrate(rate: String?): Double {
        if (rate == null || rate == "N/A") return 0.0
        val numeric = rate.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
        val suffix = rate.lastOrNull()?.uppercaseChar()
        return numeric * (suffixes[suffix] ?: 1.0)
    }

    fun formatHashrate(value: Double): String {
        if (value <= 0) return "0 H"
        val units = listOf("H", "K", "M", "G", "T", "P")
        val i = (kotlin.math.log10(value) / 3).toInt().coerceIn(0, units.size - 1)
        val num = value / 1000.0.pow(i.toDouble())
        return String.format(Locale.US, "%.2f%s", num, units[i])
    }


fun calculatePercentChange(old: Double, new: Double): String {
    if (old == 0.0) return "+100%"

    val change = ((new - old) / old) * 100

    // Use NumberFormat to apply grouping separators (e.g., 12,345.6)
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 1
        isGroupingUsed = true
    }

    val formatted = formatter.format(change)
    return if (change >= 0) "+$formatted%" else "$formatted%"
}
    fun cleanWorkerName(fullName: String?): String {
        if (fullName == null) return "Unknown"
        return fullName.substringAfterLast('.')
    }

    fun formatTimeAgo(timestamp: Long): String {
        val diff = (System.currentTimeMillis() / 1000) - timestamp
        return when {
            diff < 60 -> "Just now" //diff < 60 -> "${diff}s ago"
            diff < 3600 -> "${diff / 60}m ago"
            diff < 86400 -> "${diff / 3600}h ago"
            else -> "${diff / 86400}d ago"
        }
    }
}
