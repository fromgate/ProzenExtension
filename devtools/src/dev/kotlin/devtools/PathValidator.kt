package devtools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Результат обхода одного ответа по эталону эндпоинта. */
internal class ValidationOutcome(
    val endpoint: Endpoint,
    /** Отчёты проверки формы: `new`, `missing`, `type`, `unverified`. */
    val pending: List<PendingDiff>,
    /** Что увидели в ответе: реальный путь -> множество типов (для снятия прошлых отчётов). */
    val observedTypes: Map<List<PathStep>, Set<String>>,
    /** Вызвал запрос. */
    val caller: String?,
)

/**
 * Что увидели в ответе. Нужен, чтобы снимать отработавшие записи: `missing`
 * и `unverified`, чей путь появился, `type`, чей тип теперь допустим.
 */
internal class ResponseSnapshot(
    private val endpoint: Endpoint,
    private val types: Map<List<PathStep>, Set<String>>,
) {
    fun typeOf(pathText: String): Set<String>? =
        types[parsePath(pathText)]

    /** Путь присутствует в ответе (правило может использовать wildcards). */
    fun containsPath(pathText: String): Boolean {
        val pattern = parsePath(pathText)
        return types.keys.any { usedMatches(pattern, it) }
    }

    /** Типы по пути теперь укладываются в требование `used`. */
    fun typeAllowed(pathText: String): Boolean {
        val steps = parsePath(pathText)
        if (steps !in types) return false
        val rule = endpoint.bestUsedExact(steps) ?: return true
        if (rule.types.contains("any")) return true
        return types[steps]!!.all { it in rule.types }
    }

    /** Запись снята этим ответом: её больше не нужно показывать. */
    fun resolved(record: DiffRecord): Boolean = when (record.kind) {
        DiffKind.MISSING, DiffKind.UNVERIFIED -> containsPath(record.path)
        DiffKind.TYPE -> typeAllowed(record.path)
        else -> false
    }
}


/** Лимит фрагмента значения, который хранится в расхождении. */
internal const val diffSampleLimit: Int = 200

internal class PathValidator(private val endpoint: Endpoint) {

    private val observed = mutableMapOf<List<PathStep>, MutableSet<String>>()
    private val matchedRules = mutableSetOf<Int>()
    private val ruleByPath = mutableMapOf<List<PathStep>, Endpoint.UsedRule>()
    private val samples = mutableMapOf<List<PathStep>, JsonElement>()
    private val newNodes = mutableMapOf<List<PathStep>, JsonElement>()
    private val blockedArrays = mutableListOf<Pair<List<PathStep>, String>>()

    fun validate(root: JsonElement, caller: String?): ValidationOutcome {
        walk(root, emptyList())
        return ValidationOutcome(endpoint, collectDiffs(), observedSnapshot(), caller)
    }

    private fun observedSnapshot(): Map<List<PathStep>, Set<String>> =
        observed.mapValues { (_, types) -> types.toSet() }

    // ------------------------------------------------------------------ обход

    private fun walk(node: JsonElement, path: List<PathStep>) {
        val usedExact = endpoint.bestUsedExact(path)
        val ignored = endpoint.bestIgnored(path)
        val below = endpoint.bestUsedAtOrBelow(path)

        // `ignored` перекрывает узел целиком. Исключение — если под узлом есть
        // правило `used` более специфичное (тогда спускаемся дальше).
        if (ignored != null && (below == null || ignored.rank >= below.rank)) {
            return
        }

        if (usedExact != null) {
            // Отмечаем все совпавшие правила: точное совпадение перекрывает wildcard-правило.
            endpoint.allUsedExact(path).forEach { rule -> matchedRules.add(rule.index) }
            ruleByPath[path] = usedExact
        } else if (below == null) {
            // Узел неизвестен и правил ниже нет: один `new` на всё поддерево.
            newNodes[path] = node
            return
        }
        // Иначе узел — контейнер внутри пути `used`: идём глубже.

        observed.getOrPut(path) { mutableSetOf() }.add(jsonTypeOf(node))
        if (!samples.containsKey(path)) samples[path] = node

        when (node) {
            is JsonObject -> node.forEach { (key, value) -> walk(value, path + PathStep.Key(key)) }
            is JsonArray -> {
                if (node.isEmpty()) {
                    blockedArrays.add(path to "пустой массив: содержимое не проверено")
                    return
                }
                sampleIndices(node.size).forEach { index -> walk(node[index], path + PathStep.ArrayItem) }
                if (node.size > ARRAY_SAMPLE_SIZE) {
                    blockedArrays.add(
                        path to "массив обрезан (${node.size} элементов, проверено $ARRAY_SAMPLE_SIZE)"
                    )
                }
            }

            else -> {}
        }
    }

    private fun sampleIndices(size: Int): List<Int> {
        if (size <= ARRAY_SAMPLE_SIZE) return (0 until size).toList()
        val head = ARRAY_SAMPLE_SIZE / 2
        val tail = ARRAY_SAMPLE_SIZE - head
        return (0 until head).toList() + (size - tail until size).toList()
    }

    // ---------------------------------------------------------------- отчёты

    private fun collectDiffs(): List<PendingDiff> {
        val diffs = mutableListOf<PendingDiff>()

        newNodes.forEach { (path, node) ->
            diffs += PendingDiff(
                endpoint = endpoint.name,
                kind = DiffKind.NEW,
                path = formatPath(path),
                observed = jsonTypeOf(node),
                sample = sampleValue(node),
            )
        }

        // Типы: фактические типы пути сравниваются с допустимыми у правила.
        observed.forEach { (path, types) ->
            val rule = ruleByPath[path] ?: return@forEach
            if ("any" in rule.types) return@forEach
            if (types.any { it !in rule.types }) {
                diffs += PendingDiff(
                    endpoint = endpoint.name,
                    kind = DiffKind.TYPE,
                    path = formatPath(path),
                    expected = formatTypeSpec(rule),
                    observed = types.sorted().joinToString("|"),
                    sample = samples[path]?.let(::sampleValue),
                )
            }
        }

        collectMissingAndUnverified(diffs)
        return diffs
    }

    private data class Report(val rule: Endpoint.UsedRule, val kind: DiffKind, val note: String?)

    private fun collectMissingAndUnverified(diffs: MutableList<PendingDiff>) {
        val unmatched = endpoint.used.filter { it.index !in matchedRules }
        val reports = mutableListOf<Report>()

        unmatched.forEach { rule ->
            when {
                blockedReason(rule.path) != null ->
                    reports += Report(rule, DiffKind.UNVERIFIED, blockedReason(rule.path))

                endpoint.hasMatchingIgnored(rule) -> Unit

                // Необязательный родитель отсутствует: содержимое проверить нельзя.
                unmatched.firstOrNull { isAncestorPattern(it.path, rule.path) && it.optional } != null -> {
                    val parent = unmatched.first { isAncestorPattern(it.path, rule.path) && it.optional }
                    reports += Report(rule, DiffKind.UNVERIFIED, "необязательный родитель ${parent.canonical} отсутствует")
                }

                // Родитель тоже пропал: сообщаем только предка, без каскада.
                unmatched.firstOrNull { isAncestorPattern(it.path, rule.path) } != null -> Unit

                rule.optional -> Unit

                else -> reports += Report(rule, DiffKind.MISSING, null)
            }
        }

        reports.forEach { report ->
            diffs += PendingDiff(
                endpoint = endpoint.name,
                kind = report.kind,
                path = report.rule.canonical,
                expected = formatTypeSpec(report.rule),
                observed = if (report.kind == DiffKind.UNVERIFIED) "не проверено" else "отсутствует",
                note = report.note,
            )
        }
    }

    /** Массив на этом пути опустошён или обрезан, поэтому правило под ним проверить нельзя. */
    private fun blockedBy(arrayPath: List<PathStep>, rulePath: List<PathStep>): Boolean {
        if (rulePath.size <= arrayPath.size) return false
        if (rulePath[arrayPath.size] !== PathStep.ArrayItem) return false
        return arrayPath.indices.all { actualStepMatches(arrayPath[it], rulePath[it]) }
    }

    private fun blockedReason(rulePath: List<PathStep>): String? =
        blockedArrays.firstOrNull { (arrayPath, reason) -> blockedBy(arrayPath, rulePath) }?.second

    private fun sampleValue(node: JsonElement): String {
        val text = node.toString()
        return if (text.length <= diffSampleLimit) text else text.take(diffSampleLimit) + "…"
    }
}

/** Множество типов в человекочитаемой форме (`number`, `string|null?`). */
internal fun formatTypeSpec(rule: Endpoint.UsedRule): String {
    val types = if ("any" in rule.types) {
        "any"
    } else {
        rule.types.sorted().joinToString("|")
    }
    return if (rule.optional) "$types?" else types
}

/** Тип фактического JSON-узла в терминах эталона. */
internal fun jsonTypeOf(node: JsonElement): String = when (node) {
    is JsonNull -> "null"
    is JsonObject -> "object"
    is JsonArray -> "array"
    is JsonPrimitive -> when {
        node.isString -> "string"
        node.booleanOrNull != null -> "boolean"
        else -> "number"
    }
}
