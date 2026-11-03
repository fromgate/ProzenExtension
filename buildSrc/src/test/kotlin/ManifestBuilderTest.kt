import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManifestBuilderTest {

    private fun permissions(manifest: kotlinx.serialization.json.JsonObject) =
        manifest["permissions"]!!.jsonArray.map { it.jsonPrimitive.content }

    private fun webAccessibleResources(manifest: kotlinx.serialization.json.JsonObject) =
        manifest["web_accessible_resources"]!!.jsonArray[0].jsonObject["resources"]!!.jsonArray
            .map { it.jsonPrimitive.content }

    @Test
    fun `chrome release manifest matches the old checked-in one`() {
        val manifest = buildManifest(Browser.CHROME, dev = false, version = "3.1.1")

        assertEquals(3, manifest["manifest_version"]!!.jsonPrimitive.int)
        assertEquals("__MSG_extName__", manifest["name"]!!.jsonPrimitive.content)
        assertEquals("__MSG_extDescription__", manifest["description"]!!.jsonPrimitive.content)
        assertEquals("3.1.1", manifest["version"]!!.jsonPrimitive.content)
        assertEquals("settings.html", manifest["options_page"]!!.jsonPrimitive.content)
        assertNull(manifest["browser_specific_settings"])
        assertEquals("js/serviceWorkerKt.js", manifest["background"]!!.jsonObject["service_worker"]!!.jsonPrimitive.content)
        assertEquals(listOf("storage", "webRequest"), permissions(manifest))
        assertEquals(
            listOf(
                "search.html", "stats.html", "status.html", "settings.html",
                "js/*.js", "js/*.js.map", "css/prozen.css", "img/*.png", "icons/*.png", "fonts/*.ttf",
            ),
            webAccessibleResources(manifest),
        )
    }

    @Test
    fun `chrome dev manifest has dev name and unlimitedStorage`() {
        val manifest = buildManifest(Browser.CHROME, dev = true, version = "3.1.1")

        assertEquals(DEV_MANIFEST_NAME, manifest["name"]!!.jsonPrimitive.content)
        assertEquals(listOf("unlimitedStorage", "storage", "webRequest"), permissions(manifest))
    }

    @Test
    fun `edge matches chrome`() {
        val chrome = buildManifest(Browser.CHROME, dev = false, version = "1.0.0")
        val edge = buildManifest(Browser.EDGE, dev = false, version = "1.0.0")

        assertEquals(chrome.minus("name") + ("name" to edge["name"]!!), edge)
    }

    @Test
    fun `firefox manifest uses gecko scripts and firefox description`() {
        val manifest = buildManifest(Browser.FIREFOX, dev = false, version = "3.1.1")

        // isFirefox() ищет слово "Firefox" в описании манифеста — см. common/CommonFunctions.kt.
        assertEquals("__MSG_extDescriptionFirefox__", manifest["description"]!!.jsonPrimitive.content)
        val gecko = manifest["browser_specific_settings"]!!.jsonObject["gecko"]!!.jsonObject
        assertEquals("{62e2b893-cd14-4019-8749-9ca43a1c66ac}", gecko["id"]!!.jsonPrimitive.content)
        assertEquals("115.0", gecko["strict_min_version"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("js/serviceWorkerKt.js"),
            manifest["background"]!!.jsonObject["scripts"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        // Исторически в firefox-манифесте нет ни карт исходников, ни шрифтов.
        assertFalse(webAccessibleResources(manifest).contains("js/*.js.map"))
        assertFalse(webAccessibleResources(manifest).contains("fonts/*.ttf"))
    }

    @Test
    fun `firefox dev manifest equals release`() {
        val release = buildManifest(Browser.FIREFOX, dev = false, version = "3.1.1")
        val dev = buildManifest(Browser.FIREFOX, dev = true, version = "3.1.1")

        assertEquals(release, dev)
    }

    @Test
    fun `content scripts target dzen editor pages`() {
        for (browser in Browser.values()) {
            val manifest = buildManifest(browser, dev = false, version = "1.0.0")
            val contentScript = manifest["content_scripts"]!!.jsonArray[0].jsonObject
            val matches = contentScript["matches"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertTrue(matches.contains("https://dzen.ru/profile/editor/*"))
            assertEquals("document_end", contentScript["run_at"]!!.jsonPrimitive.content)
            assertEquals(listOf("js/contentKt.js"), contentScript["js"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals("https://dzen.ru/", manifest["host_permissions"]!!.jsonArray[0].jsonPrimitive.content)
        }
    }
}
