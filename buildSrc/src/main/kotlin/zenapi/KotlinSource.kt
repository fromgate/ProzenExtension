package zenapi

/**
 * Генерирует Kotlin-файл с эталоном. Ресурс + fetch не используются: эталон нужен
 * синхронно на старте, ещё до первого запроса.
 */
fun Compilation.toKotlinSource(packageName: String = "devtools"): String = buildString {
    appendLine("// Сгенерировано из reference/zen-api.toml. Не редактировать вручную.")
    appendLine("package $packageName")
    appendLine()
    appendLine("internal const val ZEN_API_REFERENCE_HASH: String = \"$hash\"")
    appendLine()
    appendLine("internal const val ZEN_API_REFERENCE_JSON: String = \"\"\"")
    appendLine(escapeRawString(json))
    appendLine("\"\"\"")
    appendLine()
    append("internal const val ZEN_API_REFERENCE_TOML: String = \"\"\"")
    append(escapeRawSource(source))
    append("\"\"\"")
    appendLine()
}

/** В raw-строке Kotlin интерполируется только `$`; кавычки и переводы строк уже экранированы JSON-ом. */
private fun escapeRawString(value: String): String = value.replace("$", "\${'$'}")

/**
 * Исходный TOML вставляется в raw-строку байт-в-байт. `"""` внутри текста
 * закрыло бы строку раньше времени, поэтому генератор падает явно.
 */
private fun escapeRawSource(value: String): String {
    require(!value.contains("\"\"\"")) {
        "reference/zen-api.toml содержит \"\"\" и не может быть вставлен в Kotlin raw-string без потери текста"
    }
    return value.replace("$", "\${'$'}")
}
