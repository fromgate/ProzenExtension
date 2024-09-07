package publication

import common.*
import common.Option
import kotlinx.serialization.json.JsonObject
import kotlinx.browser.document
import kotlinx.html.dom.create
import kotlinx.html.js.div
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.span
import kotlinx.html.style
import kotlinx.html.title
import org.w3c.dom.*


class Article(requester: Requester, data: JsonObject) : PublicationPage(requester, data) {
    /* 🕑 05.09.24 18:11 (05.09.24 18:18) 📃 408 📄 213 🔗 🤖 */
    override fun showStats() {
        val stats = this.stats ?: return
        val infoBlock =
            document.querySelector("div[class^=content--article-info-block__articleInfoBlock-]") as? HTMLDivElement
        infoBlock?.removeChildren()
        val dateBlock = document.create.div("article-info-block__addTimeInfo-25") {
            +"🕑 ${stats.showTime()}"
            title = "Время создания (редактирования)"
            attributes["itemprop"] = "datePublished"
        }
        infoBlock?.appendChild(dateBlock)

        if (stats.views != stats.viewsTillEnd) {
            val viewsBlock = document.create.div("article-info-block__viewsInfo-1g") {
                +"📃 ${stats.views?.format()}"
                title = "Просмотры"
            }
            infoBlock?.appendChild(viewsBlock)
        }

        val viewsTillEndBlock = document.create.div("article-info-block__viewsInfo-1g") {
            +"📄 ${stats.viewsTillEnd?.format()}"
            title = "Дочитывания"
        }
        infoBlock?.appendChild(viewsTillEndBlock)

        val shortLinkBlock = document.create.div("article-info-block__viewsInfo-1g") {
            +"🔗"
            title = SHORT_LINK_TITLE
            onClickFunction = {
                copyTextToClipboard(stats.shortLink)
            }
            style = "cursor: pointer;"
        }
        infoBlock?.appendChild(shortLinkBlock)

        if (stats.notIndexed) {
            val sadRobotBlock = document.create.div("article-info-block__viewsInfo-1g") {
                +"🤖"
                title = NO_INDEX_TITLE
            }
            infoBlock?.appendChild(sadRobotBlock)
        }

        addSubtitleLinks()
    }


    fun addSubtitleLinks() {
        Option.SUBTITLE_LINKS.value().then { option ->
            if (option) {
                val shortLink = (document.head!!.querySelector("link[rel=canonical][href]") as HTMLLinkElement).href
                val headers = document.querySelectorAll("h2, h3")
                headers.asList().forEach {
                    val header = it as HTMLHeadingElement
                    val anchorId = header.id
                    val linkIcon = document.create.span ("publication_header_icon_url"){
                        title = "Ссылка на заголовок.\n" +
                                "Кликните, чтобы скопировать её в буфер обмена"
                        onClickFunction = {
                            copyTextToClipboard("$shortLink#$anchorId")
                        }
                    }
                    if (header.hasChildNodes()) {
                        header.insertBefore(linkIcon, header.firstChild)
                    } else {
                        val text = header.innerText
                        header.innerText = ""
                        header.appendChild(linkIcon)
                        header.appendChild(document.create.span {
                            +text
                        })
                    }
                }
            }
        }
    }
}