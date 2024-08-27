import kotlinx.browser.window
import org.w3c.dom.MessageEvent

fun sendProzenData() {
    val data = js("({})").unsafeCast<dynamic>()
    data.type = "prozen-data"
    data.text = window.asDynamic()._csrfToken
    data.jsonData = window.asDynamic()._data

    console.log("page.kt -  sednProzenData")
    window.postMessage(data, "*")
}

fun main() {
    console.log("page.kt")
    sendProzenData()

    window.addEventListener("message", { event ->
        console.log("page.kt -  event")
        val messageEvent = event as MessageEvent
        if (messageEvent.source != window) return@addEventListener

        val data = messageEvent.data.asDynamic()
        if (data?.type == "prozen-request") {
            sendProzenData()
        }
    })
}
