package common

import kotlinx.browser.document
import kotlinx.datetime.*
import org.w3c.dom.HTMLInputElement

fun getDateRange(fromDateId: String = "from-date", toDateId: String = "to-date"): Pair<Instant, Instant>? {
    val fromDateInput = document.getElementById(fromDateId) as? HTMLInputElement
    val toDateInput = document.getElementById(toDateId) as? HTMLInputElement
    val fromDate = fromDateInput?.value?.takeIf { it.isNotEmpty() }?.let { Instant.parse("${it}T00:00:00Z") }
    val toDate = toDateInput?.value?.takeIf { it.isNotEmpty() }?.let { Instant.parse("${it}T23:59:59Z") }
    return if (fromDate != null && toDate != null) {
        Pair(fromDate, toDate)
    } else null
}

fun setDateRange(fromDate: Instant?, toDate: Instant?, fromDateId: String = "from-date", toDateId: String = "to-date") {
    val fromDateInput = document.getElementById(fromDateId) as? HTMLInputElement
    val toDateInput = document.getElementById(toDateId) as? HTMLInputElement
    val zone = TimeZone.currentSystemDefault()
    fromDateInput?.value = fromDate?.toLocalDateTime(zone)?.date?.toString() ?: ""
    toDateInput?.value = toDate?.toLocalDateTime(zone)?.date?.toString() ?: ""
}

fun setDateRange(range: Pair<Instant, Instant>, fromDateId: String = "from-date", toDateId: String = "to-date") {
    setDateRange(range.first, range.second, fromDateId, toDateId)
}

fun fromInputDate(dateStr: String, endOfDate: Boolean = false): Instant? {
    return try {
        Instant.parse("${dateStr}T${if (endOfDate) "23:59:59" else "00:00:00"}Z")
    } catch (e: Exception) {
        console.dError("Failed to parse date: $dateStr")
        null
    }
}

fun getLastWeek(): Pair<Instant, Instant> {
    val now = Clock.System.now()
    val lastWeek = now.minus(DateTimePeriod(days = 7), TimeZone.currentSystemDefault())
    return lastWeek to now
}

fun getLastMonth(): Pair<Instant, Instant> {
    val now = Clock.System.now()
    val lastWeek = now.minus(DateTimePeriod(months = 1), TimeZone.currentSystemDefault())
    return lastWeek to now
}


fun getCurrentMonth(): Pair<Instant, Instant> {
    val now = Clock.System.now()
    val firstOfMonth = now.toLocalDateTime(TimeZone.currentSystemDefault()).run {
        Instant.parse("${date.year}-${date.monthNumber}-01T00:00:00Z")
    }
    return firstOfMonth to now
}

fun getLast30Days(): Pair<Instant, Instant> {
    val now = Clock.System.now()
    val last30Days = now.minus(DateTimePeriod(days = 30), TimeZone.currentSystemDefault())
    return last30Days to now
}

fun getLast6Months(): Pair<Instant, Instant> {
    val now = Clock.System.now()
    val last6Months = now.minus(DateTimePeriod(months = 6), TimeZone.currentSystemDefault())
    return last6Months to now
}

fun getLastYear(): Pair<Instant, Instant> {
    val now = Clock.System.now()
    val lastYear = now.minus(DateTimePeriod(years = 1), TimeZone.currentSystemDefault())
    return lastYear to now
}

fun getCurrentYear(): Pair<Instant, Instant> {
    val now = Clock.System.now()
    val firstOfYear = now.toLocalDateTime(TimeZone.currentSystemDefault()).run {
        Instant.parse("${date.year}-01-01T00:00:00Z")
    }
    return firstOfYear to now
}

fun Pair<Instant, Instant>.toStringPair(): Pair<String, String> {
    return this.first.toYYYYMMDD() to this.second.toYYYYMMDD()
}