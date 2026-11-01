package zenapi

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/** Проверяет эталон, ничего не записывая. Пригодится для CI и ручного прогона. */
abstract class VerifyApiReferenceTask : DefaultTask() {

    @get:InputFile
    abstract val referenceFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val compilation = compileOrFail()
        compilation.warnings.forEach { logger.warn("reference: $it") }
        val document = compilation.document
        val used = document.endpoints.sumOf { it.used.size }
        val ignored = document.endpoints.sumOf { it.ignored.size }
        logger.lifecycle(
            "Эталон API: эндпоинтов ${document.endpoints.size}, used $used, ignored $ignored, ${compilation.hash}"
        )
    }

    private fun compileOrFail(): Compilation {
        val file = referenceFile.get().asFile
        if (!file.isFile) {
            throw GradleException("Не найден эталон API: ${file.absolutePath}")
        }
        return try {
            ReferenceCompiler.compile(file.readText())
        } catch (error: ReferenceException) {
            throw GradleException(
                "Эталон API ${file.name} содержит ошибки:\n" +
                    error.errors.joinToString("\n") { "  - $it" }
            )
        }
    }
}

/** Собирает Kotlin-константу с эталоном для dev-сборки. */
abstract class GenerateApiReferenceTask : DefaultTask() {

    @get:InputFile
    abstract val referenceFile: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        packageName.convention("devtools")
    }

    @TaskAction
    fun generate() {
        val file = referenceFile.get().asFile
        if (!file.isFile) {
            throw GradleException("Не найден эталон API: ${file.absolutePath}")
        }

        val compilation = try {
            ReferenceCompiler.compile(file.readText())
        } catch (error: ReferenceException) {
            throw GradleException(
                "Эталон API ${file.name} содержит ошибки:\n" +
                    error.errors.joinToString("\n") { "  - $it" }
            )
        }

        compilation.warnings.forEach { logger.warn("reference: $it") }

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(compilation.toKotlinSource(packageName.get()))
        logger.lifecycle("Эталон API записан в ${output.path} (${compilation.hash})")
    }
}
