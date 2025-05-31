package common

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.FOLLOW
import org.w3c.fetch.RequestInit
import org.w3c.fetch.RequestRedirect
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.json

suspend fun getCachedFinalUrl(url: String): String {
    val cachedUrl = getFromCache(url)
    if (cachedUrl != null && !cachedUrl.startsWith("https://dzen.ru/showcaptcha?")) {
        return cachedUrl
    }
    val finalUrl = getFinalUrl(url)
    saveToCache(url, finalUrl)
    return finalUrl
}

suspend fun getFinalUrl(url: String): String {
    val response = window.fetch(url, RequestInit(method = "GET", redirect = RequestRedirect.FOLLOW)).await()
    val redirectUrl = response.url
    if (redirectUrl.startsWith("https://dzen.ru/showcaptcha?")) {
        console.dLog("getFinalUrl: Dzen asked capcha for $url")
    }
    return redirectUrl
}

private suspend fun saveToCache(url: String, finalUrl: String) {
    if (finalUrl.startsWith("https://dzen.ru/showcaptcha?")) return
    suspendCoroutine<Unit> { continuation ->
        chrome.storage.local.set(json(url to finalUrl)) {
            continuation.resume(Unit)
        }
    }
}

private suspend fun getFromCache(url: String): String? {
    return suspendCoroutine { continuation ->
        chrome.storage.local.get(url) { result ->
            var resultUrl = result[url] as? String
            if (resultUrl != null && resultUrl.startsWith("https://dzen.ru/showcaptcha?")) {
                resultUrl = null
            }
            continuation.resume(resultUrl)
        }
    }
}