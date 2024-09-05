import kotlinx.browser.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.get

val OFF_BY_DEFAULT = listOf(
    "prozen-realtime-switch",
    "prozen-article-link-switch2",
    "prozen-promote-show"/*,"prozen-comments-widget-switch"*/
)
val switchIds = mutableListOf<String>();

fun initSwitches() {
    val switchElements = document.getElementsByClassName("switch-checkbox")
    for (i in 0 until switchElements.length) {
        val el = switchElements[i] as HTMLElement
        val switchId = el.id
        switchIds.add(switchId)
        document.getElementById(switchId)?.addEventListener("click", {
            onCheckboxClick(switchId)
        })
    }
}

fun onCheckboxClick(switchId: String) {
    setCheckbox(switchId, (document.getElementById(switchId) as HTMLInputElement).checked, true)
}

fun setCheckbox(switchId: String, switchState: Boolean, save: Boolean = false) {
    val switchEl = document.getElementById(switchId) as HTMLInputElement
    switchEl.checked = switchState
    val switchTextEl = document.getElementById("$switchId-text")!! as HTMLElement
    if (switchEl.hasAttribute("data-text-switch-on") && switchEl.hasAttribute("data-text-switch-off")) {
        switchTextEl.textContent =
            if (switchEl.checked) switchEl.getAttribute("data-text-switch-on") else switchEl.getAttribute("data-text-switch-off")
    }
    switchTextEl.style.fontWeight = if (switchState) "500" else "normal"
    if (save) {
        saveOptions()
    }
}

fun loadOptions() {
    Options.load().then { options ->
        switchIds.forEach { switchId ->
            var save = false
            val value = options[switchId] ?: {
                save = true
                Option.getValueOrDefault(switchId, true)
            }
            setCheckbox(switchId, value as Boolean, false)
            if (save) saveOptions()
        }
    }

    /* chrome.storage.local.get(switchIds.toTypedArray()) { options ->
        var save = false
        switchIds.forEach { switchId ->
            if (options[switchId] as Boolean) {
                setCheckbox(switchId, options[switchId] as Boolean)
            } else {
                setCheckbox(switchId, !OFF_BY_DEFAULT.contains(switchId))
                save = true
            }
        }
        if (save) {
            saveOptions()
        }
    } */
}

fun saveOptions() {
    val options = mutableMapOf<String, Boolean>()
    switchIds.forEach { switchId ->
        options[switchId] = (document.getElementById(switchId) as HTMLInputElement).checked
    }
    Options.save(options)
    /* val options = json()
    switchIds.forEach { switchId ->
        options[switchId] = (document.getElementById(switchId) as HTMLInputElement).checked
    }
    chrome.storage.local.set(options)*/
}

fun updateTranslation() {
    val langs = listOf("popupPrivacyPolicy")
    langs.forEach { key ->
        val element = document.getElementById(key)
        if (element != null) {
            element.textContent = chrome.i18n.getMessage(key)
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun showLastPost() {
    window.setTimeout({
        GlobalScope.launch {
            val channel = Channel("prodzn", true)
            val lastPost = channel.getLastPostCard()
            if (lastPost != null) {
                val a = document.getElementById("prozen-post-url")
                a?.setAttribute("href", "${lastPost.link}?utm_source=extension_popup")
                val img = document.getElementById("prozen-post-image")
                if (!lastPost.imageUrl.isNullOrEmpty()) {
                    img?.setAttribute("src", lastPost.imageUrl as String)
                } else {
                    val titleTextEl = document.getElementsByClassName("prozen-popup-title")[0]
                    titleTextEl.asDynamic().style["-webkit-line-clamp"] = "4"
                }
                val title = document.getElementById("prozen-post-title")
                title?.textContent = lastPost.title
            }
            (document.getElementById("prozen-image") as? HTMLElement)?.style?.visibility = "visible"
        }
    }, 0)
}

fun main() {
    val versionElement = document.getElementById("extver")
    val extensionVersion = chrome.runtime.getManifest().version
    versionElement?.textContent = versionElement?.textContent?.replace("1.0.0", extensionVersion)

    (document.getElementById("prozen-image") as? HTMLElement)?.style?.visibility = "hidden"

    updateTranslation()
    initSwitches()
    loadOptions()
    showLastPost()

}