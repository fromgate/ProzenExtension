import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Целевой браузер расширения. Chrome и Edge сейчас идентичны, но собираются отдельными артефактами. */
enum class Browser(val id: String) {
    CHROME("chrome"),
    EDGE("edge"),
    FIREFOX("firefox"),
}

/**
 * Генерирует manifest.json для варианта сборки.
 *
 * Содержание совпадает с прежними checked-in манифестами:
 * - firefox отличается описанием (`__MSG_extDescriptionFirefox__` — по нему `isFirefox()`
 *   определяет браузер в рантайме), блоком gecko, фоном `background.scripts` и урезанным
 *   списком web_accessible_resources;
 * - dev-вариант существует только для chrome/edge: имя [DEV_MANIFEST_NAME], чтобы
 *   dev-расширение не спутать с обычным, и `unlimitedStorage` (квота storage.local в Chrome
 *   без него — 10 МБ, а dev-расхождения могут быть объёмными). Firefox-dev использует
 *   обычный манифест, отличие — только в JS и имени архива.
 */
fun buildManifest(browser: Browser, dev: Boolean, version: String): JsonObject = buildJsonObject {
    put("manifest_version", 3)
    put("name", if (dev && browser != Browser.FIREFOX) DEV_MANIFEST_NAME else "__MSG_extName__")
    put(
        "description",
        if (browser == Browser.FIREFOX) "__MSG_extDescriptionFirefox__" else "__MSG_extDescription__"
    )
    put("version", version)
    put("author", "fromgate, prozen.ru")
    put("default_locale", "ru")
    if (browser == Browser.FIREFOX) {
        put("browser_specific_settings", buildJsonObject {
            put("gecko", buildJsonObject {
                put("id", "{62e2b893-cd14-4019-8749-9ca43a1c66ac}")
                put("strict_min_version", "115.0")
            })
        })
    }
    put("options_page", "settings.html")
    put("action", buildJsonObject {
        put("default_icon", buildJsonObject {
            put("19", "icons/tab-icon-19.png")
            put("38", "icons/tab-icon-38.png")
        })
        put("default_title", "__MSG_extName__")
        put("default_popup", "popup.html")
    })
    put("icons", buildJsonObject {
        put("16", "icons/tab-icon-16.png")
        put("48", "icons/tab-icon-48.png")
        put("128", "icons/tab-icon-128.png")
    })
    put("web_accessible_resources", JsonArray(listOf(buildJsonObject {
        put("resources", JsonArray(webAccessibleResources(browser).map { JsonPrimitive(it) }))
        put("matches", JsonArray(listOf(JsonPrimitive("https://dzen.ru/*"))))
    })))
    put("background", if (browser == Browser.FIREFOX) {
        buildJsonObject {
            put("scripts", JsonArray(listOf(JsonPrimitive("js/serviceWorkerKt.js"))))
        }
    } else {
        buildJsonObject {
            put("service_worker", "js/serviceWorkerKt.js")
        }
    })
    put("content_scripts", JsonArray(listOf(buildJsonObject {
        put("js", JsonArray(listOf(JsonPrimitive("js/contentKt.js"))))
        put("matches", JsonArray(listOf(
            JsonPrimitive("https://dzen.ru/profile/editor/*"),
            JsonPrimitive("https://dzen.ru/a/*"),
            JsonPrimitive("https://dzen.ru/b/*"),
            JsonPrimitive("https://dzen.ru/video/*"),
            JsonPrimitive("https://dzen.ru/shorts/*"),
        )))
        put("run_at", "document_end")
    })))
    put("permissions", JsonArray(permissions(dev && browser != Browser.FIREFOX).map { JsonPrimitive(it) }))
    put("host_permissions", JsonArray(listOf(JsonPrimitive("https://dzen.ru/"))))
}

const val DEV_MANIFEST_NAME = "Prozen dev"

private fun webAccessibleResources(browser: Browser): List<String> {
    val resources = mutableListOf(
        "search.html",
        "stats.html",
        "status.html",
        "settings.html",
        "js/*.js",
    )
    if (browser != Browser.FIREFOX) {
        // В firefox-манифесте этих записей исторически нет — не добавляем без необходимости.
        resources += "js/*.js.map"
    }
    resources += listOf("css/prozen.css", "img/*.png", "icons/*.png")
    if (browser != Browser.FIREFOX) {
        resources += "fonts/*.ttf"
    }
    return resources
}

private fun permissions(dev: Boolean): List<String> =
    if (dev) listOf("unlimitedStorage", "storage", "webRequest") else listOf("storage", "webRequest")

fun manifestJson(manifest: JsonObject): String =
    Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), manifest)
