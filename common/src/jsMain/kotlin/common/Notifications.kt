package common

import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.dom.create
import kotlinx.html.img

val scope = MainScope()

fun showNotification(
    message: String,
    logoUrl: String = chrome.runtime.getURL("img/toast-logo.png"),
    timeout: Long = 3000
) {
    val notification = document.create.div {
        classes = setOf("prozen-notification", "prozen-fade-in")

        img(src = logoUrl, alt = "👍") {
            classes = setOf("prozen-logo")
        }

        div {
            +message
        }
    }

    document.body?.appendChild(notification)

    scope.launch {
        delay(timeout)
        notification.classList.remove("prozen-fade-in")
        notification.classList.add("prozen-fade-out")

        delay(500)
        document.body?.removeChild(notification)
    }
}
