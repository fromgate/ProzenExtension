import common.M
import common.Option
import common.format
import common.pageanalyzer.PageContext
import common.pageanalyzer.Thematic
import common.pageanalyzer.TypeCheck
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.html.div
import kotlinx.html.dom.append
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import kotlin.js.json


val mainScope = MainScope()

suspend fun showPublicationInfo() {
    if (Option.POPUP_INFO.isSet()) {

    val queryInfo = json(
        "active" to true,
        "currentWindow" to true
    )
    chrome.tabs.query(queryInfo) {
        val activeTab = it.firstOrNull()
        if (activeTab != null && activeTab.url?.matches(Regex("https://dzen\\.ru/([ab]|video|shorts)/.+")) == true) {
            mainScope.launch {
                val result = PageContext.analyzePage(activeTab.url!!)
                showInfo(result)
            }
        }
    }
        }
}

fun showInfo(info: Pair<PageContext, Map<TypeCheck, Any>>) {
    document.getElementById("prozen-popup-info-block-container")?.append {
        div(classes = "prozen-popup-info-block") {
            div(classes = "prozen-popup-info-title") {
                +info.first.title
            }

            val viewPair = getViewPair (info.first.views, info.first.viewsTillEnd)

            div(classes = "prozen-popup-info-stats") {
                viewPair.first?.let {
                    div("prozen-popup-stats-item") {
                        title = M.publicationViews
                        span ("publication_icon_views_2 prozen-popup-stats-item-icon")
                        +it.format()
                    }
                }

                viewPair.second?.let {
                    div("prozen-popup-stats-item") {
                        title = M.publicationFullViews
                        span ("publication_icon_full_views prozen-popup-stats-item-icon")
                        +it.format()
                    }
                }

                info.first.likes?.let {
                    div("prozen-popup-stats-item") {
                        title = M.publicationLikes
                        span ("publication_icon_likes prozen-popup-stats-item-icon")
                        +it.format()
                    }

                }
                info.first.comments?.let {
                    div("prozen-popup-stats-item") {
                        title = M.publicationComments
                        span ("publication_icon_comments prozen-popup-stats-item-icon")
                        +it.format()
                    }
                }
                val noindex = info.second[TypeCheck.NO_INDEX] as? Boolean
                if (noindex != null && noindex) {
                    div("prozen-popup-stats-item") {
                        title = M.publicationNotIndexed
                        span ("publication_icon_sad_robot2 prozen-popup-stats-item-icon") {
                            style = "margin-left: 5px;"
                        }
                    }
                }
            }

            // 🤖 Индексация 😷 COVID-19 🪙 Реклама 🎹 DMCA 🤐 Комментарии отключены 🤫 Комментарии для подписчиков 😬 Комментарии открыты для всех
            /* div(classes = "prozen-popup-info-indicators") {
                span { +"🪙" }
                span { +"📺" }
            } */
            @Suppress("UNCHECKED_CAST") val tags: List<Thematic> =
                info.second[TypeCheck.THEMATICS] as? List<Thematic> ?: emptyList()

            if (tags.isNotEmpty()) {

                div(classes = "prozen-popup-info-tags") {
                    tags.take(5).forEach { span(classes = "prozen-popup-tag") { +it.title } }
                    if (tags.size > 5) {
                        span(classes = "prozen-popup-tag") {
                            title = "Всего ${tags.size} тем:\n${
                                tags.map { it.title }
                                    .chunked(3).joinToString(",\n") { group ->
                                        group.joinToString(", ")
                                    }
                            }"
                            +"+${tags.size - 5}"
                        }
                    }
                }
            }
        }
    }
}

fun getViewPair(views: Int?, viewsTillEnd: Int?): Pair<Int?, Int?> {
    return if (views == viewsTillEnd) views to null else views to viewsTillEnd
}