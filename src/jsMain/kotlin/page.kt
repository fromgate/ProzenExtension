import kotlinx.browser.window
import org.w3c.dom.MessageEvent


fun sendProzenData() {
    val data = js("({})").unsafeCast<dynamic>()
    data.type = "prozen-data"
    data.text = window.asDynamic()._csrfToken
    data.jsonData = window.asDynamic()._data

    window.postMessage(data, "*")
}

fun main() {
    sendProzenData()

    window.addEventListener("message", { event ->
        val messageEvent = event.unsafeCast<MessageEvent>()
        if (messageEvent.source != window) return@addEventListener

        val data = messageEvent.data.asDynamic()
        if (data?.type == "prozen-request") {
            sendProzenData()
        }
    })
}


/*
sendProzenData();

window.addEventListener("message", event => {
    if (event.source !== window) {
        return;
    }
    if (event.data.type && (event.data.type === "prozen-request")) {
        sendProzenData();
    }
});

function sendProzenData() {
    const data = {
        type: "prozen-data",
        text: window._csrfToken,
        jsonData: window._data
    };
    window.postMessage(data, "*");
}
 */