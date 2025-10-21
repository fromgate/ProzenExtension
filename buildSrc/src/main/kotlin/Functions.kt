fun runCommand(vararg parts: String): String {
    val process = ProcessBuilder(*parts)
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    return output
}