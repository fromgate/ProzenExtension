package common

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.fetch.FOLLOW
import org.w3c.fetch.RequestInit
import org.w3c.fetch.RequestRedirect
import kotlin.coroutines.resume
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

/* Отложено до лучших времён, пока не заработает стандартный RequestInit */
suspend fun getFinalUrlOld(url: String): String {
    val response = window.fetch(url, RequestInit(method = "GET", redirect = RequestRedirect.FOLLOW)).await()
    val redirectUrl = response.url
    if (redirectUrl.startsWith("https://dzen.ru/showcaptcha?")) {
        console.dLog("getFinalUrl: Dzen asked capcha for $url")
    }
    return redirectUrl
}

suspend fun getFinalUrl(url: String): String {
    try {
        val response = window.fetch(
            url,
            jso {
                method = "GET"
                redirect = RequestRedirect.FOLLOW
            }
        ).await()
        val redirectUrl = response.url
        if (redirectUrl.startsWith("https://dzen.ru/showcaptcha?")) {
            console.log("getFinalUrl: Dzen asked capcha for $url")
        }
        return redirectUrl
    } catch (e: Exception) {
        console.log("Failed getting finalUrl for $url")
        console.log (e.stackTraceToString())
    }
    return url
}

private suspend fun saveToCache(url: String, finalUrl: String) {
    if (finalUrl.startsWith("https://dzen.ru/showcaptcha?")) return
    suspendCancellableCoroutine<Unit> { continuation ->
        chrome.storage.local.set(json(url to finalUrl)) {
            continuation.resume(Unit)
        }
    }
}

private suspend fun getFromCache(url: String): String? {
    return suspendCancellableCoroutine { continuation ->
        chrome.storage.local.get(url) { result ->
            var resultUrl = result[url] as? String
            if (resultUrl != null && resultUrl.startsWith("https://dzen.ru/showcaptcha?")) {
                resultUrl = null
            }
            continuation.resume(resultUrl)
        }
    }
}