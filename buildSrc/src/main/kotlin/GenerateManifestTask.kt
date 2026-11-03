import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/** Пишет manifest.json варианта сборки (браузер × dev/release) в каталог расширения. */
abstract class GenerateManifestTask : DefaultTask() {

    @get:Input
    abstract val browser: Property<String>

    @get:Input
    abstract val dev: Property<Boolean>

    @get:Input
    abstract val version: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        val variant = Browser.values().firstOrNull { it.id == browser.get() }
            ?: throw IllegalArgumentException("Неизвестный браузер: ${browser.get()}")
        target.writeText(manifestJson(buildManifest(variant, dev.get(), version.get())))
    }
}
