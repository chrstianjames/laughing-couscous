package com.shortly.app.ui.util

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object Utils {
    fun formatCount(n: Int): String {
        return when {
            n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
            n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
            else -> n.toString()
        }
    }

    fun formatDuration(ms: Long): String {
        if (ms < 0) return "0:00"
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    fun timeAgo(isoTime: String?): String {
        if (isoTime.isNullOrBlank()) return ""
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = fmt.parse(isoTime.substringBefore(".").substringBefore("+").substringBefore("Z")) ?: return ""
            val now = Date()
            val diff = now.time - date.time
            when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
                diff < TimeUnit.HOURS.toMillis(1) -> "${diff / TimeUnit.MINUTES.toMillis(1)}m ago"
                diff < TimeUnit.DAYS.toMillis(1) -> "${diff / TimeUnit.HOURS.toMillis(1)}h ago"
                diff < TimeUnit.DAYS.toMillis(7) -> "${diff / TimeUnit.DAYS.toMillis(1)}d ago"
                diff < TimeUnit.DAYS.toMillis(30) -> "${diff / TimeUnit.DAYS.toMillis(7)}w ago"
                else -> "${diff / TimeUnit.DAYS.toMillis(30)}mo ago"
            }
        } catch (_: Exception) {
            isoTime
        }
    }
}
