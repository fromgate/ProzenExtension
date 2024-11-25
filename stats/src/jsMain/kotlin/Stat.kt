import common.*
import kotlinx.datetime.*
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.h4
import kotlinx.html.span
import org.w3c.dom.HTMLElement

data class Stat(
    var type: String,
    var count: Int = 0,
    var impressions: Int = 0,
    var impressionsMed: Int = 0,
    var impressionsAvg: Int = 0,
    var clicks: Int = 0,
    var clicksMed: Int = 0,
    var clicksAvg: Int = 0,
    var typeSpecificViews: Int = 0,
    var typeSpecificViewsMed: Int = 0,
    var typeSpecificViewsAvg: Int = 0,
    var deepViews: Int = 0,
    var deepViewsMed: Int = 0,
    var deepViewsAvg: Int = 0,
    var minAddTime: Long = 0L,
)

fun TagConsumer<HTMLElement>.statBlock(stat: Stat) {
    div(classes = "prozen-statistics-section") {
        h4 { +typeToDescription(stat.type) }
        div(classes = "prozen-statistics-list") {
            statRow("Опубликовано", stat.count.format(), stat.countPerDay())
            statRow("Показы", stat.impressions.format())
            statRow("Клики", stat.clicks.format(), stat.ctrVtr())
            statRow(stat.showsOrViews(), stat.deepViews.format())
            statRow("Первая публикация", stat.minAddTime.toInstant().toDDMMYYYYHHMM(), stat.sinceStartDay())
        }
    }
}

fun Stat.showsOrViews(): String {
    return when (type) {
        "article" -> "Дочитывания"
        "total" -> "Просмотры / дочитывания"
        else -> "Просмотры"
    }
}
fun TagConsumer<HTMLElement>.statRow(description: String, value: String, secondaryValue: String? = null) {
    div(classes = "prozen-statistics-row") {
        span(classes = "prozen-statistics-description") { +description }
        span(classes = "prozen-statistics-value") {
            span(classes = "prozen-statistics-main-value") { +value }
            if (!secondaryValue.isNullOrEmpty()) {
                span(classes = "prozen-statistics-secondary-value") { +secondaryValue }
            }
        }
    }
}

fun typeToDescription(type: String): String {
    return when (type) {
        "article" -> "Статьи"
        "brief" -> "Посты"
        "gif" -> "Видео"
        "short_video" -> "Ролики"
        "total" -> "Сводная статистика"
        else -> type
    }
}

fun Stat.countPerDay(): String {
    if (count == 0) return ""
    val currentDate = Clock.System.now()
    val startDate = minAddTime.toInstant()
    val period = currentDate.minus(startDate)
    val days = period.inWholeDays.toInt()
    val countPerDay = count.toDouble() / days
    return "${countPerDay.format(2)} в день"
}

fun Stat.sinceStartDay(): String {
    if (minAddTime == 0L) return ""
    val currentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val startDate = Instant.fromEpochMilliseconds(minAddTime)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val period = startDate.until(currentDate, DateTimeUnit.DAY)
    val totalDays = period
    val years = totalDays / 365
    val remainingDaysAfterYears = totalDays % 365
    val months = remainingDaysAfterYears / 30
    val days = remainingDaysAfterYears % 30
    val yearsText = if (years > 0) paucalYear(years) else ""
    val monthsText = if (months > 0) paucalMonth(months) else ""
    val daysText = if (days > 0) paucalDay(days) else ""
    return "Прошло ${paucalDay(totalDays)} ($yearsText, $monthsText, $daysText)"
}

fun Stat.ctrVtr(): String {
    if (type == "total" || impressions == 0) return ""
    return try {
        val (ctrVtr, ctrVtrValue) = when (type) {
            "article" -> "CTR" to (clicks.toDouble() / impressions * 100).format(2)
            else -> "VTR" to (deepViews.toDouble() / impressions * 100).format(2)
        }
        "$ctrVtr: $ctrVtrValue%"
    } catch (e: Exception) {
        ""
    }
}

