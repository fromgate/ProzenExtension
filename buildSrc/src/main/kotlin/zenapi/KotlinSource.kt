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
}

/** В raw-строке Kotlin интерполируется только `$`; кавычки и переводы строк уже экранированы JSON-ом. */
private fun escapeRawString(value: String): String = value.replace("$", "\${'$'}")
