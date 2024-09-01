import chrome.webRequest.RequestFilter
import chrome.webRequest.WebRequestHeadersDetails
import org.w3c.dom.url.URLSearchParams
import kotlin.js.Json
import kotlin.js.json

fun isProzenEnabled(callback: (Boolean) -> Unit) {
    chrome.storage.local.get(arrayOf("prozen-switch")) { options ->
        val isEnabled = options["prozen-switch"] as? Boolean ?: true
        callback(isEnabled)
    }
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
fun registerWebRequestListener() {
    val filter = js(
        "{ urls: [\"https://dzen.ru/editor-api/v2/get-publications-by-filter?group=published&publisherId=*\"] }"
    ) as RequestFilter
    chrome.webRequest.onBeforeSendHeaders.addListener({ details ->
        handleRequest(details, "prozen-webrequest")
    }, filter, arrayOf("requestHeaders"))
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
fun registerMainPageRequestListener() {
    val filter = js("{ urls: [\"https://dzen.ru/editor-api/v3/publications?*\"] }") as RequestFilter
    chrome.webRequest.onBeforeSendHeaders.addListener({ details ->
        handleRequest(details, "prozen-mainpage-request")
    }, filter, arrayOf("requestHeaders"))
}

fun handleRequest(details: WebRequestHeadersDetails, messageType: String) {
    var token: String? = null
    var prozenRequest = false
    val urlParams = URLSearchParams(details.url)
    details.requestHeaders?.forEach { header ->
        when (header.name) {
            "X-Csrf-Token" -> token = header.value
            "X-Prozen-Request" -> prozenRequest = true
        }
    }

    if (!prozenRequest) {
        chrome.tabs.sendMessage(
            details.tabId as Int, json(
                "type" to messageType,
                "url" to details.url,
                "publicationIdAfter" to urlParams.get("publicationIdAfter"),
                "publisherId" to urlParams.get("publisherId"),
                "pageSize" to urlParams.get("pageSize"),
                "types" to urlParams.get("types"),
                "view" to urlParams.get("view"),
                "query" to urlParams.get("query"),
                "token" to token
            )
        )
    }
}

fun main() {
    isProzenEnabled { enabled ->
        if (enabled) {
            registerWebRequestListener()
            registerMainPageRequestListener()
        }
    }
    chrome.runtime.onMessage.addListener { message, _, sendResponse ->
        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        if ((message as? Json)?.get("type") == "keepAlive") {
            sendResponse(json("status" to "Prozen Service worker is active"))
        }
        true
    }
}