
import PageType.*
import common.Option
import common.getZenObject
import common.obj
import common.string
import dataclasses.ProzenData
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromDynamic
import org.w3c.dom.*
import org.w3c.dom.events.Event
import publication.Article
import publication.Brief
import publication.Shorts
import publication.Video
import requester.Requester
import requester.RequesterCached
import studio.Menu
import studio.Studio
import kotlin.js.json

var token: String? = null
var data: JsonObject? = null
var publisherId: String? = null
var requester: Requester? = null

var oldHref: String = window.location.href
var observerWindowLocationHref: MutationObserver? = null

fun injectCssAndScript() {
    window.removeEventListener("message", ::onProzenData)

    if (document.getElementById("prozen-css") == null) {
        val css = (document.createElement("link") as HTMLLinkElement).apply {
            rel = "stylesheet"
            type = "text/css"
            id = "prozen-css"
            href = chrome.runtime.getURL("css/prozen.css")
        }
        document.head?.appendChild(css)
    }

    if (document.getElementById("prozen-page-script") == null) {
        val script = (document.createElement("script") as HTMLScriptElement).apply {
            type = "text/javascript"
            id = "prozen-page-script"
            src = chrome.runtime.getURL("js/pageKt.js")
        }
        document.body?.appendChild(script)
    }

    window.addEventListener("message", ::onProzenData)
}

@OptIn(ExperimentalSerializationApi::class)
fun onProzenData(e: Event) {
val event = e as? MessageEvent ?: return

    if (event.source !is Window || event.source != window) return

    val prozenData = try {
        val eventData = event.data.asDynamic()
        if (eventData.type == "prozen-data") {
            Json.decodeFromDynamic<ProzenData>(event.data)
        } else return
    } catch (e: Exception) {
        null
    } ?: return

    if (prozenData.type == "prozen-data") {
        token = prozenData.text
        data = prozenData.jsonData
        publisherId = prozenData.jsonData?.obj("publisher")?.string("id")
    }

    if (publisherId == null) {
        publisherId = getZenObject()?.first
    }

    requester = RequesterCached(publisherId, token)

    val pageType = getPageType()

    val contentRunner: ContentRunner? = when (pageType) {
        ARTICLE -> {
            Article(requester!!, data!!)
        }

        BRIEF -> {
            Brief(requester!!, data!!)
        }

        VIDEO -> {
            Video(requester!!, data!!)
        }

        SHORT -> {
            Shorts(requester!!, data!!)
        }

        MAIN -> {
            registerObserverWindowsLocation()
            Studio(requester!!) // + Menu
        }

        PUBLICATIONS -> {
            registerObserverWindowsLocation()
            Menu(requester!!)
        }

        STATS, CAMPAIGNS, COMMENTS, MONEY -> {
            registerObserverWindowsLocation()
            Menu(requester!!)
        }

        else -> {
            null
        }
    }
    contentRunner?.run()
}

fun getPageType(): PageType {
    val path = window.location.pathname
    val pageType = when {
        path.startsWith("/profile/editor/") -> when {
            path.contains("/money/") -> MONEY
            path.endsWith("/publications") -> PUBLICATIONS
            path.endsWith("/edit") -> EDIT
            path.endsWith("/publications-stat") -> STATS
            path.endsWith("/campaigns/") -> CAMPAIGNS
            path.endsWith("/comments/") -> COMMENTS
            else -> MAIN
        }

        path.startsWith("/a/") -> ARTICLE
        path.startsWith("/b/") -> BRIEF
        path.startsWith("/video/") -> VIDEO
        path.startsWith("/shorts/") -> SHORT

        else -> UNKNOWN
    }
    return pageType
}

fun keepServiceWorkerAlive() {
    chrome.runtime.sendMessage(json("type" to "keepAlive"))
}


fun main() {
    Option.PROZEN.value().then { enabled ->
        if (enabled) {
            keepServiceWorkerAlive()
            window.setInterval({ keepServiceWorkerAlive() }, 25000)
            listenToRequests()
            injectCssAndScript()
        }
    }
}

fun registerObserverWindowsLocation() {
    observerWindowLocationHref?.disconnect()
    observerWindowLocationHref = MutationObserver { mutations, _ ->
        repeat(mutations.count()) {
            val newHref = document.location?.href?.split("?")?.first()
            if (oldHref != newHref && newHref != null) {
                oldHref = newHref
                sendProzenRequest()
            }
        }
    }

    val config = MutationObserverInit(
        childList = true,
        subtree = true
    )

    document.querySelector("body")?.let {
        observerWindowLocationHref?.observe(it, config)
    }
}

fun sendProzenRequest() {
    window.postMessage(
        json(
            "type" to "prozen-request"
        ), "*"
    )
}
