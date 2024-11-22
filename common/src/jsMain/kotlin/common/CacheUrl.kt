package common

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.FOLLOW
import org.w3c.fetch.RequestInit
import org.w3c.fetch.RequestRedirect
import kotlin.js.Promise
import kotlin.js.json

suspend fun getCachedFinalUrl(url: String): String {
    val cachedUrl = getFromCache(url)
    if (cachedUrl != null) {
        return cachedUrl
    }
    val finalUrl = getFinalUrl(url)
    saveToCache(url, finalUrl)
    return finalUrl
}

suspend fun getFinalUrl(url: String): String {
    val response = window.fetch(url, RequestInit(method = "HEAD", redirect = RequestRedirect.FOLLOW)).await()
    return response.url
}

private suspend fun saveToCache(url: String, finalUrl: String) {
    return Promise<Unit> { resolve, _ ->
        chrome.storage.local.set(json(url to finalUrl)) {
            resolve(Unit)
        }
    }.await()
}

private suspend fun getFromCache(url: String): String? {
    return Promise<String?> { resolve, _ ->
        chrome.storage.local.get(url) { result ->
            resolve(result[url] as? String)
        }
    }.await()
}

