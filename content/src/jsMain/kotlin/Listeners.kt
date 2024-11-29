
import PageType.MAIN
import PageType.PUBLICATIONS
import chrome.runtime.ExtensionMessageEvent
import chrome.runtime.MessageSender
import common.dInfo
import dataclasses.ServiceWorkerMessage
import publications.Publications

val serviceRequests = setOf("prozen-webrequest","prozen-mainpage-request")

fun backgroundListener(request: dynamic, sender: MessageSender, sendResponse: (Any) -> Unit) {
    try {
        val requestType = request.type as? String
        publisherId = request.publisherId as? String
        token = request.token as? String
        val pageType = getPageType()
        if (requestType in serviceRequests) {
            val serviceWorkerMessage = ServiceWorkerMessage(
                type = request.type as? String,
                url = request.url as? String,
                publicationIdAfter = request.publicationIdAfter as? String,
                publisherId = request.publisherId as? String,
                pageSize = (request.pageSize as? String)?.toIntOrNull(),
                types = request.types as? String,
                view = request.view as? String,
                query = request.query as? String,
                token = request.token as? String
            )

            console.dInfo("backgroundListener / serviceWorkerMessage: $serviceWorkerMessage")

            when (pageType) {
                MAIN -> {
                    processStudioCards(serviceWorkerMessage.pageSize ?: 0)
                }
                PUBLICATIONS -> {
                    processPublicationsCards(serviceWorkerMessage)
                }
                else -> console.dInfo("Unknown page type")
            }
        }
    } catch (e: Exception) {
        console.error("Error: ", e.message ?: "Unknown error")
    }
}

fun listenToRequests() {
    val onMessageEvent: ExtensionMessageEvent = chrome.runtime.onMessage
    if (onMessageEvent.hasListener(::backgroundListener)) {
        onMessageEvent.removeListener(::backgroundListener)
    }
    onMessageEvent.addListener(::backgroundListener)
}


fun processStudioCards(pageSize: Int) {
    Publications(request!!).processDashboardCards(pageSize)
}

fun processPublicationsCards(serviceWorkerMessage: ServiceWorkerMessage) {
    Publications(request!!).processPublicationsCards(serviceWorkerMessage)
}
