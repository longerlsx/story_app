package com.longerlsx.storyapp.feature.reader.tts

object ReaderTtsTimeLabelFormatter {
    fun formatRemainingMillis(remainingMillis: Long): String {
        val totalMinutes = ((remainingMillis + 59_999L) / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours <= 0 -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }
}
