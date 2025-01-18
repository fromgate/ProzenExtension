package pageanalyzer

import common.*
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.*
import org.w3c.dom.*
import org.w3c.dom.parsing.DOMParser

class PageContextBuilder(private val document: Document) {
    private var url: String = ""
    internal var type: String = "unknown"
    private var title: String = "Публикация"
    private var publisherId: String = ""
    private var publicationId: String = ""
    private var views: Int? = null
    private var viewsTillEnd: Int? = null
    private var comments: Int? = null
    private var likes: Int? = null
    private var timeToRead: Int? = null
    internal var thematics: List<Thematic> = emptyList()
    internal var embeddedJson: JsonObject? = null
    internal val metaTags: MutableList<HTMLMetaElement> = mutableListOf()
    private val checkResults: MutableMap<TypeCheck, Any> = mutableMapOf()
    private var isOk: Boolean = true
    private var isParseError: Boolean = false
    private var isCaptchaAsked: Boolean = false

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
        val regex = Regex("""https://dzen\.ru/(a|b|video/watch|shorts)/([^?/]+)""")
        val match = regex.find(url)
        val type = when (match?.groupValues?.get(1) ?: "unknown") {
            "a" -> "article"
            "b" -> "brief"
            "video/watch" -> "gif"
            "shorts" -> "short_video"
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
        val script = document.getElementsByTagName("script").asList()
            .filterIsInstance<HTMLScriptElement>()
            .find { it.text.contains(""""webCommonData":{"clientDefinitionMap":""") }
        if (script != null) {
            jsonData = extractJsonElement(script.text, "var _params")?.jsonObject
        }
        embeddedJson = jsonData
        isParseError = jsonData == null
        return this
    }

    fun initializeThematics(): PageContextBuilder {
        val pathPrefix = if (isText()) "ssrData.publishersResponse" else "ssrData.videoMetaResponse"

        val thematicJsonData: JsonArray? = embeddedJson?.arr("$pathPrefix.thematicBanners")
        val topicChannelSubscriptionSuggestion: String? =
            embeddedJson?.string("$pathPrefix.topicChannelSubscriptionSuggestion.topicChannelTitle")

        val tags = metaTags.filter { it.getAttribute("property") == "og:article:tag" }.map { it.content }
        val firstTopic = tags.firstOrNull() ?: topicChannelSubscriptionSuggestion

        thematics = thematicJsonData?.let {
            getThematics(it, tags, topicChannelSubscriptionSuggestion)
        }?.sortByTitle(firstTopic) ?: emptyList()

        return this
    }

    fun extractJsonStrings(
        input: String,
        required: Set<String>? = null,
        prefixRegex: Regex = Regex("""(?:w\.\w+|var\s+\w+)\s*=\s*\(?"""),// Regex("""w\.\w+\s*=\s*""")
    ): Map<String, String> {
        return input.lines()
            .mapNotNull { line ->
                line.trim().takeIf { it.contains(prefixRegex) }
            }
            .mapNotNull { line ->
                val prefix = prefixRegex.find(line)!!.value.replace(Regex("""\s*=\s*\(?"""), "").trim()
                if (required == null || required.contains(prefix)) {
                    val valuePart = line.substringAfter("=")
                        .trim()
                        .removePrefix("(")
                        .removeSuffix(";")
                        .removeSuffix(")")
                    prefix to valuePart
                } else {
                    null
                }
            }.toMap()
    }

    fun getThematics(thematicObject: JsonArray, tags: List<String>?, suggested: String?): List<Thematic> {
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
        return thematics.getTagged(tags, suggested)
    }

    fun initializeStats(): PageContextBuilder {
        if (isText()) {
            views = embeddedJson?.int("ssrData.publishersResponse.data.data.publication.publicationStatistics.views")
            viewsTillEnd =
                embeddedJson?.int("ssrData.publishersResponse.data.data.publication.publicationStatistics.viewsTillEnd")
            timeToRead = embeddedJson?.int("ssrData.publishersResponse.data.data.publication.content.timeToReadSeconds")
            val data = embeddedJson?.arr("ssrData.socialMetaResponse.items")?.firstOrNull()?.jsonObject
            comments = data?.int("metaInfo.commentsCount")
            likes = data?.int("metaInfo.likeCount")
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

    fun performChecks(checks: List<PageCheck>): PageContextBuilder {
        if (this.isOk && !this.isParseError) {
            checks.forEach {
                it.analyze(this)
                this.checkResults.putAll(it.getResults())
            }
        }
        return this
    }

    fun extractJsonElement(
        input: String,
        required: String,
        prefixRegex: Regex = Regex("""(?:w\.\w+|var\s+\w+)\s*=\s*\(?"""),// Regex("""w\.\w+\s*=\s*"""),
    ): JsonElement? {
        val jsonString = extractJsonStrings(input, setOf(required), prefixRegex).values.first()
        return try {
            Json.decodeFromString<JsonElement>(jsonString)
        } catch (e: Exception) {
            console.dError("Failed to parse $required : ${jsonString.take(50)}...")
            null
        }
    }

    fun extractJsonElements(
        input: String,
        required: Set<String>? = null,
        prefixRegex: Regex = Regex("""(?:w\.\w+|var\s+\w+)\s*=\s*\(?"""),// Regex("""w\.\w+\s*=\s*"""),
    ): Map<String, JsonElement?> {
        val jsonStrings = extractJsonStrings(input, required, prefixRegex)
        return jsonStrings.mapValues { (key, jsonString) ->
            try {
                Json.decodeFromString<JsonElement>(jsonString)
            } catch (e: Exception) {
                console.dError("Failed to parse $key : ${jsonString.take(50)}...")
                null
            }
        }
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
            timeToRead,
            thematics = thematics,
            // embeddedJson = embeddedJson,
            // metaTags = metaTags,
            checkResults = checkResults,
            isOk = isOk,
            isParseError = isParseError,
            isCaptchaAsked = isCaptchaAsked
        )
    }
}

suspend fun createPageContext(
    documentUrl: String,
    checks: List<PageCheck> = listOf(
        CheckNoIndex, CheckAdv, CheckThematics, CheckCovid, CheckComments, CheckDmca
    ),
): PageContext {
    console.dLog("createPageContext: $documentUrl")
    val regex = Regex("""https://dzen\.ru/(a|b|video/watch|shorts)/([^?/]+)""")
    val targetUrl = regex.find(documentUrl)?.groupValues?.get(0) ?: getCachedFinalUrl(documentUrl)
    console.dLog("targetUrl: $targetUrl")
    if (targetUrl.startsWith("https://dzen.ru/showcaptcha?")) {
        console.dLog("Captcha were asked")
        return PageContext(
            url = targetUrl,
            type = "unknown",
            title = "unknown",
            publisherId = "",
            publicationId = "",
            isOk = false,
            isCaptchaAsked = true
        )
    }

    val response = window.fetch(targetUrl).await()
    if (response.ok) {
        console.dLog("Response: Ok")
        val htmlContent = response.text().await()
        val document = DOMParser().parseFromString(htmlContent, "text/html")
        val builder = PageContextBuilder(document)
            .initializeBaseData()
            .initializeMetaTags()
            .initializePageJsonData()
            .initializeThematics()
            .initializeStats()
            .performChecks (checks)
        val context = builder.finalize()
        console.dLog("Check is Ok: ${context.isOk()}")
        return context
    } else {
        console.dLog("Response: ${response.status}")
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


