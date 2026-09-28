package com.shortly.app.ui.util

import com.shortly.app.platform.formatOneDecimal
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

object Utils {
    fun formatCount(n: Int): String {
        return when {
            n >= 1_000_000 -> formatOneDecimal(n / 1_000_000.0) + "M"
            n >= 1_000 -> formatOneDecimal(n / 1_000.0) + "K"
            else -> n.toString()
        }
    }

    fun formatDuration(ms: Long): String {
        if (ms < 0) return "0:00"
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "$m:" + s.toString().padStart(2, '0')
    }

    /** Parses the API's ISO-8601 timestamps ("2026-01-01T00:00:00+00:00"). */
    fun parseIso(isoTime: String?): Instant? {
        if (isoTime.isNullOrBlank()) return null
        return try {
            Instant.parse(isoTime)
        } catch (_: Exception) {
            try {
                // No zone suffix -> treat as UTC
                Instant.parse(isoTime.substringBefore(".").substringBefore("+").removeSuffix("Z") + "Z")
            } catch (_: Exception) {
                null
            }
        }
    }

    fun timeAgo(isoTime: String?): String {
        val date = parseIso(isoTime) ?: return isoTime ?: ""
        val diff = Clock.System.now().toEpochMilliseconds() - date.toEpochMilliseconds()
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour
        return when {
            diff < minute -> "just now"
            diff < hour -> "${diff / minute}m ago"
            diff < day -> "${diff / hour}h ago"
            diff < 7 * day -> "${diff / day}d ago"
            diff < 30 * day -> "${diff / (7 * day)}w ago"
            else -> "${diff / (30 * day)}mo ago"
        }
    }
}
