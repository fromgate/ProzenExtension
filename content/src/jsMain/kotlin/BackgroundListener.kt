import chrome.runtime.MessageSender
import chrome.runtime.ExtensionMessageEvent
import PageType.*

val serviceRequests = setOf("prozen-webrequest","prozen-mainpage-request")

fun backgroundListener(request: dynamic, sender: MessageSender, sendResponse: (Any) -> Unit) {
    try {
        val requestType = request.type as? String
        publisherId = request.publisherId as? String
        token = request.token as? String
        val pageType = getPageType()
        if (requestType in serviceRequests) {
            when (pageType) {
                MAIN -> {
                    val pageSize = (request.pageSize as? String)?.toIntOrNull() ?: 0
                    processDashboardCards(pageSize)
                }
                PUBLICATIONS -> {
                    processPublicationsCards(request)
                }
                else -> console.log("Unknown page type")
            }
        }
    } catch (e: Exception) {
        console.log("Error: ", e.message ?: "Unknown error")
    }
}

fun listenToRequests() {
    val onMessageEvent: ExtensionMessageEvent = chrome.runtime.onMessage
    if (onMessageEvent.hasListener(::backgroundListener)) {
        onMessageEvent.removeListener(::backgroundListener)
    }
    onMessageEvent.addListener(::backgroundListener)
}


fun processDashboardCards(pageSize: Int) {
    console.log("processDashboardCards")

}

fun processPublicationsCards(request: dynamic) {
    console.log("processPublicationsCards")
}

