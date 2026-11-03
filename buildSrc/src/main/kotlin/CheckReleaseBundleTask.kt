import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipFile

/**
 * Проверяет, что в release-сборке нет dev-кода.
 *
 * Каталог [extensionDir] содержит варианты `prozen-<browser>`; варианты `*-dev`
 * (если собраны тем же прогоном — обычно их нет, т.к. dev и release собираются
 * разными процессами) пропускаются.
 *
 * Проверки намеренно грубые (строки, имена файлов, разрешения): они должны ловить
 * случайный импорт devtools, а не доказывать корректность сборки.
 */
abstract class CheckReleaseBundleTask : DefaultTask() {

    /** Корневой каталог собранных расширений (варианты `prozen-<browser>`). */
    @get:InputDirectory
    abstract val extensionDir: DirectoryProperty

    @get:Input
    abstract val dev: Property<Boolean>

    /** Файлы, которых в release быть не должно (относительно каталога варианта). */
    @get:Input
    abstract val forbiddenFiles: ListProperty<String>

    /** Строки, которых не должно быть в JS и манифесте. */
    @get:Input
    abstract val forbiddenMarkers: ListProperty<String>

    /** Разрешения, которых не должно быть в release-манифесте. */
    @get:Input
    abstract val forbiddenPermissions: ListProperty<String>

    /** Zip с исходниками (для Firefox AMO). Проверяется только в release. */
    @get:Optional
    @get:InputFile
    abstract val sourcesZip: RegularFileProperty

    /** Вхождения архива исходников, которых в нём быть не должно. */
    @get:Input
    abstract val forbiddenZipEntries: ListProperty<String>

    @TaskAction
    fun check() {
        if (dev.get()) {
            throw GradleException(
                "checkReleaseBundle проверяет release-сборку. " +
                    "Запустите её с -Pprozen.dev=false или через ./gradlew buildVersion."
            )
        }

        val root = extensionDir.get().asFile
        if (!root.isDirectory) {
            throw GradleException("Нет каталога release-сборки: ${root.absolutePath}")
        }

        val problems = mutableListOf<String>()

        val variants = root.listFiles { file -> file.isDirectory && !file.name.endsWith("-dev") }
            .orEmpty()
            .sortedBy { it.name }
        if (variants.isEmpty()) {
            problems += "в ${root.absolutePath} нет release-вариантов"
        }

        variants.forEach { variant -> checkVariant(variant, problems) }

        checkSourcesZip(problems)

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Release-сборка содержит dev-код или dev-разрешения:\n" +
                    problems.joinToString("\n") { "  - $it" }
            )
        }

        logger.lifecycle("Release-бандл чист: devtools, dev-строк и dev-разрешений нет")
    }

    private fun checkVariant(variant: File, problems: MutableList<String>) {
        forbiddenFiles.get().forEach { relative ->
            if (File(variant, relative).exists()) {
                problems += "${variant.name}: найден файл $relative"
            }
        }

        variant.walkTopDown()
            .filter { it.isFile && "devtools" in it.name.lowercase() }
            .forEach { problems += "${variant.name}: найден devtools-файл ${it.relativeTo(variant)}" }

        variant.walkTopDown()
            .filter { it.isFile && it.extension == "map" }
            .forEach { problems += "${variant.name}: найден source map ${it.relativeTo(variant)}" }

        val markers = forbiddenMarkers.get()
        val jsDir = File(variant, "js")
        jsDir.listFiles { file -> file.isFile && file.name.endsWith(".js") }
            .orEmpty()
            .sortedBy { it.name }
            .forEach { file ->
                val text = file.readText()
                markers.forEach { marker ->
                    if (marker in text) {
                        problems += "${variant.name}: ${file.relativeTo(variant)}: найдена dev-строка \"$marker\""
                    }
                }
            }

        checkManifest(variant, problems)
    }

    private fun checkManifest(variant: File, problems: MutableList<String>) {
        val manifestFile = File(variant, "manifest.json")
        if (!manifestFile.isFile) {
            problems += "${variant.name}: нет manifest.json"
            return
        }

        val manifest = try {
            Json.parseToJsonElement(manifestFile.readText()).jsonObject
        } catch (error: Exception) {
            problems += "${variant.name}: manifest.json не разбирается: ${error.message}"
            return
        }

        val name = manifest["name"]?.jsonPrimitive?.contentOrNull
        if (name == DEV_MANIFEST_NAME) {
            problems += "${variant.name}: manifest.json: dev-имя \"$name\""
        }

        val permissions = manifest["permissions"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()

        forbiddenPermissions.get().forEach { permission ->
            if (permission in permissions) {
                problems += "${variant.name}: manifest.json: dev-разрешение \"$permission\""
            }
        }
    }

    private fun checkSourcesZip(problems: MutableList<String>) {
        if (!sourcesZip.isPresent) {
            return
        }
        val zipFile = sourcesZip.get().asFile
        val label = zipFile.name
        if (!zipFile.isFile) {
            problems += "нет архива исходников: ${zipFile.absolutePath}"
            return
        }

        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            forbiddenZipEntries.get().forEach { forbidden ->
                val found = entries.firstOrNull { it == forbidden || it.startsWith("$forbidden/") || it.startsWith(forbidden) }
                if (found != null) {
                    problems += "$label: запрещённое вхождение \"$found\""
                }
            }
            // Сборка release из исходников обязана работать (проверяется распаковкой),
            // поэтому каркас проекта и release-заглушки devtools должны присутствовать.
            listOf("settings.gradle.kts", "devtools/src/release/").forEach { required ->
                if (entries.none { it.startsWith(required) }) {
                    problems += "$label: нет вхождения \"$required\""
                }
            }
        }
    }
}
