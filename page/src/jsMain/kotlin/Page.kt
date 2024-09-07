import kotlinx.browser.window
import org.w3c.dom.Window
import org.w3c.dom.get
import kotlin.js.Json
import kotlin.js.json

fun main() {
    sendProzenData()
    window.onmessage = { event ->
        if (event.source is Window && event.source == window) {
            val data = event.data.unsafeCast<Json>()
            if (data["type"] != null && data["type"] == "prozen-request") {
                sendProzenData()
            }
        }
    }
}

fun sendProzenData() {
    val data = json(
        "type" to "prozen-data",
        "text" to (window["_csrfToken"] ?: ""),
        "jsonData" to (window["_data"] ?: "{}")
    )
    window.postMessage(data, "*")
}