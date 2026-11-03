import java.io.File

fun runCommand(vararg parts: String): String {
    val process = ProcessBuilder(*parts)
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    return output
}

/**
 * Запускает команду и падает при ненулевом коде выхода.
 * Нужен для вложенных сборок: их провал обязан ронять внешнюю задачу.
 */
fun runCommandChecked(vararg parts: String): String {
    val process = ProcessBuilder(*parts)
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().readText().trim()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw org.gradle.api.GradleException(
            "Команда завершилась с кодом $exitCode: ${parts.joinToString(" ")}\n$output"
        )
    }
    return output
}

/** Версия расширения — первый заголовок `### X.Y.Z` в CHANGELOG.md. */
fun getVersionFromChangelog(rootDir: File): String {
    val changelogFile = File(rootDir, "CHANGELOG.md")
    val versionRegex = Regex("""^### (\d+\.\d+\.\d+)""")
    for (line in changelogFile.readLines()) {
        val matchResult = versionRegex.find(line)
        if (matchResult != null) {
            return matchResult.groupValues[1]
        }
    }
    throw IllegalStateException("Version not found in CHANGELOG.md")
}
