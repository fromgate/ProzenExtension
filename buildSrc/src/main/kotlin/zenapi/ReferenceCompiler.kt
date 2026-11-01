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
)

/**
 * Читает `reference/zen-api.toml`, проверяет его и собирает компактный JSON.
 *
 * Все ошибки собираются разом: чинить файл по одной строке за сборку неудобно.
 */
object ReferenceCompiler {

    private val ENDPOINT_NAME = Regex("[A-Za-z0-9_-]+")
    private val META_KEYS = setOf("format", "zenVersion", "updated", "volatileParams")
    private val ENDPOINT_KEYS = setOf("match", "note", "used", "ignored")

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

        return Compilation(document, json, hash, collector.warnings)
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

        val unknown = table.keySet() - ENDPOINT_KEYS
        unknown.forEach { collector.error(context, "неизвестный ключ \"$it\"") }

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

        if (match == null) return null
        return EndpointDefinition(name, note, match, used, ignored)
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

            val type = when (val value = entry.value) {
                is String -> try {
                    TypeSyntax.parse(value)
                } catch (error: SyntaxException) {
                    collector.error(context, error.message ?: "неверный тип")
                    return@mapNotNull null
                }

                is TomlTable -> {
                    val unknown = value.keySet() - setOf("type")
                    if ("by" in value.keySet()) {
                        collector.error(context, "поле \"by\" больше не поддерживается: кто вызывает эндпоинт, видно по caller")
                    }
                    unknown.filter { it != "by" }.forEach {
                        collector.error(context, "неизвестный ключ \"$it\" в расширенной записи")
                    }
                    val typeString = value.getString("type")
                    if (typeString == null) {
                        collector.error(context, "в расширенной записи обязателен \"type\"")
                        return@mapNotNull null
                    }
                    try {
                        TypeSyntax.parse(typeString)
                    } catch (error: SyntaxException) {
                        collector.error(context, error.message ?: "неверный тип")
                        return@mapNotNull null
                    }
                }

                else -> {
                    collector.error(context, "ожидается тип строкой или таблицей { type = \"...\" }")
                    return@mapNotNull null
                }
            }

            UsedPath(path, type)
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
        }
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
