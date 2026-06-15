
import common.Cache
import common.Option
import common.setVisualThemeOpt
import kotlinx.browser.document
import kotlinx.coroutines.*
import kotlinx.dom.clear
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import requester.Channel
import requester.Excerpt
import kotlin.js.Json
import kotlin.js.json
import kotlin.time.Duration.Companion.days


val cache = Cache("prozen-popup")

suspend fun createPopup() {
    val extensionEnabled = Option.PROZEN.isSet()

    val defaultExcerpt = Excerpt(
        "Расширенный редактор для Дзена",
        "",
        "https://dzen.ru/media/id/5a3def60e86a9e50b401ab4a/lichnaia-razrabotka-addon-dlia-brauzera-rasshirennyi-redaktor-dlia-iandeksdzen-5bfab21cdfc89b00aa85d9ac?utm_source=extension_popup",
        "img/link-image.png"
    )

    val cachedExcerpt = loadExcerpt()

    document.body?.append {
        div(classes = "prozen-popup-container") {

            val extensionVersion = chrome.runtime.getManifest().version
            // Титульный блок
            div(classes = "prozen-popup-header") {
                img(src = "icons/tab-icon-128.png", classes = "prozen-popup-icon") {}
                div(classes = "prozen-popup-title") {
                    +"Продзен v${extensionVersion}"
                }
            }

            // Карточка последней публикации
            div(classes = "prozen-popup-card") {
                id = "pozen-popup-card"
                a(
                    href = cachedExcerpt?.link ?: defaultExcerpt.link,
                    target = "_blank"
                ) {
                    div(classes = "prozen-popup-card-image") {
                        img(src = cachedExcerpt?.imageUrl ?: defaultExcerpt.imageUrl)
                    }
                    div(classes = "prozen-popup-card-title") {
                        +(cachedExcerpt?.title ?: defaultExcerpt.title!!)
                    }
                }
            }

            // Информационный блок о текущей статье
            div {
                id = "prozen-popup-info-block-container"
            }

            // Управляющая строка с кнопками
            div(classes = "prozen-popup-control-bar") {
                button(classes = "prozen-popup-toggle-button${" active".takeIf { extensionEnabled } ?: ""}") {
                    id = "toggle-extension"
                    +if (extensionEnabled) "Расширение включено" else "Расширение отключено"
                    onClickFunction = {
                        it.preventDefault()
                        val button = document.getElementById("toggle-extension") as HTMLButtonElement
                        button.classList.toggle("active")
                        val active = (button.classList.contains("active"))
                        button.textContent = if (active) "Расширение включено" else "Расширение отключено"
                        Option.PROZEN.set(active)
                    }
                }
                button(classes = "prozen-popup-settings-button") {
                    +"Настройки"
                    onClickFunction = {
                        it.preventDefault()
                        chrome.runtime.openOptionsPage()
                    }
                }
            }

            // Футер с ссылками
            div(classes = "prozen-popup-footer") {
                div(classes = "prozen-popup-footer-row") {
                    a(href = "https://t.me/+jgjgYMVg2gY0ODVi", target = "_blank") { +"Продзен в Telegram" }
                    a(href = "https://prozen.ru", target = "_blank") { +"prozen.ru" }
                    a(href = "https://dzen.ru/prodzn", target = "_blank") { +"Продзен в Дзене" }
                }
                div(classes = "prozen-popup-footer-secondary") {
                    a(
                        href = "https://prozen.ru/prozen-extension-policy/?utm_source=ext_policy",
                        target = "_blank"
                    ) { +"Политика безопасности" }
                    span { +" | " }
                    a(href = "https://donate.stream/prodzen", target = "_blank") { +"Поддержать разработчика" }
                }
            }
        }
    }
}

private suspend fun saveExcerpt(excerpt: Excerpt) {
    val excerptData = json(
        "title" to excerpt.title,
        "link" to excerpt.link,
        "text" to excerpt.text,
        "imageUrl" to excerpt.imageUrl
    )
    cache.saveToCache("last-post", excerptData, cache.calcExpirationTime(1.days))
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
private suspend fun loadExcerpt(): Excerpt? {
    val excerptData = cache.getFromCache("last-post") as? Json ?: return null
    val title = excerptData["title"] as String?
    val link = excerptData["link"] as String?
    val text = excerptData["text"] as String?
    val imageUrl = excerptData["imageUrl"] as String?
    return Excerpt(title, text, link, imageUrl)
}

suspend fun setLastPost() {
    val cardItem = document.getElementById("pozen-popup-card") as? HTMLElement
    val channel = Channel("prodzn", true)
    val lastPost = channel.getLastPostCard("smart_crop_516x290")
    if (cardItem != null && lastPost != null) {

        saveExcerpt(lastPost)

        cardItem.clear()
        cardItem.append {
            a(
                href = "${lastPost.link}?utm_source=extension_popup",
                target = "_blank"
            ) {
                div(classes = "prozen-popup-card-image") {
                    img(src = if (lastPost.imageUrl.isNullOrEmpty()) "img/link-image.png" else lastPost.imageUrl)
                }
                div(classes = "prozen-popup-card-title") {
                    lastPost.title?.let { +it }
                }
            }
        }

    }
}

/*
fun showInfo() {
    document.getElementById("prozen-popup-info-block-container")?.append {
        div(classes = "prozen-popup-info-block") {
            div(classes = "prozen-popup-info-title") {
                +"Название текущей публикации"
            }
            div(classes = "prozen-popup-info-stats") {
                +"Просмотры: 1000 | Дочитывания: 800 | Лайки: 150 | Комментарии: 10"
            }
            div(classes = "prozen-popup-info-indicators") {
                span { +"🤖" }
                span { +"🪙" }
                span { +"📺" }
            }
            div(classes = "prozen-popup-info-tags") {
                span(classes = "prozen-popup-tag") { +"экономика" }
                span(classes = "prozen-popup-tag") { +"финансы" }
                span(classes = "prozen-popup-tag") { +"технологии" }
                span(classes = "prozen-popup-tag") { +"бьюти" }
            }
        }
    }
} */

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    GlobalScope.launch {
        setVisualThemeOpt()
        createPopup()
        listOf (
            async { setLastPost() },
            async { showPublicationInfo() }
        ).awaitAll()
    }
}