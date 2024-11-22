package common

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
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

suspend fun getPageFromUrl(url: String): String? {
    return try {
        val response = window.fetch(url).await()
        if (response.ok) {
            response.text().await()
        } else {
            console.error("Failed to fetch URL: ${response.statusText}")
            null
        }
    } catch (e: Exception) {
        console.error("Error fetching URL: $e")
        null
    }
}

suspend fun checkNoIndexUrl(url: String): Boolean {
    return try {
        val responseText = getPageFromUrl(url) ?: return false
        val document = DOMParser().parseFromString(responseText, "text/html")
        val metas = document.getElementsByTagName("meta")
        metas.asList()
            .mapNotNull { it as? HTMLMetaElement }
            .any { meta ->
                val propertyAttr = meta.getAttribute("property")
                (meta.name == "robots" || propertyAttr == "robots") && meta.content.contains("noindex")
            }
    } catch (e: Throwable) {
        console.error("Error processing URL: $e")
        false
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

fun paucalYear(num: Int): String {
    return paucal(num, "год", "года", "лет")
}

fun paucalMonth(num: Int): String {
    return paucal(num, "месяц", "месяца", "месяцев")
}

fun paucalDay(num: Int): String {
    return paucal(num, "день", "дня", "дней")
}

/**
 * Возвращает строку, представляющую числительное с правильной формой существительного.
 *
 * @param num Число, для которого требуется получить правильную форму существительного.
 * @param p1 Форма существительного для числа 1 (например, "год").
 * @param p234 Форма существительного для чисел, оканчивающихся на 2, 3, 4 (например, "года").
 * @param p Общая форма существительного для всех остальных случаев (например, "лет").
 * @return Строка в формате "{num} {правильная форма существительного}".
 *
 * Пример:
 * - paucal(1, "год", "года", "лет") -> "1 год"
 * - paucal(2, "год", "года", "лет") -> "2 года"
 * - paucal(5, "год", "года", "лет") -> "5 лет"
 */
fun paucal(num: Int, p1: String, p234: String, p: String): String {
    val numStr = num.format()
    val n = num % 100
    return when {
        n in 11..19 -> "$numStr $p"
        else -> when (num % 10) {
            1 -> "$numStr $p1"
            2, 3, 4 -> "$numStr $p234"
            else -> "$numStr $p"
        }
    }
}

fun Int.paucal(p1: String, p234: String, p: String): String {
    return common.paucal(this, p1, p234, p)
}

fun convertDzenUrlToOk(dzenUrl: String): String? {
    val regex = Regex("""https://dzen\.ru/(a|b|video/watch|shorts)/([^?/]+)""")
    val match = regex.find(dzenUrl) ?: return null
    val type = match.groupValues[1]
    if (type != "a") return null
    val id = match.groupValues[2]
    return "https://ok.ru/dzen/article/$id"
}