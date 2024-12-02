import common.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.html.div
import kotlinx.html.dom.append
import kotlinx.html.js.a
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import pageanalyzer.*
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
                    val pageContext = createPageContext(activeTab.url!!, listOf(CheckNoIndex, CheckThematics))
                    showInfo(pageContext)
                }
            }
        }
    }
}

fun showInfo(info: PageContext) {
    document.getElementById("prozen-popup-info-block-container")?.append {
        div(classes = "prozen-popup-info-block") {
            div(classes = "prozen-popup-info-title") {
                +info.title
            }

            val viewPair = info.getViewPair()

            div(classes = "prozen-popup-info-stats") {
                viewPair.first?.let {
                    div("prozen-popup-stats-item") {
                        title = M.publicationViews
                        span("publication_icon_views_2 prozen-popup-stats-item-icon")
                        +it.format()
                    }
                }

                viewPair.second?.let {
                    div("prozen-popup-stats-item") {
                        title = M.publicationFullViews
                        span("publication_icon_full_views prozen-popup-stats-item-icon")
                        +it.format()
                    }
                }

                info.likes?.let {
                    div("prozen-popup-stats-item") {
                        title = M.publicationLikes
                        span("publication_icon_likes prozen-popup-stats-item-icon")
                        +it.format()
                    }

                }
                info.comments?.let {
                    div("prozen-popup-stats-item") {
                        title = M.publicationComments
                        span("publication_icon_comments prozen-popup-stats-item-icon")
                        +it.format()
                    }
                }
                info.timeToRead?.let {
                    div("prozen-popup-stats-item") {
                        title = M.publicationTimeToRead
                        span("publication_icon_read_time prozen-popup-stats-item-icon")
                        +it.secToHHMMSS()
                    }
                }
                val okLink = convertDzenUrlToOk(info.url)
                okLink?.let {
                    div("prozen-popup-stats-item") {
                        a(href = it, target = "_blank") {
                            title = M.publicationOkLink
                            span("publication_icon_ok_logo prozen-popup-stats-item-icon") {
                                style = "margin-left: 5px; width: 9px !important; height: 14px !important;"
                            }
                        }
                    }
                }

                val noindex = info.checkResults[TypeCheck.NO_INDEX] as? Boolean
                if (noindex != null && noindex) {
                    div("prozen-popup-stats-item") {
                        title = M.publicationNotIndexed
                        span("publication_icon_sad_robot2 prozen-popup-stats-item-icon") {
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
                info.checkResults[TypeCheck.THEMATICS] as? List<Thematic> ?: emptyList()

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


fun PageContext.getViewPair(): Pair<Int?, Int?> {
    if (views != viewsTillEnd) return views to viewsTillEnd
    return if (this.type == "article") null to viewsTillEnd else views to null
}