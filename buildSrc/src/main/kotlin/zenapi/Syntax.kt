package zenapi

/** Ошибка разбора одной строки эталона. Контекст (раздел, путь) добавляет компилятор. */
class SyntaxException(message: String) : RuntimeException(message)

/** Разбор путей в `used`/`ignored`. */
object PathSyntax {

    fun parse(raw: String): ReferencePath {
        if (raw.isEmpty()) throw SyntaxException("путь пустой")

        val rawSegments = splitSegments(raw)
        val segments = mutableListOf<PathSegment>()

        rawSegments.forEachIndexed { index, segment ->
            when {
                segment.isEmpty() -> throw SyntaxException("пустой сегмент пути в \"$raw\"")

                segment == "**" -> {
                    if (index != rawSegments.lastIndex) {
                        throw SyntaxException("\"**\" допустим только последним сегментом")
                    }
                    segments += PathSegment.Deep
                }

                segment == "*" -> segments += PathSegment.AnyKey

                segment == "[]" -> {
                    if (index != 0) {
                        throw SyntaxException("\"[]\" отдельным сегментом допустим только в начале пути (для корня-массива)")
                    }
                    segments += PathSegment.ArrayItem
                }

                segment.endsWith("[]") -> {
                    val name = segment.dropLast(2)
                    checkKey(name, raw)
                    segments += PathSegment.Key(name)
                    segments += PathSegment.ArrayItem
                }

                segment.contains('[') || segment.contains(']') -> {
                    throw SyntaxException("неверное использование \"[]\" в сегменте \"$segment\"")
                }

                segment.contains('*') -> {
                    throw SyntaxException("\"*\" должен быть отдельным сегментом, а не частью имени: \"$segment\"")
                }

                else -> {
                    checkKey(segment, raw)
                    segments += PathSegment.Key(segment)
                }
            }
        }

        if (segments.isEmpty()) throw SyntaxException("путь пустой")
        if (segments.last() == PathSegment.ArrayItem) {
            throw SyntaxException("\"[]\" не может быть последним сегментом: элементы массива сливаются в один путь")
        }

        return ReferencePath(segments)
    }

    /** Проверяет, что `**` встречается только в `ignored` (для `used` вызывается отдельно). */
    fun deepSegments(path: ReferencePath): List<Int> =
        path.segments.mapIndexedNotNull { index, segment ->
            if (segment == PathSegment.Deep) index else null
        }

    private fun checkKey(name: String, raw: String) {
        if (name.isEmpty()) throw SyntaxException("пустое имя ключа в \"$raw\"")
        if (name.any { it.isWhitespace() }) throw SyntaxException("пробелы в имени ключа \"$name\"")
    }

    /**
     * Режет путь по точкам, учитывая `\.` как точку внутри имени ключа.
     */
    private fun splitSegments(raw: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < raw.length) {
            when (val char = raw[index]) {
                '\\' -> {
                    if (index + 1 >= raw.length) {
                        throw SyntaxException("незавершённое экранирование в конце пути")
                    }
                    val next = raw[index + 1]
                    if (next != '.') {
                        throw SyntaxException("неизвестное экранирование \"\\$next\": допустимо только \"\\.\"")
                    }
                    current.append('.')
                    index += 2
                }

                '.' -> {
                    result += current.toString()
                    current.clear()
                    index++
                }

                else -> {
                    current.append(char)
                    index++
                }
            }
        }
        result += current.toString()
        return result
    }
}

/** Разбор типов в `used`: `number`, `number?`, `number|null`. */
object TypeSyntax {

    fun parse(raw: String): TypeSpec {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw SyntaxException("тип пустой")

        var body = trimmed
        val optional = body.endsWith("?")
        if (optional) {
            body = body.dropLast(1)
            if (body.isEmpty()) throw SyntaxException("тип состоит только из \"?\"")
        }
        if (body.contains('?')) throw SyntaxException("\"?\" допустим только в конце: \"$trimmed\"")

        val parts = body.split('|').map { it.trim() }
        if (parts.any { it.isEmpty() }) throw SyntaxException("пустой тип в \"$trimmed\"")

        val types = linkedSetOf<JsonType>()
        parts.forEach { part ->
            val type = JsonType.byWire(part)
                ?: throw SyntaxException("неизвестный тип \"$part\" (допустимы: ${JsonType.values().joinToString(", ") { it.wire }})")
            if (!types.add(type)) throw SyntaxException("тип \"$part\" повторяется")
        }

        if (JsonType.ANY in types && types.size > 1) {
            throw SyntaxException("\"any\" нельзя объединять с другими типами")
        }

        return TypeSpec(types, optional)
    }
}

/** Разбор `match`: `METHOD путь?параметр=значение`. */
object MatchSyntax {

    private val VAR_SEGMENT = Regex("""^\{([A-Za-z_][A-Za-z0-9_]*)\}$""")

    fun parse(raw: String): EndpointMatch {
        val trimmed = raw.trim()
        val firstSpace = trimmed.indexOfFirst { it.isWhitespace() }
        if (firstSpace <= 0) {
            throw SyntaxException("ожидается \"METHOD путь?query\", получено \"$raw\"")
        }

        val method = trimmed.substring(0, firstSpace)
        if (!Regex("[A-Z]+").matches(method)) {
            throw SyntaxException("метод \"$method\" должен быть записан заглавными буквами")
        }

        val rest = trimmed.substring(firstSpace).trim()
        if (rest.isEmpty()) throw SyntaxException("после метода нет пути")

        val queryStart = rest.indexOf('?')
        val pathPart = if (queryStart >= 0) rest.substring(0, queryStart) else rest
        val queryPart = if (queryStart >= 0) rest.substring(queryStart + 1) else null

        val segments = parsePath(pathPart)
        val params = if (queryPart.isNullOrEmpty()) emptyList() else parseQuery(queryPart)

        return EndpointMatch(method = method, segments = segments, params = params)
    }

    private fun parsePath(raw: String): List<MatchSegment> {
        val path = raw.removePrefix("/")
        if (path.isEmpty()) throw SyntaxException("путь пустой")
        if (path.startsWith("/")) throw SyntaxException("пустой сегмент пути: \"$raw\"")
        if (path.endsWith("/")) throw SyntaxException("путь не должен заканчиваться на \"/\"")

        return path.split('/').map { segment ->
            if (segment.isEmpty()) throw SyntaxException("пустой сегмент пути: \"$raw\"")
            val variable = VAR_SEGMENT.matchEntire(segment)
            if (variable != null) {
                MatchSegment.Variable(variable.groupValues[1])
            } else {
                if (segment.contains('{') || segment.contains('}')) {
                    throw SyntaxException("неверная переменная пути \"$segment\"")
                }
                if (segment.any { it.isWhitespace() } || segment.any { it in "?#" }) {
                    throw SyntaxException("недопустимый символ в сегменте пути \"$segment\"")
                }
                MatchSegment.Literal(segment)
            }
        }
    }

    private fun parseQuery(raw: String): List<MatchParam> {
        val grouped = linkedMapOf<String, MutableList<MatchParamValue>>()

        raw.split('&').forEach { part ->
            if (part.isEmpty()) throw SyntaxException("пустая пара в query")
            val equals = part.indexOf('=')
            if (equals <= 0) throw SyntaxException("параметр \"$part\" должен быть в форме имя=значение")

            val name = decodePercent(part.substring(0, equals), plusAsSpace = true)
            if (name.isEmpty()) throw SyntaxException("пустое имя параметра")
            if (name.any { it in "{}" }) throw SyntaxException("недопустимое имя параметра \"$name\"")

            val rawValue = part.substring(equals + 1)
            val variable = VAR_SEGMENT.matchEntire(rawValue)
            val value = if (variable != null) {
                MatchParamValue.Any(variable.groupValues[1])
            } else {
                MatchParamValue.Literal(decodePercent(rawValue, plusAsSpace = true))
            }
            grouped.getOrPut(name) { mutableListOf() }.add(value)
        }

        return grouped
            .toSortedMap()
            .map { (name, values) -> MatchParam(name, sortValues(values.distinct())) }
    }

    private fun sortValues(values: List<MatchParamValue>): List<MatchParamValue> = values.sortedWith(
        compareBy(
            { if (it is MatchParamValue.Literal) 0 else 1 },
            { when (it) { is MatchParamValue.Literal -> it.value; is MatchParamValue.Any -> it.name } },
        )
    )

    /**
     * Декодирует percent-encoding. В query `+` — это пробел (семантика URLSearchParams),
     * в пути — обычный плюс.
     */
    fun decodePercent(raw: String, plusAsSpace: Boolean): String {
        val bytes = java.io.ByteArrayOutputStream(raw.length)
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            when {
                char == '%' -> {
                    if (index + 2 >= raw.length) throw SyntaxException("незавершённый %-код в \"$raw\"")
                    val hex = raw.substring(index + 1, index + 3)
                    val byte = hex.toIntOrNull(16)
                        ?: throw SyntaxException("неверный %-код \"%$hex\" в \"$raw\"")
                    bytes.write(byte)
                    index += 3
                }

                char == '+' && plusAsSpace -> {
                    bytes.write(' '.code)
                    index++
                }

                else -> {
                    val text = raw.substring(index, index + 1)
                    bytes.write(text.toByteArray(Charsets.UTF_8))
                    index++
                }
            }
        }
        val text = String(bytes.toByteArray(), Charsets.UTF_8)
        if (text.contains('\uFFFD')) throw SyntaxException("неверная UTF-8 последовательность в \"$raw\"")
        return text
    }

    /** Кодирует значение для канонической строки, чтобы она оставалась валидным URL-ом. */
    fun encodeQueryValue(value: String): String {
        val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        return buildString {
            value.toByteArray(Charsets.UTF_8).forEach { byte ->
                val char = byte.toInt().toChar()
                if (char in allowed) {
                    append(char)
                } else {
                    append('%').append("%02X".format(byte.toInt() and 0xFF))
                }
            }
        }
    }
}
