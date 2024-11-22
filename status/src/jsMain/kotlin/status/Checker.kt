package status

import common.*
import common.pageanalyzer.*
import common.progress.ProgressBar
import kotlinx.browser.document
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Instant
import kotlinx.html.*
import kotlinx.html.dom.create
import org.w3c.dom.HTMLElement

class Checker(private val requester: Requester) {


    private var cards: List<Card> = emptyList()
    private var checks: MutableMap<String, PageContext> = mutableMapOf()

    fun getPageContext(card: Card): PageContext? {
        return checks[card.id]
    }

    suspend fun loadPublications() {
        cards = requester.loadAllPublications(true)
        console.dInfo("Checker: Loaded ${cards.size} cards")
    }

    fun countUnloadedContext(cards: List<Card>): Int {
        return cards.count { it.id !in checks }
    }

    fun getUnloadedContextCards(cards: List<Card>): List<Card> {
        return cards.filter { it.id !in checks }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun loadPageContext(ids: List<String>) {
        GlobalScope.launch {
            val missingIds = ids.filter { it !in checks }
            val cardsToLoad = missingIds.mapNotNull { id ->
                cards.find { it.id == id }?.let { id to it.url() }
            }

            cardsToLoad.map { (id, url) ->
                GlobalScope.async {
                    checks[id] = createPageContext(url)
                }
            }.awaitAll()
        }
    }

    suspend fun loadPageContext(card: Card) {
        checks[card.id] = createPageContext(card.url())
    }


    @OptIn(DelicateCoroutinesApi::class)
    suspend fun loadCardsInParallel(unloaded: List<Card>, maxParallelism: Int = 3, progress: ProgressBar? = null) {
        var count = 0
        val semaphore = Semaphore(maxParallelism)
        val countMutex = Mutex()
        val jobs = unloaded.map { card ->
            GlobalScope.async {
                semaphore.withPermit {
                    progress?.update(text = card.title)
                    loadPageContext(card)
                    countMutex.withLock {
                        count++
                    }
                    progress?.update(count, unloaded.size)
                }
            }
        }
        jobs.awaitAll()
    }


    fun hasPublications(): Boolean {
        return cards.isNotEmpty()
    }

    fun find(
        types: List<String>,
        period: Pair<Instant, Instant>?,
    ): List<Card> {
        return cards.filter {
            it.type in types && it.addedIn(period)
        }
    }

    fun getAllTopics(): Set<Thematic> {
        val topics = mutableSetOf<Thematic>()
        checks.values.forEach {
            val thematics: List<Thematic>? = it.checkResults[TypeCheck.THEMATICS] as? List<Thematic>
            thematics?.let { topics.addAll(it) }
        }
        return topics
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


@Suppress("UNCHECKED_CAST")
fun Card.toLi(pageContext: PageContext?): HTMLElement {
    return document.create.li("prozen-search-result-item") {
        a(href = this@toLi.url(), classes = "prozen-search-result-link", target = "_blank") {
            div("prozen-image-container") {
                img(
                    src = this@toLi.smallImage() ?: "img/picture-placeholder.png",
                    classes = "prozen-search-result-icon"
                )
                span("prozen-svg-icon") {
                    span("prozen-type-${this@toLi.type.replace("_", "-")}-icon ")
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

                div(classes = "prozen-status-result") {
                    if (this@toLi.isBanned) {
                        span("prozen-status-result-emoji") {
                            title = "Публикация заблокирована"
                            +"❌"
                        }
                    }
                    pageContext?.let { context ->

                        if (context.isOk()) {
                            TypeCheck.entries.filter { !it.skip }.forEach { typeCheck ->
                                context.checkResults[typeCheck]?.let {
                                    console.dLog("typeCheck : ${typeCheck.name} $it")
                                    span("prozen-status-result-emoji") {
                                        title = typeCheck.hint
                                        +typeCheck.icon
                                    }
                                }
                            }

                            val topics = context.checkResults[TypeCheck.THEMATICS] as? List<Thematic>
                            topics?.forEach {
                                span("prozen-status-result-tag") {
                                    attributes["data-topic"] = it.type
                                    +it.title
                                }
                            }

                        } else {
                            if (!context.isOk) {
                                span("prozen-status-result-emoji") {
                                    val statusCode = context.checkResults[TypeCheck.HTTP_STATUS_CODE] as? Int
                                    title =
                                        "Ошибка загрузки страницы.\nПовторите проверку позднее.${statusCode?.let { "\nКод ошибки $it" } ?: ""}"
                                    +"⚠️"
                                }
                            }
                            if (context.isParseError) {
                                span("prozen-status-result-emoji") {
                                    title = "Ошибка распознавания данных.\nВозможно нужно обновить расширение."
                                    +"⚠️"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}