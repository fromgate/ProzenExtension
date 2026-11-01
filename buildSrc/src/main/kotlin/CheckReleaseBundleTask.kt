import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Проверяет, что в release-бандле нет dev-кода.
 *
 * Проверки намеренно грубые (строки, имена файлов, разрешения): они должны ловить
 * случайный импорт devtools, а не доказывать корректность сборки.
 */
abstract class CheckReleaseBundleTask : DefaultTask() {

    /** Каталог собранного расширения. `@Internal`: в dev его может не быть, нужен понятный отказ. */
    @get:Internal
    abstract val extensionDir: DirectoryProperty

    @get:Input
    abstract val dev: Property<Boolean>

    /** Файлы, которых в release быть не должно (относительно каталога расширения). */
    @get:Input
    abstract val forbiddenFiles: ListProperty<String>

    /** Строки, которых не должно быть в JS и манифесте. */
    @get:Input
    abstract val forbiddenMarkers: ListProperty<String>

    /** Разрешения, которых не должно быть в release-манифесте. */
    @get:Input
    abstract val forbiddenPermissions: ListProperty<String>

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

        forbiddenFiles.get().forEach { relative ->
            if (File(root, relative).exists()) {
                problems += "найден файл $relative"
            }
        }

        root.walkTopDown()
            .filter { it.isFile && "devtools" in it.name.lowercase() }
            .forEach { problems += "найден devtools-файл ${it.relativeTo(root)}" }

        root.walkTopDown()
            .filter { it.isFile && it.extension == "map" }
            .forEach { problems += "найден source map ${it.relativeTo(root)}" }

        val markers = forbiddenMarkers.get()
        val jsDir = File(root, "js")
        jsDir.listFiles { file -> file.isFile && file.name.endsWith(".js") }
            .orEmpty()
            .sortedBy { it.name }
            .forEach { file ->
                val text = file.readText()
                markers.forEach { marker ->
                    if (marker in text) {
                        problems += "${file.relativeTo(root)}: найдена dev-строка \"$marker\""
                    }
                }
            }

        checkManifest(root, problems)

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Release-бандл содержит dev-код или dev-разрешения:\n" +
                    problems.joinToString("\n") { "  - $it" }
            )
        }

        logger.lifecycle("Release-бандл чист: devtools, dev-строк и dev-разрешений нет")
    }

    private fun checkManifest(root: File, problems: MutableList<String>) {
        val manifestFile = File(root, "manifest.json")
        if (!manifestFile.isFile) {
            problems += "нет manifest.json"
            return
        }

        val manifest = try {
            Json.parseToJsonElement(manifestFile.readText()).jsonObject
        } catch (error: Exception) {
            problems += "manifest.json не разбирается: ${error.message}"
            return
        }

        val name = manifest["name"]?.jsonPrimitive?.contentOrNull
        if (name == "Prozen dev") {
            problems += "manifest.json: dev-имя \"$name\""
        }

        val permissions = manifest["permissions"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()

        forbiddenPermissions.get().forEach { permission ->
            if (permission in permissions) {
                problems += "manifest.json: dev-разрешение \"$permission\""
            }
        }
    }
}
