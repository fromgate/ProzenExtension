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
    /** Отчёты проверки формы: `new`, `missing`, `type`, `empty`, `unverified`, `value`. */
    val pending: List<PendingDiff>,
    /** Что увидели в ответе: реальный путь -> множество типов (для снятия прошлых отчётов). */
    val observedTypes: Map<List<PathStep>, Set<String>>,
    /** Непустые массивы: по ним снимается `empty`. */
    val nonEmptyArrays: Set<List<PathStep>>,
    /** Массивы, содержимое которых проверено или пустота которых допустима: снимают `unverified` под собой. */
    val resolvedArrays: Set<List<PathStep>>,
    /** Наблюдённые значения путей `monitor`: canonical path -> значения из этого ответа. */
    val monitorValues: Map<String, Set<String>>,
    /** Вызвал запрос. */
    val caller: String?,
)

/**
 * Что увидели в ответе. Нужен, чтобы снимать отработавшие записи: `missing`
 * и `unverified`, чей путь появился, `type`, чей тип теперь допустим,
 * `empty`, если массив стал непустым или исчез.
 */
internal class ResponseSnapshot(
    private val endpoint: Endpoint,
    private val types: Map<List<PathStep>, Set<String>>,
    private val nonEmptyArrays: Set<List<PathStep>> = emptySet(),
    private val resolvedArrays: Set<List<PathStep>> = emptySet(),
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

    /** Путь больше не под массивом, чьё содержимое осталось непроверенным. */
    private fun underResolvedArray(pathText: String): Boolean {
        val steps = parsePath(pathText)
        return resolvedArrays.any { arrayPath -> isUnderArray(arrayPath, steps) }
    }

    /** Массив стал непустым, исчез или перестал быть массивом. */
    private fun emptyResolved(pathText: String): Boolean {
        val steps = parsePath(pathText)
        val observed = types[steps] ?: return true
        if ("array" !in observed) return true
        return steps in nonEmptyArrays
    }

    /** Запись снята этим ответом: её больше не нужно показывать. */
    fun resolved(record: DiffRecord): Boolean = when (record.kind) {
        DiffKind.MISSING -> containsPath(record.path) || underResolvedArray(record.path)
        DiffKind.UNVERIFIED -> containsPath(record.path) || underResolvedArray(record.path)
        DiffKind.TYPE -> typeAllowed(record.path) ||
            (!containsPath(record.path) && underResolvedArray(record.path))
        DiffKind.EMPTY -> emptyResolved(record.path)
        else -> false
    }
}


/** Лимит фрагмента значения, который хранится в расхождении типа/статуса. */
internal const val diffSampleLimit: Int = 200

/** Лимиты мониторинга значений; синхронизированы с константами в buildSrc (ReferenceCompiler). */
internal const val monitorValueCountLimit: Int = 64
internal const val monitorValueLengthLimit: Int = 100

/** Маркеры вместо значений, когда правило `monitor` настроено на не-скаляр. */
internal const val monitorMarkerObject: String = "<object>"
internal const val monitorMarkerArray: String = "<array>"

/** Лимит образца нового узла: 16 КБ, чтобы в него поместился целый элемент массива. */
internal const val newSampleLimit: Int = 16 * 1024

/** Обрезает образец и оставляет явный след обрезки. */
internal fun limitSample(value: String, limit: Int): String =
    if (value.length <= limit) value else value.take(limit) + "…[обрезано ${value.length - limit} симв.]"

/** Путь правила лежит под массивом `arrayPath` (следующий сегмент — `[]`). */
internal fun isUnderArray(arrayPath: List<PathStep>, rulePath: List<PathStep>): Boolean {
    if (rulePath.size <= arrayPath.size) return false
    if (rulePath[arrayPath.size] !== PathStep.ArrayItem) return false
    return arrayPath.indices.all { actualStepMatches(arrayPath[it], rulePath[it]) }
}

internal class PathValidator(private val endpoint: Endpoint) {

    private val observed = mutableMapOf<List<PathStep>, MutableSet<String>>()
    private val matchedRules = mutableSetOf<Int>()
    private val ruleByPath = mutableMapOf<List<PathStep>, Endpoint.UsedRule>()
    private val samples = mutableMapOf<List<PathStep>, JsonElement>()
    private val newNodes = mutableMapOf<List<PathStep>, JsonElement>()
    private val blockedArrays = mutableListOf<Pair<List<PathStep>, String>>()
    private val emptyArrays = mutableSetOf<List<PathStep>>()
    private val settledEmptyArrays = mutableSetOf<List<PathStep>>()
    private val nonEmptyArrays = mutableSetOf<List<PathStep>>()
    private val resolvedArrays = mutableSetOf<List<PathStep>>()
    private val monitorValues = mutableMapOf<String, MutableSet<String>>()

    fun validate(root: JsonElement, caller: String?): ValidationOutcome {
        walk(root, emptyList())
        return ValidationOutcome(
            endpoint = endpoint,
            pending = collectDiffs(),
            observedTypes = observedSnapshot(),
            nonEmptyArrays = nonEmptyArrays.toSet(),
            resolvedArrays = resolvedArrays.toSet(),
            monitorValues = monitorValues.mapValues { (_, values) -> values.toSet() },
            caller = caller,
        )
    }

    private fun observedSnapshot(): Map<List<PathStep>, Set<String>> =
        observed.mapValues { (_, types) -> types.toSet() }

    // ------------------------------------------------------------------ обход

    private fun walk(node: JsonElement, path: List<PathStep>) {
        val usedExact = endpoint.bestUsedExact(path)
        val ignored = endpoint.bestIgnored(path)
        val unknown = endpoint.bestUnknownCovering(path)
        val below = endpoint.bestUsedAtOrBelow(path)

        // `ignored` и `unknown` перекрывают узел целиком. Исключение — если под
        // узлом есть более специфичное правило `used`: тогда спускаемся дальше.
        val blocker = listOfNotNull(ignored?.rank, unknown?.rank).maxOrNull()
        if (blocker != null && (below == null || blocker >= below.rank)) {
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

        endpoint.bestMonitorExact(path)?.let { rule -> collectMonitorValue(rule, node) }

        when (node) {
            is JsonObject -> node.forEach { (key, value) -> walk(value, path + PathStep.Key(key)) }
            is JsonArray -> {
                if (node.isEmpty()) {
                    if (usedExact != null) {
                        // На пути массива есть правило: пустота — это либо допустимый
                        // вариант (`array?`), либо отдельное расхождение (`empty`).
                        resolvedArrays += path
                        settledEmptyArrays += path
                        if ("array" in usedExact.types && !usedExact.optional) {
                            emptyArrays += path
                        }
                    } else {
                        blockedArrays.add(path to "пустой массив: содержимое не проверено")
                    }
                    return
                }
                nonEmptyArrays += path
                sampleIndices(node.size).forEach { index -> walk(node[index], path + PathStep.ArrayItem) }
                if (node.size > ARRAY_SAMPLE_SIZE) {
                    blockedArrays.add(
                        path to "массив обрезан (${node.size} элементов, проверено $ARRAY_SAMPLE_SIZE)"
                    )
                } else {
                    // Содержимое проверено целиком: прошлые `unverified` под массивом больше не актуальны.
                    resolvedArrays += path
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

    // ------------------------------------------------------- мониторинг значений

    /**
     * Значение пути `monitor` из этого ответа. Скаляр пишется как есть, массив
     * скаляров — элементы по одному, объект или массив с не-скалярными элементами
     * — маркер: правило ошибочно, поля такого узла мониторят по отдельности.
     */
    private fun collectMonitorValue(rule: Endpoint.MonitorRule, node: JsonElement) {
        when (node) {
            is JsonObject -> addMonitorMarker(rule.canonical, monitorMarkerObject)
            is JsonArray -> {
                if (node.isEmpty()) return
                if (node.all { it is JsonPrimitive || it is JsonNull }) {
                    sampleIndices(node.size).forEach { index -> addMonitorScalar(rule.canonical, node[index]) }
                } else {
                    addMonitorMarker(rule.canonical, monitorMarkerArray)
                }
            }

            else -> addMonitorScalar(rule.canonical, node)
        }
    }

    private fun addMonitorMarker(canonical: String, marker: String) {
        // Маркер — диагностика: лимиты на него не действуют.
        monitorValues.getOrPut(canonical) { mutableSetOf() }.add(marker)
    }

    private fun addMonitorScalar(canonical: String, node: JsonElement) {
        val value = if (node is JsonNull) "null" else (node as JsonPrimitive).content
        if (value.length > monitorValueLengthLimit) return
        val values = monitorValues.getOrPut(canonical) { mutableSetOf() }
        if (values.size >= monitorValueCountLimit && value !in values) return
        values.add(value)
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
                sample = newSampleValue(node),
                note = newSampleNote(node),
            )
        }

        emptyArrays.filter { it !in nonEmptyArrays }.forEach { path ->
            diffs += PendingDiff(
                endpoint = endpoint.name,
                kind = DiffKind.EMPTY,
                path = formatPath(path),
                expected = "непустой array",
                observed = "пустой массив",
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
        collectMonitorDiffs(diffs)
        return diffs
    }

    /**
     * `value`: наблюдённое значение пути `monitor`, которого нет в эталонных
     * `monitor.values`. Как и `new`, снимется только переоценкой по обновлённому
     * эталону: ответы с этим значением будут приходить и дальше.
     */
    private fun collectMonitorDiffs(diffs: MutableList<PendingDiff>) {
        monitorValues.forEach { (canonical, values) ->
            val recorded = endpoint.recordedValues[canonical] ?: emptySet()
            values.filter { it !in recorded }.forEach { value ->
                diffs += PendingDiff(
                    endpoint = endpoint.name,
                    kind = DiffKind.VALUE,
                    path = canonical,
                    observed = value,
                    note = "новое значение пути monitor",
                )
            }
        }
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

                // Массив, на пути которого есть правило, пуст: содержимое не проверяется.
                suppressedByEmptyArray(rule.path) -> Unit

                // Необязательный массив отсутствует: это допустимо, содержимое не проверяем.
                hasAbsentOptionalArrayAncestor(rule, unmatched) -> Unit

                // Необязательный родитель отсутствует: содержимое проверить нельзя.
                unmatched.firstOrNull {
                    isAncestorPattern(it.path, rule.path) && it.optional && "array" !in it.types
                } != null -> {
                    val parent = unmatched.first {
                        isAncestorPattern(it.path, rule.path) && it.optional && "array" !in it.types
                    }
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

    private fun blockedReason(rulePath: List<PathStep>): String? =
        blockedArrays.firstOrNull { (arrayPath, reason) -> isUnderArray(arrayPath, rulePath) }?.second

    /** Правило лежит под массивом, который пуст, хотя на его пути есть правило. */
    private fun suppressedByEmptyArray(rulePath: List<PathStep>): Boolean =
        settledEmptyArrays.any { arrayPath -> isUnderArray(arrayPath, rulePath) }

    /** Отсутствующий необязательный массив (`array?`) закрывает вопросы к его содержимому. */
    private fun hasAbsentOptionalArrayAncestor(rule: Endpoint.UsedRule, unmatched: List<Endpoint.UsedRule>): Boolean =
        unmatched.any { ancestor ->
            isAncestorPattern(ancestor.path, rule.path) &&
                ancestor.optional &&
                "array" in ancestor.types
        }

    /** Пример для `type`: короткий, чтобы записи не разрастались. */
    private fun sampleValue(node: JsonElement): String = limitSample(node.toString(), diffSampleLimit)

    /**
     * Образец нового узла: для массива — один элемент целиком (последний просмотренный),
     * для объекта — весь объект. В 16 КБ обычно помещается целый элемент.
     */
    private fun newSampleValue(node: JsonElement): String? = when {
        node is JsonArray -> if (node.isEmpty()) null else limitSample(node.last().toString(), newSampleLimit)
        node is JsonObject -> limitSample(node.toString(), newSampleLimit)
        else -> sampleValue(node)
    }

    /** Позиция элемента в новом массиве, чтобы образец можно было сопоставить с ответом. */
    private fun newSampleNote(node: JsonElement): String? = when {
        node !is JsonArray -> null
        node.isEmpty() -> "массив пуст"
        else -> "элемент ${node.size} из ${node.size}"
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
