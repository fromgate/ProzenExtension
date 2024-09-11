import chrome.runtime.MessageSender
import chrome.runtime.ExtensionMessageEvent
import PageType.*
import dataclasses.ServiceMessage

val serviceRequests = setOf("prozen-webrequest","prozen-mainpage-request")

fun backgroundListener(request: dynamic, sender: MessageSender, sendResponse: (Any) -> Unit) {
    try {
        val requestType = request.type as? String
        publisherId = request.publisherId as? String
        token = request.token as? String
        val pageType = getPageType()
        if (requestType in serviceRequests) {
            val serviceMessage = ServiceMessage(
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

            when (pageType) {
                MAIN -> {
                    processDashboardCards(serviceMessage.pageSize ?: 0)
                }
                PUBLICATIONS -> {
                    processPublicationsCards(serviceMessage)
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

fun processPublicationsCards(serviceMessage: ServiceMessage) {
    console.log("processPublicationsCards with message: $serviceMessage")
}

