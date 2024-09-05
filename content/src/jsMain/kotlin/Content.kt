import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.w3c.dom.HTMLLinkElement
import org.w3c.dom.HTMLScriptElement
import org.w3c.dom.MessageEvent
import org.w3c.dom.events.Event
import PageType.*

var token: String? = null
var data: JsonObject? = null
var publisherId: String? = null
var requester: Requester? = null


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

/*
function ReceiveProzenData(event) {
    if (event.source !== window) {
        return;
    }
    if (event.data.type && (event.data.type === "prozen-data")) {
        token = event.data.text;
        data = event.data.jsonData;
        publisherId = event.data.jsonData.publisher.id;
        const pageType = getPageType();
        if (pageType === "main" || pageType === "publications") {
            getBalanceAndMetriksId().then(result => {
                metriksId = result.metriksId;
                moneyTotal = result.total;
                moneySaldo = result.money;
                moneyDate = result.balanceDate;
                main(publisherId);
            });
        } else {
            main(publisherId);
        }
    }
}
 */

fun onProzenData(e: Event) {
    val event = e as? MessageEvent ?: return
    if (event.source != window) return

    try {
        val prozenData = Json.decodeFromString<ProzenData>(event.data.toString())
        // Проверяем тип сообщения
        if (prozenData.type == "prozen-data") {
            token = prozenData.text
            data = prozenData.jsonData
            publisherId = prozenData.jsonData.obj("publisher")?.string("id")
            if (token != null && publisherId != null) {
                requester = Requester(publisherId!!, token!!)
            }

            val pageType = getPageType()
            console.log(pageType)

            when (pageType) {
                MAIN, PUBLICATIONS -> {}
                ARTICLE, BRIEF, VIDEO, SHORT -> {}
                else -> {}
            }
            /*
            val pageType = getPageType()
            if (pageType == "main" || pageType == "publications") {
                getBalanceAndMetriksId().then { result ->
                    metriksId = result.metriksId
                    moneyTotal = result.total
                    moneySaldo = result.money
                    moneyDate = result.balanceDate

                    main(publisherId)
                }
            } else {
                main(publisherId)
            } */
        }
    } catch (e: Exception) {
        console.error("Error parsing message data: ${e.message}")
    }


}

fun getPageType(): PageType {
    val path = window.location.pathname
    return when {
        path.startsWith("/profile/editor/") -> when {
            path.contains("/money/") -> MONEY
            path.endsWith("/publications") -> PUBLICATIONS
            path.endsWith("/edit") -> EDIT
            path.endsWith("/publications-stat") -> STATS
            path.endsWith("/campaigns") -> CAMPAIGNS
            path.endsWith("/comments") -> COMMENTS
            else -> MAIN
        }

        path.startsWith("/a/") -> ARTICLE
        path.startsWith("/b/") -> BRIEF
        path.startsWith("/video/") -> VIDEO
        path.startsWith("/shorts/") -> SHORT

        else -> UNKNOWN
    }
}


fun main() {


}

@Serializable
data class ProzenData(
    val type: String,
    val text: String,
    val jsonData: JsonObject
)