package common

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.w3c.dom.HTMLMetaElement
import org.w3c.dom.asList
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