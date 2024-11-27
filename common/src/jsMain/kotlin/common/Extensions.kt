package common

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import org.w3c.dom.HTMLElement
import kotlin.js.json


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
fun JsonObject.time(key: String): Instant? = getNestedElement(key)?.jsonPrimitive?.longOrNull?.let { Instant.fromEpochSeconds(it) }
fun JsonObject.float(key: String): Float? = getNestedElement(key)?.jsonPrimitive?.floatOrNull
fun JsonObject.double(key: String): Double? = getNestedElement(key)?.jsonPrimitive?.doubleOrNull

fun JsonArray.obj(index: Int): JsonObject? = if (this.isEmpty() && index >= this.size) null else this[index].jsonObject

fun JsonElement.asString(): String? = this.jsonPrimitive.contentOrNull


// Number format
fun Double.format(digits: Int? = null): String {
    val d = digits ?: if (this < 2) 2 else 1
    val param = json ("minimumFractionDigits" to d, "maximumFractionDigits" to d)
    return this.asDynamic().toLocaleString("ru-RU", param) as String
}

fun Int.format(): String {
    val param = js ("{minimumFractionDigits: 0, maximumFractionDigits: 0}")
    return this.asDynamic().toLocaleString("ru-RU", param) as String
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