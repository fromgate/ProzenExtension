package common

import kotlinx.browser.window
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import org.w3c.dom.HTMLElement
import kotlin.js.json


// Extension for Json Elements
fun JsonObject.obj(key: String): JsonObject? = this[key]?.jsonObject
fun JsonObject.arr(key: String): JsonArray? = this[key]?.jsonArray
fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
fun JsonObject.time(key: String): Instant? = this[key]?.jsonPrimitive?.longOrNull?.let { Instant.fromEpochSeconds(it) }
fun JsonObject.float(key: String): Float? = this[key]?.jsonPrimitive?.floatOrNull
fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
fun JsonArray.obj(index: Int): JsonObject? = if (this.isEmpty() && index >= this.size) null else this[index].jsonObject
fun JsonElement.asString(): String? = this.jsonPrimitive.contentOrNull


// Number format
fun Double.format(digits: Int = 2): String {
    val param = json ("minimumFractionDigits" to digits, "maximumFractionDigits" to digits)
    return this.asDynamic().toLocaleString("ru-RU", param) as String
}

fun Int.format(): String {
    val param = js ("{minimumFractionDigits: 0, maximumFractionDigits: 0}")
    return this.asDynamic().toLocaleString("ru-RU", param) as String
}

// Date Time
fun Instant.toYYYYMMDD(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = this.toLocalDateTime(timeZone)
    return "${time.year}-${time.monthNumber}-${time.dayOfMonth}"
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