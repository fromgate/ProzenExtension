import common.Card
import common.smallImage
import common.url
import kotlinx.browser.document
import kotlinx.datetime.Instant
import kotlinx.html.*
import kotlinx.html.dom.create
import org.w3c.dom.HTMLElement
import requester.Requester

class Finder(val requester: Requester) {

    private var pubications: List<Card> = emptyList()

    suspend fun loadPublications() {
        pubications = requester.loadAllPublications(true)
    }

    fun hasPublications(): Boolean {
        return pubications.isNotEmpty()
    }

    fun find(
        query: String, types: List<String>,
        period: Pair<Instant, Instant>?
    ): List<Card> {
        return pubications.filter {
            it.type in types && it.addedIn(period) && it.cardMatch(query)
        }
    }
}

fun Card.cardMatch(searchString: String?): Boolean {
    if (searchString.isNullOrEmpty()) {
        return true
    }

    val searchWords = searchString.split(" ").map { it.lowercase() } // Преобразуем один раз
    val title = this.title.lowercase()
    val description = this.snippet?.lowercase() ?: ""

    return searchWords.any { word -> title.contains(word) || description.contains(word) }
}

fun Card.addedIn(period: Pair<Instant, Instant>?): Boolean {
    if (period == null) return true
    return this.addTime!! > period.first.toEpochMilliseconds() && this.addTime!! < period.second.toEpochMilliseconds()
}

fun Card.toLi(): HTMLElement {
    return document.create.li("prozen-search-result-item") {
        a(href = this@toLi.url(), classes = "prozen-search-result-link", target = "_blank") {
            div("prozen-image-container") {
                img(
                    src = this@toLi.smallImage() ?: "img/picture-placeholder.png",
                    classes = "prozen-search-result-icon"
                )
                span("prozen-svg-icon") {
                    span ("prozen-type-${this@toLi.type.replace("_","-")}-icon ")
                }
            }

            div {
                div(classes = "prozen-search-result-title") {
                    +this@toLi.title
                }
                this@toLi.snippet?.let {
                    div(classes = "prozen-search-result-description") {
                        +it
                    }
                }
            }
        }
    }
}