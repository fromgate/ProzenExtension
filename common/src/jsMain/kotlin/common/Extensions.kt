package common

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import org.w3c.dom.*
import kotlin.math.pow
import kotlin.math.round


fun JsonObject.getNestedElement(key: String): JsonElement? {
    val keys = key.split(".", "/", limit = 2)
    val element = this[keys.first()]
    return if (keys.size < 2) {
        element
    } else {
        element?.jsonObject?.getNestedElement(keys.last())
    }
}


fun JsonObject.obj(key: String): JsonObject? = getNestedElement(key)?.jsonObject
fun JsonObject.arr(key: String): JsonArray? = getNestedElement(key)?.jsonArray
fun JsonObject.bool(key: String): Boolean? = getNestedElement(key)?.jsonPrimitive?.booleanOrNull
fun JsonObject.int(key: String): Int? = getNestedElement(key)?.jsonPrimitive?.intOrNull
fun JsonObject.string(key: String): String? = getNestedElement(key)?.jsonPrimitive?.contentOrNull
fun JsonObject.long(key: String): Long? = getNestedElement(key)?.jsonPrimitive?.longOrNull
fun JsonObject.timeFromSecs(key: String): Instant? =
    getNestedElement(key)?.jsonPrimitive?.longOrNull?.let { Instant.fromEpochSeconds(it) }

fun JsonObject.time(key: String): Instant? =
    getNestedElement(key)?.jsonPrimitive?.longOrNull?.let { Instant.fromEpochMilliseconds(it) }


fun JsonObject.float(key: String): Float? = getNestedElement(key)?.jsonPrimitive?.floatOrNull
fun JsonObject.double(key: String): Double? = getNestedElement(key)?.jsonPrimitive?.doubleOrNull

fun JsonArray.obj(index: Int): JsonObject? = if (this.isEmpty() && index >= this.size) null else this[index].jsonObject

fun JsonElement.asString(): String? = this.jsonPrimitive.contentOrNull

fun Double.roundTo(decimalPlaces: Int = 3): Double {
    val factor = 10.0.pow(decimalPlaces)
    return round(this * factor) / factor
}

// Number format
fun Double.format(digits: Int? = null): String {
    val d = digits ?: if (this < 2) 2 else 1
    val rounded = this.roundTo(d)
    val parts = rounded.toString().split('.')
    val integerPart = parts[0].toInt().format()
    val fractionalPart = if (parts.size > 1) parts[1] else ""
    val formattedFraction = if (fractionalPart.length < d) {
        fractionalPart.padEnd(d, '0')
    } else {
        fractionalPart
    }
    return if (d > 0) "$integerPart.$formattedFraction" else integerPart
}

fun Int.format(): String {
    return this.toString()
        .reversed()
        .chunked(3)
        .joinToString(" ")
        .reversed()
}

// Date Time
fun Instant.toYYYYMD(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = this.toLocalDateTime(timeZone)
    return "${time.year}-${time.monthNumber}-${time.dayOfMonth}"
}

fun Instant.toYYYYMMDD(timeZone: TimeZone = TimeZone.currentSystemDefault(), showTime: Boolean = false): String {
    val time = this.toLocalDateTime(timeZone)
    val dateStr = "${time.year}-${time.monthNumber.padZero()}-${time.dayOfMonth.padZero()}"
    return "$dateStr${if (showTime) " ${time.hour.padZero()}_${time.minute.padZero()}_${time.second.padZero()}" else ""}"
}

fun Int.padZero(): String {
    return this.toString().padStart(2, '0')
}

fun Long.toInstant(): Instant {
    return Instant.fromEpochMilliseconds(this)
}

fun String.parseInstant(): Instant = Instant.parse(this)

fun Instant.toDDMMYY(): String {
    val dateTime = this.toLocalDateTime(TimeZone.currentSystemDefault())
    val day = dateTime.dayOfMonth.toString().padStart(2, '0')
    val month = dateTime.monthNumber.toString().padStart(2, '0')
    val year = dateTime.year.toString().takeLast(2)
    return "$day.$month.$year"
}

fun Instant.toDDMMYYYYHHMM(yearLength: Int = 4): String {
    val dateTime = this.toLocalDateTime(TimeZone.currentSystemDefault())
    val day = dateTime.dayOfMonth.toString().padStart(2, '0')
    val month = dateTime.monthNumber.toString().padStart(2, '0')
    val year = dateTime.year.toString().takeLast(yearLength)
    val hours = dateTime.hour.toString().padStart(2, '0')
    val minutes = dateTime.minute.toString().padStart(2, '0')
    return "$day.$month.$year $hours:$minutes"
}

fun HTMLElement.removeChildren() {
    this.asDynamic().replaceChildren()
}

fun Double?.isNanOrNull(): Boolean {
    return this == null || this.isNaN()
}

fun Int.secToHHMMSS(): String {
    val hours = "${(this / 3600).padZero()}:".takeIf { it != "00:" } ?: ""
    val minutes = ((this % 3600) / 60).padZero()
    val seconds = (this % 60).padZero()
    return "${hours}$minutes:$seconds"
}

fun Int.secToTimeString(): String {
    val hours = this / 3600
    val hoursStr = if (hours > 0) hours.paucal("час", "часа", "часов") else ""
    val minutes = (this % 3600) / 60
    val minutesStr = if (minutes > 0) minutes.paucal("минута", "минуты", "минут") else ""
    val secondsStr = (this % 60).paucal("секунда", "секунды", "секунд")
    return "$hoursStr $minutesStr $secondsStr".trim()
}


fun <A, B> Pair<A?, B?>?.toNonNullPairOrNull(): Pair<A, B>? {
    return if (this?.first != null && this.second != null) {
        Pair(this.first!!, this.second!!)
    } else null
}

fun Document.getSpanById(id: String): HTMLSpanElement? {
    return this.getElementById(id) as? HTMLSpanElement
}

fun Document.getDivById(id: String): HTMLDivElement? {
    return this.getElementById(id) as? HTMLDivElement
}

fun HTMLElement.getChildSpan(index: Int): HTMLSpanElement? {
    return this.children[index] as? HTMLSpanElement
}