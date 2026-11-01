package devtools

/**
 * Реальный запрос к API, разобранный на сегменты пути и query.
 *
 * Повторяющиеся параметры записываются как множество значений: порядок значений
 * и дубли не важны. Значения декодируются из percent-encoding, `+` — пробел.
 * Схема и хост отбрасываются.
 */
internal class ActualRequest(
    val method: String,
    val segments: List<String>,
    val params: Map<String, Set<String>>,
) {
    /** Канонический вид запроса: `GET путь?имя=значение` (параметры по алфавиту). */
    fun canonical(pragma: Set<String> = emptySet()): String {
        val query = params.entries
            .filter { it.key !in pragma }
            .sortedBy { it.key }
            .flatMap { (name, values) -> values.sorted().map { encodeQueryValue(name) + "=" + encodeQueryValue(it) } }
            .joinToString("&")
        val path = segments.joinToString("/")
        return if (query.isEmpty()) "$method $path" else "$method $path?$query"
    }

    companion object {
        /** Разбирает полный URL запроса. */
        fun parse(method: String, rawUrl: String): ActualRequest {
            val url = org.w3c.dom.url.URL(rawUrl)
            val pathname = url.pathname.removePrefix("/")
            val segments = if (pathname.isEmpty()) emptyList() else pathname.split('/').map { decodePercent(it, plusAsSpace = false) }
            return ActualRequest(method.uppercase(), segments, parseQuery(url.search))
        }

        /** Разбирает канонический запрос (`GET editor-api/…?fields=…`) из записи расхождения. */
        fun parseCanonical(text: String): ActualRequest {
            val space = text.indexOf(' ')
            if (space <= 0) return ActualRequest(text.uppercase(), emptyList(), emptyMap())
            val method = text.substring(0, space).uppercase()
            val rest = text.substring(space + 1)
            val question = rest.indexOf('?')
            val pathPart = if (question >= 0) rest.substring(0, question) else rest
            val queryPart = if (question >= 0) rest.substring(question + 1) else ""
            val segments = if (pathPart.isEmpty()) emptyList() else pathPart.split('/').map { decodePercent(it, plusAsSpace = false) }
            return ActualRequest(method, segments, parseQuery(queryPart))
        }
    }
}

internal fun parseQuery(rawQuery: String): Map<String, Set<String>> {
    val text = rawQuery.removePrefix("?")
    if (text.isEmpty()) return emptyMap()
    val params = mutableMapOf<String, MutableSet<String>>()
    text.split('&').forEach { part ->
        if (part.isEmpty()) return@forEach
        val equals = part.indexOf('=')
        val name = if (equals >= 0) decodePercent(part.substring(0, equals), plusAsSpace = true) else decodePercent(part, plusAsSpace = true)
        val value = if (equals >= 0) decodePercent(part.substring(equals + 1), plusAsSpace = true) else ""
        params.getOrPut(name) { mutableSetOf() }.add(value)
    }
    return params
}

internal fun decodePercent(raw: String, plusAsSpace: Boolean): String {
    val prepared = if (plusAsSpace) raw.replace("+", " ") else raw
    return try {
        js("decodeURIComponent")(prepared) as String
    } catch (error: Throwable) {
        prepared
    }
}

/** Кодирует значение для канонической строки, чтобы она оставалась валидным URL-ом. */
internal fun encodeQueryValue(value: String): String {
    val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    return buildString {
        value.encodeToByteArray().forEach { byte ->
            val char = byte.toInt().toChar()
            if (char in allowed) {
                append(char)
            } else {
                append('%').append(((byte.toInt() and 0xFF)).toString(16).padStart(2, '0'))
            }
        }
    }
}
