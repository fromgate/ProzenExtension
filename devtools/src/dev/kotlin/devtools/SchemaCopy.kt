package devtools

import kotlin.js.Date

/** Предел образца в `unknown`-записи: файл не должен разрастаться из-за одного ответа. */
internal const val unknownSampleLimit: Int = 2 * 1024

/** Одна запись, которую патчер дописывает в `[endpoints.<имя>.unknown]`. */
internal data class SchemaAddition(
    val endpoint: String,
    val path: String,
    val type: String,
    val date: String,
    val sample: String?,
)

/** Результат копирования схемы: текст и число дописанного (для кнопки и уведомления). */
internal data class SchemaCopyResult(
    val text: String,
    val newRecords: Int,
    val newMonitorValues: Int,
)

/** Одно обновление `[endpoints.<имя>.monitor.values]`: полный новый набор значений пути. */
internal data class MonitorAddition(
    val endpoint: String,
    val path: String,
    val values: List<String>,
    /** Сколько значений добавилось к файловому набору: для счётчика кнопки. */
    val added: Int,
)

/**
 * `new`, которые ещё не покрыты эталоном и попадут в `unknown` при копировании.
 *
 * Корневые `new` (пустой путь) пропускаются: эталон не умеет описывать сам
 * корень ответа, такие записи остаются в старом отчёте расхождений.
 */
internal fun newSchemaRecords(
    records: List<DiffRecord>,
    reference: ReferenceDocument,
): List<DiffRecord> = records
    .asSequence()
    .filter { it.kind == DiffKind.NEW && !it.orphaned && it.path.isNotEmpty() }
    .filter { record ->
        val endpoint = reference.endpoint(record.endpoint) ?: return@filter false
        !endpoint.isCovered(parsePath(record.path))
    }
    .distinctBy { it.endpoint to it.path }
    .sortedWith(compareBy({ it.endpoint }, { it.path }))
    .toList()

/** Полный текст эталона с дописанными `new` в `unknown` и значениями в `monitor.values`. */
internal fun buildSchemaCopy(
    records: List<DiffRecord>,
    monitorRecords: List<MonitorStore.MonitorRecord>,
    reference: ReferenceDocument,
    source: String,
    today: Date,
): SchemaCopyResult {
    val additions = newSchemaRecords(records, reference).map { record ->
        SchemaAddition(
            endpoint = record.endpoint,
            path = record.path,
            type = record.observed?.takeIf { it.isNotBlank() } ?: "any",
            date = formatUnknownDate(record.firstSeen, today),
            // Образец идёт в строку-комментарий: переводы строк в нём недопустимы.
            sample = record.sample
                ?.takeIf { it.isNotBlank() }
                ?.replace('\r', ' ')
                ?.replace('\n', ' ')
                ?.let { limitSample(it, unknownSampleLimit) },
        )
    }
    val monitorAdditions = buildMonitorAdditions(monitorRecords, reference)
    val text = patchSchema(source, additions).let { patchMonitorValues(it, monitorAdditions) }
    return SchemaCopyResult(
        text = text,
        newRecords = additions.size,
        newMonitorValues = monitorAdditions.sumOf { it.added },
    )
}

/**
 * Что добавить в `monitor.values`: пути, у которых в storage есть значения вне
 * файлового набора. Полный набор (`values`) — для перезаписи строки целиком;
 * после сохранения файла и пересборки наборы совпадут и патч станет пустым
 * (round-trip стабилен).
 */
internal fun buildMonitorAdditions(
    monitorRecords: List<MonitorStore.MonitorRecord>,
    reference: ReferenceDocument,
): List<MonitorAddition> = monitorRecords
    .groupBy { it.endpoint to it.path }
    .mapNotNull { (key, group) ->
        val (endpointName, path) = key
        val endpoint = reference.endpoint(endpointName) ?: return@mapNotNull null
        endpoint.bestMonitorExact(parsePath(path)) ?: return@mapNotNull null
        val recorded = endpoint.recordedValues[path] ?: emptySet()
        val observed = group.flatMap { it.values.keys }.toSet()
        val fresh = observed.count { it !in recorded }
        if (fresh == 0) return@mapNotNull null
        MonitorAddition(
            endpoint = endpointName,
            path = path,
            values = (recorded + observed).sorted(),
            added = fresh,
        )
    }
    .sortedWith(compareBy({ it.endpoint }, { it.path }))

/** `DD.MM.YYYY`; при отсутствии даты записи берётся текущая. */
internal fun formatUnknownDate(millis: Long, today: Date): String {
    val date = if (millis > 0) Date(millis.toDouble()) else today
    val day = date.getDate()
    val month = date.getMonth() + 1
    return buildString {
        if (day < 10) append('0')
        append(day).append('.')
        if (month < 10) append('0')
        append(month).append('.')
        append(date.getFullYear())
    }
}

/**
 * Дописывает записи в `[endpoints.<имя>.unknown]`, не переформатируя остальной
 * текст. Если дописывать нечего, возвращает исходную строку без изменений.
 *
 * Точка вставки — после последней key-value строки якорной таблицы (unknown,
 * ignored, used или самого эндпоинта). Комментарии следующего эндпоинта
 * остаются на месте.
 */
internal fun patchSchema(source: String, additions: List<SchemaAddition>): String {
    if (additions.isEmpty()) return source

    val lines = splitLines(source)
    val eol = detectEol(source)
    val insertions = additions
        .groupBy { it.endpoint }
        .entries
        .sortedBy { it.key }
        .mapNotNull { (endpoint, group) -> planInsertion(lines, endpoint, group, eol) }

    if (insertions.isEmpty()) return source

    var result = source
    insertions
        .groupBy { it.offset }
        .entries
        .sortedByDescending { it.key }
        .forEach { (offset, group) ->
            val text = group.joinToString("") { it.text }
            result = result.substring(0, offset) + text + result.substring(offset)
        }

    // Патчер не должен добавлять финальный перевод строки, которого не было.
    if (!source.endsWith("\n") && result.endsWith(eol)) {
        result = result.removeSuffix(eol)
    }
    return result
}

// ------------------------------------------------------------- monitor.values

private data class Patch(val offset: Int, val remove: Int, val text: String)

/**
 * Дописывает или переписывает значения в `[endpoints.<имя>.monitor.values]`,
 * не трогая остальной текст. Отсутствующую таблицу создаёт сразу после
 * `[endpoints.<имя>.monitor]` (иначе после самого эндпоинта). Существующую
 * строку ключа перезаписывает целиком: таблица принадлежит патчеру.
 */
internal fun patchMonitorValues(source: String, additions: List<MonitorAddition>): String {
    if (additions.isEmpty()) return source

    val lines = splitLines(source)
    val eol = detectEol(source)
    val patches = additions
        .groupBy { it.endpoint }
        .entries
        .sortedBy { it.key }
        .flatMap { (endpoint, group) -> planMonitorPatches(lines, endpoint, group, eol) }

    if (patches.isEmpty()) return source

    var result = source
    patches
        .sortedWith(compareByDescending<Patch> { it.offset }.thenByDescending { it.remove })
        .forEach { patch ->
            result = result.substring(0, patch.offset) + patch.text + result.substring(patch.offset + patch.remove)
        }
    return result
}

private fun planMonitorPatches(
    lines: List<SchemaLine>,
    endpoint: String,
    additions: List<MonitorAddition>,
    eol: String,
): List<Patch> {
    val valuesHeader = findHeader(lines, "endpoints.$endpoint.monitor.values")
    if (valuesHeader != null) {
        val body = tableBody(lines, valuesHeader)
        val patches = mutableListOf<Patch>()
        val appended = mutableListOf<String>()
        additions.sortedBy { it.path }.forEach { addition ->
            val lineIndex = body.indexOfFirst { parseTomlKey(it.text) == addition.path }
            if (lineIndex >= 0) {
                val line = body[lineIndex]
                patches += Patch(
                    offset = line.start,
                    remove = line.end - line.start,
                    text = renderMonitorEntry(addition) + if (line.hasEol) eol else "",
                )
            } else {
                appended += renderMonitorEntry(addition)
            }
        }
        if (appended.isNotEmpty()) {
            val insertion = appendEntries(lines, bodyAnchor(lines, valuesHeader), appended, eol)
            patches += Patch(insertion.offset, 0, insertion.text)
        }
        return patches
    }

    val anchorHeader = findHeader(lines, "endpoints.$endpoint.monitor")
        ?: findHeader(lines, "endpoints.$endpoint")
        ?: return emptyList()
    val insertion = insertSection(
        lines = lines,
        anchor = bodyAnchor(lines, anchorHeader),
        header = "[endpoints.$endpoint.monitor.values]",
        entries = additions.sortedBy { it.path }.map(::renderMonitorEntry),
        eol = eol,
    )
    return listOf(Patch(insertion.offset, 0, insertion.text))
}

private fun renderMonitorEntry(addition: MonitorAddition): String = buildString {
    append('"').append(tomlEscape(addition.path)).append("\" = [")
    addition.values.joinTo(this) { "\"${tomlEscape(it)}\"" }
    append(']')
}

// ------------------------------------------------------------------- планирование

private data class Insertion(val offset: Int, val text: String)

private data class SchemaLine(val start: Int, val end: Int, val text: String) {
    val hasEol: Boolean get() = end > start + text.length
}

private fun planInsertion(
    lines: List<SchemaLine>,
    endpoint: String,
    additions: List<SchemaAddition>,
    eol: String,
): Insertion? {
    val unknownHeader = findHeader(lines, "endpoints.$endpoint.unknown")
    if (unknownHeader != null) {
        val existingPaths = tableBody(lines, unknownHeader)
            .mapNotNull { parseTomlKey(it.text) }
            .map(::parsePath)
        val kept = additions
            .distinctBy { it.path }
            .filterNot { addition ->
                val path = parsePath(addition.path)
                existingPaths.any { other -> ignoredCovers(other, path) }
            }
            .sortedBy { it.path }
        if (kept.isEmpty()) return null
        return appendEntries(
            lines = lines,
            anchor = bodyAnchor(lines, unknownHeader),
            entries = kept.map { renderAddition(it, eol) },
            eol = eol,
        )
    }

    val anchorHeader = findHeader(lines, "endpoints.$endpoint.ignored")
        ?: findHeader(lines, "endpoints.$endpoint.used")
        ?: findHeader(lines, "endpoints.$endpoint")
        ?: return null

    val kept = additions.distinctBy { it.path }.sortedBy { it.path }
    if (kept.isEmpty()) return null

    return insertSection(
        lines = lines,
        anchor = bodyAnchor(lines, anchorHeader),
        header = "[endpoints.$endpoint.unknown]",
        entries = kept.map { renderAddition(it, eol) },
        eol = eol,
    )
}

/** Последняя содержательная строка таблицы: ключ, комментарий или сам заголовок. */
private fun bodyAnchor(lines: List<SchemaLine>, header: Int): Int {
    val bodyEnd = nextHeaderIndex(lines, header) ?: lines.size
    val lastKey = (bodyEnd - 1 downTo header + 1).firstOrNull { isKeyValue(lines[it].text) }
    if (lastKey != null) return lastKey

    // Таблица пуста. Комментарии до первой пустой строки считаем её частью,
    // а комментарии следующего эндпоинта отделены пустой строкой.
    val firstBlank = (header + 1 until bodyEnd).firstOrNull { lines[it].text.isBlank() }
    val end = firstBlank ?: bodyEnd
    return (end - 1).coerceAtLeast(header)
}

private fun appendEntries(
    lines: List<SchemaLine>,
    anchor: Int,
    entries: List<String>,
    eol: String,
): Insertion {
    val line = lines[anchor]
    val next = lines.getOrNull(anchor + 1)
    val text = buildString {
        if (!line.hasEol) append(eol)
        entries.forEach { append(it).append(eol) }
        if (next != null && !next.text.isBlank()) append(eol)
    }
    return Insertion(line.end, text)
}

private fun insertSection(
    lines: List<SchemaLine>,
    anchor: Int,
    header: String,
    entries: List<String>,
    eol: String,
): Insertion {
    // Существующая пустая строка после якоря станет разделителем перед секцией.
    var index = anchor
    if (index + 1 < lines.size && lines[index + 1].text.isBlank()) {
        index += 1
    }

    val line = lines[index]
    val next = lines.getOrNull(index + 1)
    val text = buildString {
        if (!line.hasEol) append(eol)
        if (index == anchor) append(eol)
        append(header).append(eol)
        entries.forEach { append(it).append(eol) }
        if (next != null && !next.text.isBlank()) append(eol)
    }
    return Insertion(line.end, text)
}

private fun renderAddition(addition: SchemaAddition, eol: String): String = buildString {
    append('"').append(tomlEscape(addition.path)).append("\" = \"")
    append(tomlEscape(addition.type)).append("\" # обнаружено ").append(addition.date)
    addition.sample?.let { append(eol).append("#   пример: ").append(it) }
}

/** Экранирование строки TOML: обратная косая черта и кавычка. */
private fun tomlEscape(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

// ---------------------------------------------------------------------- сканер

private fun splitLines(source: String): List<SchemaLine> {
    if (source.isEmpty()) return emptyList()

    val lines = mutableListOf<SchemaLine>()
    var start = 0
    while (start < source.length) {
        val newline = source.indexOf('\n', start)
        if (newline < 0) {
            lines += SchemaLine(start, source.length, source.substring(start))
            break
        }
        val textEnd = if (newline > start && source[newline - 1] == '\r') newline - 1 else newline
        lines += SchemaLine(start, newline + 1, source.substring(start, textEnd))
        start = newline + 1
    }
    return lines
}

private fun detectEol(source: String): String {
    val newline = source.indexOf('\n')
    return if (newline > 0 && source[newline - 1] == '\r') "\r\n" else "\n"
}

private fun isTableHeader(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.startsWith("[") && trimmed.endsWith("]") && !trimmed.startsWith("[[")
}

private fun tableName(text: String): String =
    text.trim().removePrefix("[").removeSuffix("]").trim()

private fun findHeader(lines: List<SchemaLine>, name: String): Int? {
    val index = lines.indexOfFirst { isTableHeader(it.text) && tableName(it.text) == name }
    return if (index >= 0) index else null
}

private fun nextHeaderIndex(lines: List<SchemaLine>, header: Int): Int? =
    (header + 1 until lines.size).firstOrNull { isTableHeader(lines[it].text) }

private fun tableBody(lines: List<SchemaLine>, header: Int): List<SchemaLine> {
    val end = nextHeaderIndex(lines, header) ?: lines.size
    return lines.subList(header + 1, end)
}

private fun isKeyValue(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[")) return false
    return trimmed.contains('=')
}

/** Ключи в файле всегда в кавычках; для проверки дублей достаточно `\"` и `\\\\`. */
private fun parseTomlKey(text: String): String? {
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("\"")) return null

    val result = StringBuilder()
    var index = 1
    while (index < trimmed.length) {
        when (val char = trimmed[index]) {
            '"' -> return result.toString()
            '\\' -> {
                if (index + 1 >= trimmed.length) return null
                result.append(trimmed[index + 1])
                index += 2
            }
            else -> {
                result.append(char)
                index++
            }
        }
    }
    return null
}
