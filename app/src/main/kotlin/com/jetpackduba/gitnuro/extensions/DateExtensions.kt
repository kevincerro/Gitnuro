package com.jetpackduba.gitnuro.extensions

import androidx.compose.runtime.Composable
import com.jetpackduba.gitnuro.LocalDateTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

@Composable
fun Long.toSmartSystemString(
    allowRelative: Boolean = true,
    useSystemDefaultFormat: Boolean? = null,
    showTime: Boolean = false,
): String {
    return Instant.ofEpochSecond(this).toSmartSystemString(
        allowRelative,
        useSystemDefaultFormat,
        showTime,
    )
}

@Composable
fun Instant.toSmartSystemString(
    allowRelative: Boolean = true,
    useSystemDefaultFormat: Boolean? = null,
    showTime: Boolean = false,
): String {
    val dateTimeFormat = LocalDateTimeFormat.current
    val useSystemDefault = useSystemDefaultFormat ?: dateTimeFormat.useSystemDefault

    val zoneId = ZoneId.systemDefault()
    val localDate = atZone(zoneId).toLocalDate()
    val currentTime = LocalDate.now(zoneId)

    val formattedDate = if (
        dateTimeFormat.useRelativeDate &&
        allowRelative &&
        localDate.isTodayOrYesterday(currentTime)
    ) {
        if (localDate.dayOfMonth == currentTime.dayOfMonth)
            "Today"
        else
            "Yesterday"
    } else {
        val systemLocale = System.getProperty("user.language")
        val locale = Locale.Builder()
            .setLanguageTag(systemLocale)
            .build()

        val formatter = if (useSystemDefault) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        } else {
            DateTimeFormatter.ofPattern(dateTimeFormat.customFormat, locale)
        }

        formatter.format(localDate)
    }

    val formattedTime = if (showTime) {
        val localDateTime = atZone(zoneId).toLocalDateTime()

        val timeFormatter = if (useSystemDefault) {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        } else if (dateTimeFormat.is24hours) {
            DateTimeFormatter.ofPattern("HH:mm")
        } else {
            DateTimeFormatter.ofPattern("hh:mm a")
        }

        timeFormatter.format(localDateTime)
    } else {
        ""
    }

    return "${formattedDate.trim()} ${formattedTime.trim()}".trim()
}

private fun LocalDate.isTodayOrYesterday(
    currentTime: LocalDate,
): Boolean {
    return this.year == currentTime.year &&
            this.month == currentTime.month &&
            this.dayOfMonth in (currentTime.dayOfMonth - 1)..currentTime.dayOfMonth
}
/**
 * Humanized distance between this epoch (in seconds) and now, e.g. "a week ago" or "2 months ago".
 */
fun Long.toRelativeAgoString(nowEpochSeconds: Long = Instant.now().epochSecond): String {
    val seconds = (nowEpochSeconds - this).coerceAtLeast(0)
    val minutes = seconds / 60.0
    val hours = minutes / 60.0
    val days = hours / 24.0
    val months = days / 30.44
    val years = days / 365.25

    fun plural(value: Double, single: String, unit: String): String {
        val rounded = Math.round(value).coerceAtLeast(1)
        return if (rounded == 1L) "$single ago" else "$rounded $unit ago"
    }

    return when {
        seconds < 45 -> "a few seconds ago"
        minutes < 45 -> plural(minutes, "a minute", "minutes")
        hours < 22 -> plural(hours, "an hour", "hours")
        days < 7 -> plural(days, "a day", "days")
        days < 26 -> plural(days / 7, "a week", "weeks")
        months < 11 -> plural(months, "a month", "months")
        else -> plural(years, "a year", "years")
    }
}
