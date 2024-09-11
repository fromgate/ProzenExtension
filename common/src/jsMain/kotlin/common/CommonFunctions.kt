package common

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLMetaElement
import org.w3c.dom.asList
import org.w3c.dom.parsing.DOMParser
import kotlin.js.Promise


fun getZenObject(): Pair<String, String>? {
    val content = document.head
        ?.querySelector("meta[property=zen_object_id][content]")
        ?.getAttribute("content")
    return content?.split(":", limit = 2)?.let { if (it.size == 2) it[0] to it[1] else null }
}

fun checkNoIndex(): Boolean {
    return document.querySelectorAll("meta[name=robots]")
        .asList()
        .filterIsInstance<HTMLMetaElement>()
        .any { it.content.contains("noindex") }
}

fun zenReaderUrl(channelUrl: String): String {
    val id = channelUrl.removePrefix("https://dzen.ru/")
        .replace("id/", "id-")
        .replace(".", "-")
    return "https://t.me/zenreaderbot?start=$id"
}

suspend fun checkNoIndexUrl(url: String): Boolean {
    val client = HttpClient(Js)
    return try {
        val response = client.get(url)
        val responseText = response.bodyAsText()
        val document = DOMParser().parseFromString(responseText, "text/html")
        val metas = document.getElementsByTagName("meta")
        metas.asList()
            .map { it as HTMLMetaElement }
            .any { meta ->
                val propertyAttr = meta.getAttribute("property")
                (meta.name == "robots" || propertyAttr == "robots") && meta.content.contains("noindex")
            }
    } catch (e: Exception) {
        console.error("Error fetching or processing URL: $e")
        false
    } finally {
        client.close()
    }
}

fun shortUrl(publisherId: String?, publicationId: String?): String {
    var url = window.location.href.split("?")[0].split("#")[0]
    url = url.substring(0, url.lastIndexOf("/")) + "/" + url.substring(url.lastIndexOf("-") + 1)

    if (publisherId != null) {
        val urlParts = url.split("/")
        val id = publicationId ?: urlParts.last()
        url = "https://dzen.ru/media/id/$publisherId/$id"
    }

    return url
}

fun maxOfNullable(a: Int?, b: Int?): Int? {
    return maxOf(a ?: Int.MIN_VALUE, b ?: Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
}

@OptIn(DelicateCoroutinesApi::class)
fun <T> runPromise(block: suspend () -> T): Promise<T> = GlobalScope.promise {
    block()
}

fun copyTextToClipboard(text: String) {
    window.navigator.clipboard.writeText(text)
}

fun updateTranslations() {
    document.querySelectorAll("[data-i18n]")
        .asList()
        .forEach {
            val element = it as HTMLElement
            element.getAttribute("data-i18n")?.let { langKey ->
                element.innerText = chrome.i18n.getMessage(langKey)
            }
        }
}


