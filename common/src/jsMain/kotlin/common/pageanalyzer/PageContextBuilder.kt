package common.pageanalyzer

import common.arr
import common.getFinalUrl
import common.int
import common.string
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.w3c.dom.*
import org.w3c.dom.parsing.DOMParser

class PageContextBuilder(private val document: Document) {
    private var url: String = ""
    private var type: String = "unknown"
    private var title: String = "Публикация"
    private var publisherId: String = ""
    private var publicationId: String = ""
    private var views: Int? = null
    private var viewsTillEnd: Int? = null
    private var comments: Int? = null
    private var likes: Int? = null
    private var thematics: List<Thematic> = emptyList()
    private var embeddedJson: JsonObject? = null
    private val metaTags: MutableList<HTMLMetaElement> = mutableListOf()
    private val checkResults: MutableMap<TypeCheck, Any> = mutableMapOf()
    private var isOk: Boolean = true
    private var isParseError: Boolean = false

    fun initializeBaseData(): PageContextBuilder {
        val (initUrl, initType) = initializeUrlAndType(document)
        url = initUrl
        type = initType
        title = initializeTitle(document)
        val (initPublisherId, initPublicationId) = initializeZenObject(document)
        publisherId = initPublisherId
        publicationId = initPublicationId
        return this
    }

    fun initializeTitle(document: Document): String {
        return document.title.split("|").first()
    }

    fun initializeZenObject(document: Document): Pair<String, String> {
        val zenObjectId = document.getElementsByTagName("meta")
            .asList()
            .filterIsInstance<HTMLMetaElement>()
            .firstOrNull { it.name == "zen_object_id" }
            ?.content
            ?.split(":", limit = 2)
        return (zenObjectId?.getOrNull(0) ?: "") to (zenObjectId?.getOrNull(1) ?: "")
    }

    fun initializeUrlAndType(document: Document): Pair<String, String> {
        val url = (document.querySelector("link[rel=canonical]") as? HTMLLinkElement)?.href ?: ""
        val type = when (url.split("/").getOrNull(3) ?: "") {
            "a" -> "article"
            "b" -> "brief"
            "video" -> "gif"
            "short_video" -> "short_video"
            else -> "unknown"
        }
        return url to type
    }


    fun initializeMetaTags(): PageContextBuilder {
        metaTags.addAll(document.getElementsByTagName("meta").asList().filterIsInstance<HTMLMetaElement>())
        return this
    }

    fun initializePageJsonData(): PageContextBuilder {
        var jsonData: JsonObject? = null
        var thematicJsonData: JsonArray? = null
        var topicChannelSubscriptionSuggestion: String? = null

        when (type) {
            "article", "brief" -> {
                val script = document.getElementById("all-data") as? HTMLScriptElement
                if (script != null) {
                    jsonData = getTextDataByPrefix(script.text)?.let { Json.decodeFromString(it) }
                    thematicJsonData = getTextDataByPrefix(script.text, "  w._thematicBanners = ")
                        ?.let { Json.decodeFromString(it) }
                    topicChannelSubscriptionSuggestion = getTextDataByPrefix(
                        script.text, "  w._topicChannelSubscriptionSuggestion = "
                    )?.let { Json.decodeFromString<JsonObject>(it).string("topicChannelTitle") }
                }
            }

            "gif", "short_video" -> {
                val script = document.getElementsByTagName("script").asList()
                    .filterIsInstance<HTMLScriptElement>()
                    .find { it.text.contains("\"webCommonData\":{\"clientDefinitionMap\":") }
                if (script != null) {
                    val jsonStr = getTextDataByPrefix(script.text, "        var _params=(", 2)
                    jsonData = jsonStr?.let { Json.decodeFromString(it) }
                    thematicJsonData = jsonData?.arr("ssrData.videoMetaResponse.thematicBanners")
                    topicChannelSubscriptionSuggestion = jsonData?.string(
                        "ssrData.videoMetaResponse.topicChannelSubscriptionSuggestion.topicChannelTitle"
                    )
                }
            }
        }

        embeddedJson = jsonData
        thematics = thematicJsonData?.let { getThematics(it, topicChannelSubscriptionSuggestion) } ?: emptyList()
        isParseError = jsonData == null
        return this
    }

    fun getTextDataByPrefix(
        scriptLines: String,
        prefix: String = "  w._data = ",
        removeLast: Int = 1,
        removePrefix: Boolean = true,
    ): String? {
        return scriptLines
            .lineSequence()
            .firstOrNull { it.startsWith(prefix) }
            ?.let { if (removePrefix) it.substring(prefix.length, it.length - removeLast) else it }
    }

    fun getThematics(thematicObject: JsonArray, mainTopic: String?): List<Thematic> {
        val thematics = mutableListOf<Thematic>()
        thematicObject.forEach {
            try {
                val themObj = it as JsonObject
                val thematic = Thematic(
                    themObj.string("link")!!,
                    themObj.string("title")!!,
                    themObj.string("tabId")!!,
                    themObj.string("type")!!,
                    themObj.int("subscribersCount")
                )
                thematics.add(thematic)
            } catch (e: Exception) {
                console.log(e)
            }
        }
        return thematics.sortByTitle(mainTopic)
    }

    fun getTextDataByPattern(
        scriptLines: String,
        startPattern: String = "{\"data\":{\"MICRO_APP_SSR_DATA",
        endPattern: String = "}})}();",
    ): String? {
        val begin = scriptLines.indexOf(startPattern)
        if (begin < 0) return null
        return scriptLines.substring(begin, scriptLines.length - endPattern.length)
    }

    /*
    fun isVideo() = type == "short_video" || type == "gif"
fun PageContext.isText() = type == "article" || type == "brief"
fun PageContext.isOk() = this.isOk && !this.isParseError
fun PageContext.containsAnyChecks(typeChecks: List<TypeCheck>): Boolean {
    return checkResults.keys.any { it in typeChecks }
}
     */


    fun initializeStats(): PageContextBuilder {
        if (isText()) {
            views = embeddedJson?.int("publication.publicationStatistics.views")
            viewsTillEnd = embeddedJson?.int("publication.publicationStatistics.viewsTillEnd")
            val script = document.getElementById("all-data") as? HTMLScriptElement
            if (script != null) {
                val scriptText = getTextDataByPrefix(script.text, "  w._socialMediaResponse = ")
                val data = scriptText?.let { Json.decodeFromString<JsonObject>(it) }
                    ?.arr("items")?.firstOrNull()?.jsonObject
                comments = data?.int("metaInfo.commentsCount")
                likes = data?.int("metaInfo.likeCount")
            }
        } else if (isVideo()) {
            val script = document.getElementById("video-microdata") as? HTMLScriptElement
            if (script != null) {
                val data = Json.decodeFromString<JsonObject>(script.text)
                val counters = data.arr("interactionStatistic")
                counters?.forEach {
                    val counter = it.jsonObject
                    when (counter.string("interactionType.@type")) {
                        "WatchAction" -> views = counter.int("userInteractionCount")
                        "LikeAction" -> likes = counter.int("userInteractionCount")
                        "CommentAction" -> comments = counter.int("userInteractionCount")
                    }
                }
            }
        }
        return this
    }

    fun isVideo() = type == "short_video" || type == "gif"

    fun isText() = type == "article" || type == "brief"

    fun finalize(): PageContext {
        return PageContext(
            url = url,
            type = type,
            title = title,
            publisherId = publisherId,
            publicationId = publicationId,
            views = views,
            viewsTillEnd = viewsTillEnd,
            comments = comments,
            likes = likes,
            thematics = thematics,
            embeddedJson = embeddedJson,
            metaTags = metaTags,
            checkResults = checkResults,
            isOk = isOk,
            isParseError = isParseError
        )
    }


}

fun performChecks(context: PageContext, checks: List<PageCheck>) {
    if (context.isOk()) {
        checks.forEach {
            it.analyze(context)
            context.checkResults.putAll(it.getResults())
        }
    }
}

suspend fun createPageContext(
    documentUrl: String,
    checks: List<PageCheck> = listOf(
        CheckNoIndex, CheckAdv, CheckThematics, CheckCovid, CheckComments
    )
): PageContext {
    val targetUrl = if (documentUrl.matches(Regex("""https://dzen\.ru/(a|b|video|short_video)/.+"""))) {
        documentUrl
    } else {
        getFinalUrl(documentUrl)
    }

    val response = window.fetch(targetUrl).await()
    if (response.ok) {
        val htmlContent = response.text().await()
        val document = DOMParser().parseFromString(htmlContent, "text/html")

        val builder = PageContextBuilder(document)
            .initializeBaseData()
            .initializeMetaTags()
            .initializePageJsonData()
            .initializeStats()

        val context = builder.finalize()
        performChecks(context, checks)
        return context
    } else {
        return PageContext(
            url = targetUrl,
            type = "unknown",
            title = "unknown",
            publisherId = "",
            publicationId = "",
            isOk = false
        ).apply {
            checkResults[TypeCheck.HTTP_STATUS_CODE] = response.status.toInt()
        }
    }
}


