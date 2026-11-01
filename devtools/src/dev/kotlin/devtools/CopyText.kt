package devtools

/** Сколько схлопнутых путей показывать в комментарии к одному блоку `new`. */
private const val NEW_DETAIL_LIMIT = 12

/** Сколько символов примера значения оставлять в комментарии. */
private const val NEW_SAMPLE_LIMIT = 120

private val HEADER_RULE = "═".repeat(72)

/**
 * Собирает «почти TOML» с расхождениями: разделы `[endpoints.*]` можно
 * переносить в `reference/zen-api.toml`, строки с `#` — пояснения для правки.
 *
 * Содержимое записи уже канонично (запрос без `volatileParams`), поэтому
 * генератор ничего не запрашивает и не меняет: только форматирует.
 */
internal fun buildCopyText(records: List<DiffRecord>, reference: ReferenceDocument): String {
    val builder = StringBuilder()
    builder.appendLine("# Prozen: расхождения API Дзена с эталоном")
    builder.appendLine("# Записей: ${records.size}; эталон: ${reference.hash}")
    builder.appendLine("# «Почти TOML»: разделы [endpoints.*] переносятся в reference/zen-api.toml,")
    builder.appendLine("# строки с # — пояснения, переносить их не обязательно.")

    if (records.isEmpty()) {
        builder.appendLine()
        builder.appendLine("# Расхождений нет.")
        return builder.toString()
    }

    collectBlocks(records, reference).forEach { block ->
        builder.appendLine()
        when (block) {
            is StubBlock -> renderStub(builder, block)
            is EndpointBlock -> renderEndpoint(builder, block)
        }
    }
    return builder.toString()
}

// ---------------------------------------------------------------- блоки вывода

private sealed interface ExportBlock {
    val name: String
}

/** Заготовка раздела для неописанного эндпоинта или варианта параметров. */
private class StubBlock(
    val record: DiffRecord,
    override val name: String,
    val match: StubMatch,
) : ExportBlock

/** Накопленные расхождения по известному эндпоинту. */
private class EndpointBlock(
    override val name: String,
    val records: List<DiffRecord>,
) : ExportBlock

private fun collectBlocks(records: List<DiffRecord>, reference: ReferenceDocument): List<ExportBlock> {
    val usedNames = reference.endpoints.mapTo(mutableSetOf()) { it.name }
    val blocks = mutableListOf<ExportBlock>()

    // Заготовки — по одной на запись: разные параметры дают разные разделы.
    records
        .filter { it.kind == DiffKind.NEW_ENDPOINT || it.kind == DiffKind.NEW_VARIANT }
        .sortedWith(compareBy({ it.endpoint }, { it.kind.ordinal }, { it.request ?: "" }))
        .forEach { record ->
            blocks += StubBlock(
                record = record,
                name = uniqueEndpointName(record.endpoint, usedNames),
                match = buildStubMatch(record.request, reference),
            )
        }

    records
        .filter { it.kind != DiffKind.NEW_ENDPOINT && it.kind != DiffKind.NEW_VARIANT }
        .groupBy { it.endpoint }
        .entries
        .sortedBy { it.key }
        .forEach { (name, group) -> blocks += EndpointBlock(name, group) }

    return blocks.sortedBy { it.name }
}

private fun uniqueEndpointName(base: String, used: MutableSet<String>): String {
    val clean = base.ifBlank { "endpoint" }
    if (used.add(clean)) return clean
    var index = 2
    while (!used.add("$clean-$index")) index++
    return "$clean-$index"
}

// ------------------------------------------------------------------- рендеринг

private fun renderStub(builder: StringBuilder, block: StubBlock) {
    val record = block.record
    val title = if (record.kind == DiffKind.NEW_ENDPOINT) "НОВЫЙ ЭНДПОИНТ" else "НОВЫЙ ВАРИАНТ"
    builder.appendLine("# $HEADER_RULE")
    builder.appendLine("# $title: ${block.name}${stackSuffix(record)}")
    builder.appendLine("#   запрос: ${record.request ?: "не записан"}")
    if (record.kind == DiffKind.NEW_VARIANT) {
        builder.appendLine("#   близкий эндпоинт: ${record.endpoint}")
    }
    if (record.request == null) {
        builder.appendLine("#   TODO: запрос не сохранён — описать match вручную.")
    } else if (!block.match.templated) {
        builder.appendLine("#   TODO: путь не найден в эталоне — заменить литеральные id на {id}.")
    }
    builder.appendLine("# $HEADER_RULE")
    builder.appendLine()
    builder.appendLine("[endpoints.${block.name}]")
    if (block.match.match.isNotBlank()) {
        builder.appendLine("match = \"${tomlEscape(block.match.match)}\"")
    } else {
        builder.appendLine("# match = \"GET путь?параметры\"   # TODO: запрос не записан")
    }
    val note = if (record.kind == DiffKind.NEW_ENDPOINT) {
        "TODO: описание эндпоинта"
    } else {
        "TODO: чем отличается от ${record.endpoint}"
    }
    builder.appendLine("note  = \"${tomlEscape(note)}\"")
    builder.appendLine()
    builder.appendLine("# TODO: добавить [endpoints.${block.name}.used] и [endpoints.${block.name}.ignored].")
}

private fun renderEndpoint(builder: StringBuilder, block: EndpointBlock) {
    builder.appendLine("# $HEADER_RULE")
    builder.appendLine("# ${block.name}")
    builder.appendLine("# $HEADER_RULE")
    builder.appendLine()

    val live = block.records.filterNot { it.orphaned }
    val orphaned = block.records.filter { it.orphaned }

    val newRecords = live.filter { it.kind == DiffKind.NEW }
    if (newRecords.isNotEmpty()) {
        renderNew(builder, block.name, newRecords)
    }

    val others = live
        .filter { it.kind != DiffKind.NEW }
        .sortedWith(compareBy({ it.kind.ordinal }, { it.path }, { it.request ?: "" }))
    if (others.isNotEmpty()) {
        if (newRecords.isNotEmpty()) builder.appendLine()
        builder.appendLine("# Расхождения по форме ответа (правятся в коде):")
        others.forEach { record ->
            builder.appendLine(diffComment(record))
            if (record.kind == DiffKind.UNVERIFIED) {
                record.note?.takeIf { it.isNotBlank() }?.let { builder.appendLine("#   причина: $it") }
            }
        }
    }

    if (orphaned.isNotEmpty()) {
        if (newRecords.isNotEmpty() || others.isNotEmpty()) builder.appendLine()
        builder.appendLine("# Эндпоинт исчез из эталона: эти записи нужно разобрать вручную.")
        orphaned
            .sortedWith(compareBy({ it.kind.ordinal }, { it.path }))
            .forEach { builder.appendLine(diffComment(it)) }
    }
}

/**
 * `new` схлопываются по родителю: одиночный путь остаётся точным, соседи
 * объединяются в `родитель.**`. Подробности — в комментариях перед строкой.
 */
private fun renderNew(builder: StringBuilder, endpoint: String, records: List<DiffRecord>) {
    val groups = records
        .map { record -> record to parsePath(record.path) }
        .groupBy { (_, steps) -> if (steps.isEmpty()) emptyList() else steps.dropLast(1) }

    builder.appendLine("# Новые пути (new) — поля, которых нет в эталоне.")
    builder.appendLine("# Строку с пустой причиной перенести в [endpoints.$endpoint.ignored]")
    builder.appendLine("# (объединить с существующим разделом), причину заполнить.")
    builder.appendLine("[endpoints.$endpoint.ignored]")

    // Родитель, под которым уже схлопнуты соседи, покрывает и вложенные пути:
    // отдельные строки для них были бы избыточны.
    val collapsed = mutableListOf<List<PathStep>>()

    groups.entries
        .sortedBy { (parent, _) -> parent.size }
        .forEach { (parent, members) ->
            if (collapsed.any { isPrefixPath(it, parent) }) {
                // Родитель уже схлопнут: строка была бы избыточной, но детали не теряем.
                appendNewDetails(builder, members.map { it.first })
                return@forEach
            }

            if (parent.isEmpty() || members.size == 1) {
                members.forEach { (record, steps) ->
                    appendNewDetails(builder, listOf(record))
                    val path = if (steps.isEmpty()) "**" else tomlEscape(formatPath(steps))
                    builder.appendLine("\"$path\" = \"\"")
                }
            } else {
                collapsed += parent
                appendNewDetails(builder, members.map { it.first })
                builder.appendLine("\"${tomlEscape(formatPath(parent))}.**\" = \"\"")
            }
        }
}

/** `prefix` — предок `path` или совпадает с ним (без учёта wildcards путей). */
private fun isPrefixPath(prefix: List<PathStep>, path: List<PathStep>): Boolean {
    if (prefix.size > path.size) return false
    return prefix.indices.all { patternSegmentsMatch(prefix[it], path[it]) }
}

private fun appendNewDetails(builder: StringBuilder, records: List<DiffRecord>) {
    records.take(NEW_DETAIL_LIMIT).forEach { record ->
        val path = record.path.ifEmpty { "(корень ответа)" }
        builder.appendLine("#   $path — ${record.observed ?: "?"}${stackSuffix(record)}")
        record.sample?.takeIf { it.isNotBlank() }?.let { sample ->
            builder.appendLine("#     пример: ${limitText(sample, NEW_SAMPLE_LIMIT)}")
        }
    }
    if (records.size > NEW_DETAIL_LIMIT) {
        builder.appendLine("#   … ещё ${records.size - NEW_DETAIL_LIMIT}")
    }
}

// ------------------------------------------------------------------ заготовки

/** Строка `match` для заготовки: реальный запрос с шаблоном пути, если он известен. */
internal class StubMatch(val match: String, val templated: Boolean)

internal fun buildStubMatch(requestText: String?, reference: ReferenceDocument): StubMatch {
    val text = requestText?.takeIf { it.isNotBlank() } ?: return StubMatch("", false)
    val request = ActualRequest.parseCanonical(text)
    val candidate = reference.pathCandidates(request).maxByOrNull { matchScore(it, request) }

    val segments = candidate?.match?.segments?.map { segment ->
        when (segment) {
            is MatchSegment.Literal -> segment.value
            is MatchSegment.Variable -> "{${segment.name}}"
        }
    } ?: request.segments

    val query = request.params.entries
        .filter { it.key !in reference.volatileParams }
        .sortedBy { it.key }
        .flatMap { (name, values) ->
            values.sorted().map { value -> "${encodeQueryValue(name)}=${encodeQueryValue(value)}" }
        }
        .joinToString("&")

    val path = "${request.method} ${segments.joinToString("/")}"
    return StubMatch(
        match = if (query.isEmpty()) path else "$path?$query",
        templated = candidate != null,
    )
}

// ---------------------------------------------------------------- комментарии

private fun diffComment(record: DiffRecord): String {
    val suffix = stackSuffix(record)
    val prefix = if (record.orphaned) "[эндпоинт исчез из эталона] " else ""
    return when (record.kind) {
        DiffKind.NEW ->
            "# ${prefix}new: ${record.path} — ${record.observed ?: "?"}$suffix"

        DiffKind.MISSING ->
            "# ${prefix}missing: ${record.path} — ожидалось ${record.expected ?: "?"}, получено отсутствует$suffix"

        DiffKind.TYPE ->
            "# ${prefix}type: ${record.path} — ожидалось ${record.expected ?: "?"}, получено ${record.observed ?: "?"}$suffix"

        DiffKind.STATUS ->
            "# ${prefix}status: ${record.request ?: record.path} — ожидалось ${record.expected ?: "200"}, получено ${record.observed ?: "?"}$suffix"

        DiffKind.UNVERIFIED ->
            "# ${prefix}unverified: ${record.path} — ожидалось ${record.expected ?: "?"}, получено не проверено$suffix"

        // В комментарии попадают только осиротевшие записи: раздел писать некуда.
        DiffKind.NEW_ENDPOINT ->
            "# ${prefix}new-endpoint: ${record.request ?: "?"}$suffix"

        DiffKind.NEW_VARIANT ->
            "# ${prefix}new-variant: ${record.request ?: "?"}$suffix"
    }
}

/** ` (getScr, ×4)` — вызывающий и число повторов, если они есть. */
private fun stackSuffix(record: DiffRecord): String {
    val parts = mutableListOf<String>()
    record.caller?.takeIf { it.isNotBlank() }?.let(parts::add)
    if (record.count > 1) parts += "×${record.count}"
    return if (parts.isEmpty()) "" else " (${parts.joinToString(", ")})"
}

// -------------------------------------------------------------------- утилиты

private fun tomlEscape(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

private fun limitText(value: String, limit: Int): String =
    if (value.length <= limit) value else value.take(limit) + "…"
