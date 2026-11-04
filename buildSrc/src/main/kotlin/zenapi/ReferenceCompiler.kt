package zenapi

import org.tomlj.Toml
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable
import java.security.MessageDigest

class Compilation(
    val document: ReferenceDocument,
    /** Готовый JSON для встраивания в бандл. */
    val json: String,
    /** `sha256:...` от JSON с пустым полем `hash`. */
    val hash: String,
    val warnings: List<String>,
    /** Исходный текст эталона: dev-сборка копирует его в буфер с дописанными `unknown`. */
    val source: String,
)

/**
 * Читает `reference/zen-api.toml`, проверяет его и собирает компактный JSON.
 *
 * Все ошибки собираются разом: чинить файл по одной строке за сборку неудобно.
 */
object ReferenceCompiler {

    private val ENDPOINT_NAME = Regex("[A-Za-z0-9_-]+")
    private val META_KEYS = setOf("format", "zenVersion", "updated", "volatileParams")
    private val ENDPOINT_KEYS = setOf("match", "note", "used", "ignored", "unknown", "monitor")

    /** Лимиты совпадают с константами в devtools (PathValidator/MonitorStore): модули компилируются независимо. */
    private const val MONITOR_VALUE_COUNT_LIMIT = 64
    private const val MONITOR_VALUE_LENGTH_LIMIT = 100

    fun compile(toml: String): Compilation {
        val root = Toml.parse(toml)
        if (root.hasErrors()) {
            throw ReferenceException(
                root.errors().map { error ->
                    val position = error.position()
                    "${position.line()}:${position.column()}: ${error.message}"
                }
            )
        }

        val collector = Collector()
        val meta = readMeta(root, collector)
        val endpoints = readEndpoints(root, collector)

        checkCrossEndpoint(endpoints, collector)
        checkVolatileParams(meta, endpoints, collector)

        if (collector.errors.isNotEmpty()) throw ReferenceException(collector.errors)

        val document = ReferenceDocument(meta!!, endpoints)
        val jsonWithoutHash = ReferenceJson.write(document, hash = "")
        val hash = "sha256:" + sha256(jsonWithoutHash)
        val json = ReferenceJson.write(document, hash = hash)

        return Compilation(document, json, hash, collector.warnings, toml)
    }

    private fun readMeta(root: TomlParseResult, collector: Collector): Meta? {
        val table = root.getTable("meta")
        if (table == null) {
            collector.error("meta", "отсутствует раздел [meta]")
            return null
        }

        val unknown = table.keySet() - META_KEYS
        unknown.forEach { collector.error("meta", "неизвестный ключ \"$it\"") }

        val format = (table.get("format") as? Long)?.toInt()
        if (format == null) {
            collector.error("meta.format", "обязательное целое число")
        } else if (format != 1) {
            collector.error("meta.format", "поддерживается только формат 1, получен $format")
        }

        val zenVersion = table.get("zenVersion") as? String
        if (zenVersion.isNullOrBlank()) {
            collector.error("meta.zenVersion", "обязательная непустая строка")
        }

        val updated = when (val value = table.get("updated")) {
            is java.time.LocalDate -> value.toString()
            is String -> value
            null -> null
            else -> null
        }
        if (updated == null) {
            collector.error("meta.updated", "обязательная дата в формате YYYY-MM-DD")
        } else if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(updated)) {
            collector.error("meta.updated", "дата должна быть в формате YYYY-MM-DD, получено \"$updated\"")
        }

        val volatileParams = mutableListOf<String>()
        val array = table.getArray("volatileParams")
        if (array == null) {
            collector.error("meta.volatileParams", "обязательный массив имён параметров")
        } else {
            for (index in 0 until array.size()) {
                val value = array.get(index)
                if (value !is String) {
                    collector.error("meta.volatileParams[$index]", "ожидается строка")
                    continue
                }
                if (value.isBlank()) {
                    collector.error("meta.volatileParams[$index]", "пустое имя параметра")
                    continue
                }
                if (value in volatileParams) {
                    collector.error("meta.volatileParams[$index]", "параметр \"$value\" повторяется")
                    continue
                }
                volatileParams += value
            }
        }

        if (format != 1 || zenVersion.isNullOrBlank() || updated == null) return null

        return Meta(format, zenVersion, updated, volatileParams)
    }

    private fun readEndpoints(root: TomlParseResult, collector: Collector): List<EndpointDefinition> {
        val table = root.getTable("endpoints")
        if (table == null) {
            collector.error("endpoints", "нет ни одного раздела [endpoints.<имя>]")
            return emptyList()
        }

        return table.entrySet().mapNotNull { entry ->
            readEndpoint(entry.key, entry.value as? TomlTable, collector)
        }
    }

    private fun readEndpoint(
        name: String,
        table: TomlTable?,
        collector: Collector,
    ): EndpointDefinition? {
        val context = "endpoints.$name"
        if (!ENDPOINT_NAME.matches(name)) {
            collector.error(context, "имя должно состоять из A-Za-z0-9_-")
        }
        if (table == null) {
            collector.error(context, "ожидается таблица")
            return null
        }

        val unknownKeys = table.keySet() - ENDPOINT_KEYS
        unknownKeys.forEach { collector.error(context, "неизвестный ключ \"$it\"") }

        val matchRaw = table.getString("match")
        val match = if (matchRaw == null) {
            collector.error("$context.match", "обязательная строка")
            null
        } else {
            try {
                MatchSyntax.parse(matchRaw)
            } catch (error: SyntaxException) {
                collector.error("$context.match", error.message ?: "неверный match")
                null
            }
        }

        val note = when (val value = table.get("note")) {
            null -> null
            is String -> value
            else -> {
                collector.error("$context.note", "ожидается строка")
                null
            }
        }

        val used = readUsed(name, table.getTable("used"), collector)
        val ignored = readIgnored(name, table.getTable("ignored"), collector)
        val unknown = readUnknown(name, table.getTable("unknown"), collector)
        val monitor = readMonitor(name, table.getTable("monitor"), collector)
        val monitorValues = readMonitorValues(name, table.getTable("monitor"), monitor, collector)

        if (match == null) return null
        return EndpointDefinition(name, note, match, used, ignored, unknown, monitor, monitorValues)
    }

    private fun readUsed(
        endpoint: String,
        table: TomlTable?,
        collector: Collector,
    ): List<UsedPath> {
        if (table == null) return emptyList()

        return table.entrySet().mapNotNull { entry ->
            val rawPath = entry.key
            val context = "endpoints.$endpoint.used[\"$rawPath\"]"
            val path = try {
                PathSyntax.parse(rawPath)
            } catch (error: SyntaxException) {
                collector.error(context, error.message ?: "неверный путь")
                return@mapNotNull null
            }

            if (PathSyntax.deepSegments(path).isNotEmpty()) {
                collector.error(context, "\"**\" допустим только в ignored")
                return@mapNotNull null
            }

            val type = readTypeSpec(context, entry.value, collector) ?: return@mapNotNull null
            UsedPath(path, type)
        }
    }

    /**
     * Общий разбор значения `used`/`unknown`: строка с типом или расширенная
     * запись `{ type = "..." }`. Ошибки добавляются в коллектор, при ошибке — null.
     */
    private fun readTypeSpec(context: String, value: Any?, collector: Collector): TypeSpec? {
        return when (value) {
            is String -> try {
                TypeSyntax.parse(value)
            } catch (error: SyntaxException) {
                collector.error(context, error.message ?: "неверный тип")
                null
            }

            is TomlTable -> {
                if ("by" in value.keySet()) {
                    collector.error(context, "поле \"by\" больше не поддерживается: кто вызывает эндпоинт, видно по caller")
                }
                (value.keySet() - setOf("type")).filter { it != "by" }.forEach {
                    collector.error(context, "неизвестный ключ \"$it\" в расширенной записи")
                }
                val typeString = value.getString("type")
                if (typeString == null) {
                    collector.error(context, "в расширенной записи обязателен \"type\"")
                    null
                } else {
                    try {
                        TypeSyntax.parse(typeString)
                    } catch (error: SyntaxException) {
                        collector.error(context, error.message ?: "неверный тип")
                        null
                    }
                }
            }

            else -> {
                collector.error(context, "ожидается тип строкой или таблицей { type = \"...\" }")
                null
            }
        }
    }

    private fun readIgnored(
        endpoint: String,
        table: TomlTable?,
        collector: Collector,
    ): List<IgnoredPath> {
        if (table == null) return emptyList()

        return table.entrySet().mapNotNull { entry ->
            val rawPath = entry.key
            val context = "endpoints.$endpoint.ignored[\"$rawPath\"]"
            val path = try {
                PathSyntax.parse(rawPath)
            } catch (error: SyntaxException) {
                collector.error(context, error.message ?: "неверный путь")
                return@mapNotNull null
            }

            val reason = when (val value = entry.value) {
                is String -> value.trim()
                else -> {
                    collector.error(context, "ожидается непустая причина строкой")
                    return@mapNotNull null
                }
            }
            if (reason.isEmpty()) {
                collector.error(context, "пустая причина")
                return@mapNotNull null
            }

            IgnoredPath(path, reason)
        }
    }

    /**
     * `unknown` — склад наблюдённых, но ещё не разобранных путей. Пути только
     * точные: `*` и `**` здесь недопустимы, иначе запись неоднозначна.
     */
    private fun readUnknown(
        endpoint: String,
        table: TomlTable?,
        collector: Collector,
    ): List<UnknownPath> {
        if (table == null) return emptyList()

        return table.entrySet().mapNotNull { entry ->
            val rawPath = entry.key
            val context = "endpoints.$endpoint.unknown[\"$rawPath\"]"
            val path = try {
                PathSyntax.parse(rawPath)
            } catch (error: SyntaxException) {
                collector.error(context, error.message ?: "неверный путь")
                return@mapNotNull null
            }

            if (path.segments.any { it == PathSegment.AnyKey || it == PathSegment.Deep }) {
                collector.error(context, "\"*\" и \"**\" недопустимы в unknown: путь должен быть точным")
                return@mapNotNull null
            }

            val type = readTypeSpec(context, entry.value, collector) ?: return@mapNotNull null
            UnknownPath(path, type)
        }
    }

    /**
     * `monitor` — пути, чьи значения валидатор собирает в runtime. Тип не
     * ограничиваем скалярами: объекты и массивы объектов не падают здесь, а
     * получают служебный маркер в результаты мониторинга (см. PathValidator).
     */
    private fun readMonitor(
        endpoint: String,
        table: TomlTable?,
        collector: Collector,
    ): List<MonitorPath> {
        if (table == null) return emptyList()

        return table.entrySet().mapNotNull { entry ->
            // Вложенная таблица `[...monitor.values]` — не путь, а склад найденных значений.
            if (entry.key == "values") return@mapNotNull null
            val rawPath = entry.key
            val context = "endpoints.$endpoint.monitor[\"$rawPath\"]"
            val path = try {
                PathSyntax.parse(rawPath)
            } catch (error: SyntaxException) {
                collector.error(context, error.message ?: "неверный путь")
                return@mapNotNull null
            }

            if (PathSyntax.deepSegments(path).isNotEmpty()) {
                collector.error(context, "\"**\" допустим только в ignored")
                return@mapNotNull null
            }

            val type = readTypeSpec(context, entry.value, collector) ?: return@mapNotNull null
            MonitorPath(path, type)
        }
    }

    /**
     * `monitor.values` — найденные значения: пополняется копированием схемы,
     * можно править руками. Пути конкретные (как в `unknown`: без `*`/`**`),
     * каждый путь обязан быть объявлен в `monitor`.
     */
    private fun readMonitorValues(
        endpoint: String,
        monitorTable: TomlTable?,
        monitor: List<MonitorPath>,
        collector: Collector,
    ): List<MonitorValues> {
        val table = monitorTable?.getTable("values") ?: return emptyList()
        val monitorPaths = monitor.map { it.path.canonical }.toSet()

        return table.entrySet().mapNotNull { entry ->
            val rawPath = entry.key
            val context = "endpoints.$endpoint.monitor.values[\"$rawPath\"]"
            val path = try {
                PathSyntax.parse(rawPath)
            } catch (error: SyntaxException) {
                collector.error(context, error.message ?: "неверный путь")
                return@mapNotNull null
            }

            if (path.segments.any { it == PathSegment.AnyKey || it == PathSegment.Deep }) {
                collector.error(context, "\"*\" и \"**\" недопустимы: путь должен быть точным")
                return@mapNotNull null
            }

            if (path.canonical !in monitorPaths) {
                collector.error(context, "путь \"$rawPath\" не объявлен в monitor")
                return@mapNotNull null
            }

            val array = entry.value as? org.tomlj.TomlArray
            if (array == null) {
                collector.error(context, "ожидается массив строк")
                return@mapNotNull null
            }

            val values = mutableListOf<String>()
            for (index in 0 until array.size()) {
                val value = array.get(index)
                if (value !is String) {
                    collector.error(context, "значение [$index] должно быть строкой")
                    continue
                }
                if (value.isEmpty()) {
                    collector.error(context, "пустое значение [$index]")
                    continue
                }
                if (value in values) {
                    collector.error(context, "значение \"$value\" повторяется")
                    continue
                }
                values += value
            }

            if (values.size > MONITOR_VALUE_COUNT_LIMIT) {
                collector.warnings +=
                    "$context: значений ${values.size}, больше лимита runtime-накопления ($MONITOR_VALUE_COUNT_LIMIT)"
            }
            values.firstOrNull { it.length > MONITOR_VALUE_LENGTH_LIMIT }?.let { long ->
                collector.warnings += "$context: значение длиннее $MONITOR_VALUE_LENGTH_LIMIT символов: \"${long.take(40)}…\""
            }

            MonitorValues(path, values)
        }
    }

    private fun checkCrossEndpoint(endpoints: List<EndpointDefinition>, collector: Collector) {
        endpoints
            .groupBy { it.match.canonical }
            .filterValues { it.size > 1 }
            .forEach { (canonical, duplicates) ->
                collector.error(
                    "endpoints",
                    "одинаковый match у ${duplicates.joinToString(", ") { it.name }}: $canonical",
                )
            }

        for (first in endpoints.indices) {
            for (second in first + 1 until endpoints.size) {
                val a = endpoints[first]
                val b = endpoints[second]
                if (a.match.canonical == b.match.canonical) continue
                if (potentiallyAmbiguous(a.match, b.match)) {
                    collector.error(
                        "endpoints",
                        "match ${a.name} и ${b.name} могут совпасть с одним запросом при одинаковой специфичности: " +
                            "${a.match.canonical} ↔ ${b.match.canonical}",
                    )
                }
            }
        }

        endpoints.forEach { endpoint ->
            val used = endpoint.used.associateBy { it.path.canonical }
            endpoint.ignored.forEach { ignored ->
                val usedPath = used[ignored.path.canonical]
                if (usedPath != null && usedPath.path.specificity == ignored.path.specificity) {
                    collector.error(
                        "endpoints.${endpoint.name}",
                        "путь \"${ignored.path.canonical}\" одновременно в used и ignored",
                    )
                }
            }

            val ignoredByPath = endpoint.ignored.associateBy { it.path.canonical }
            endpoint.unknown.forEach { unknown ->
                if (used.containsKey(unknown.path.canonical)) {
                    collector.error(
                        "endpoints.${endpoint.name}",
                        "путь \"${unknown.path.canonical}\" одновременно в used и unknown",
                    )
                }
                if (ignoredByPath.containsKey(unknown.path.canonical)) {
                    collector.error(
                        "endpoints.${endpoint.name}",
                        "путь \"${unknown.path.canonical}\" одновременно в ignored и unknown",
                    )
                }
                endpoint.ignored
                    .firstOrNull {
                        it.path.canonical != unknown.path.canonical && ignoredCovers(it.path, unknown.path)
                    }
                    ?.let { covering ->
                        collector.warnings +=
                            "endpoints.${endpoint.name}.unknown: путь \"${unknown.path.canonical}\" " +
                            "уже покрыт ignored \"${covering.path.canonical}\""
                    }
            }
        }
    }

    /** `ignored`-правило покрывает точный путь `unknown` (возможно, через wildcard). */
    private fun ignoredCovers(pattern: ReferencePath, actual: ReferencePath): Boolean {
        val segments = pattern.segments
        if (segments.isEmpty()) return false
        val length = if (segments.last() == PathSegment.Deep) segments.size - 1 else segments.size
        if (actual.segments.size < length) return false
        return (0 until length).all { index -> segmentMatches(segments[index], actual.segments[index]) }
    }

    private fun segmentMatches(pattern: PathSegment, actual: PathSegment): Boolean = when (pattern) {
        is PathSegment.Key -> actual is PathSegment.Key && actual.name == pattern.name
        PathSegment.AnyKey -> actual is PathSegment.Key
        PathSegment.ArrayItem -> actual === PathSegment.ArrayItem
        PathSegment.Deep -> true
    }

    private fun checkVolatileParams(
        meta: Meta?,
        endpoints: List<EndpointDefinition>,
        collector: Collector,
    ) {
        if (meta == null) return
        endpoints.forEach { endpoint ->
            endpoint.match.params.forEach { param ->
                if (param.name in meta.volatileParams) {
                    collector.warnings +=
                        "endpoints.${endpoint.name}.match: параметр \"${param.name}\" указан и в volatileParams, и в match"
                }
            }
        }
    }

    /**
     * Грубая проверка на неоднозначность, доступная без реальных запросов.
     *
     * Два match считаются потенциально неоднозначными, если методы совпадают, пути
     * совместимы по сегментам, литеральные параметры не противоречат друг другу
     * и специфичность равна. Параметр, у которого в одном match литерал, а в другом
     * другой литерал, считается различающим варианты (типичный случай — `fields`).
     */
    private fun potentiallyAmbiguous(a: EndpointMatch, b: EndpointMatch): Boolean {
        if (a.method != b.method) return false
        if (a.segments.size != b.segments.size) return false

        for (index in a.segments.indices) {
            val left = a.segments[index]
            val right = b.segments[index]
            if (left is MatchSegment.Literal && right is MatchSegment.Literal && left.value != right.value) {
                return false
            }
        }

        val common = a.params.map { it.name }.toSet() intersect b.params.map { it.name }.toSet()
        for (name in common) {
            val left = a.literalValues(name)
            val right = b.literalValues(name)
            if (left != null && right != null && left != right) return false
        }

        return a.specificity == b.specificity
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private class Collector {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        fun error(context: String, message: String) {
            errors += "$context: $message"
        }
    }
}
