package common.pageanalyzer

import common.arr
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

data class PageContext(val document: Document) {

    var title: String = "Публикация"

    var type: String
    val publisherId: String
    val publicationId: String
    val url: String

    var views: Int? = null
    var viewsTillEnd: Int? = null
    var comments: Int? = null
    var likes: Int? = null

    val metaTags = document.getElementsByTagName("meta").asList().filterIsInstance<HTMLMetaElement>()
    var embeddedJson: JsonObject? = null

    var thematics: List<Thematic> = emptyList()

    init {
        url = (document.querySelector("link[rel=canonical]") as? HTMLLinkElement)?.href ?: ""
        type = when (url.split("/").getOrNull(3) ?: "") {
            "a" -> "article"
            "b" -> "brief"
            "video" -> "gif"
            "short_video" -> "short_video"
            else -> "unknown"
        }

        title = document.title.split("|").first()

        val zenObjectId = document.getElementsByTagName("meta")
            .asList()
            .filterIsInstance<HTMLMetaElement>()
            .firstOrNull { it.name == "zen_object_id" }
            ?.content
            ?.split(":", limit = 2)
        publisherId = zenObjectId?.getOrNull(0) ?: ""
        publicationId = zenObjectId?.getOrNull(1) ?: ""

        obtainPageJsonData()
        obtainStats()
    }

    private fun obtainStats() {
        if (isText()) {
            views = embeddedJson?.int("publication.publicationStatistics.views")
            viewsTillEnd = embeddedJson?.int("publication.publicationStatistics.viewsTillEnd")
            val script = document.getElementById("all-data") as? HTMLScriptElement
            if (script != null) {
                val scriptText = getTextDataByPrefix(script.text,"  w._socialMediaResponse = ")
                val data = scriptText?.let { Json.decodeFromString<JsonObject>(it) }?.arr("items")?.firstOrNull()?.jsonObject
                comments = data?.int ("metaInfo.commentsCount")
                likes = data?.int ("metaInfo.likeCount")
            }
        } else {
            views = embeddedJson?.int("ssrData.videoSettings.exportData.video.video.views")
            val script = document.getElementById("video-microdata") as? HTMLScriptElement
            if (script != null) {
                val data = Json.decodeFromString<JsonObject>(script.text)
                val counters = data.arr("interactionStatistic")
                counters?.forEach {
                    val counter = it.jsonObject
                    val interactionType = counter.string ("interactionType.@type")
                    val userInteractionCount = counter.int("userInteractionCount")
                    when (interactionType) {
                        "WatchAction" -> views = userInteractionCount
                        "LikeAction" -> likes = userInteractionCount
                        "CommentAction" -> comments = userInteractionCount
                    }
                }
            }
        }
    }

    fun isVideo(): Boolean = type == "short_video" || type == "gif"

    fun isText(): Boolean = type == "article" || type == "brief"

    private fun obtainPageJsonData() {
        var jsonData: JsonObject? = null
        var thematicJsonData: JsonArray? = null
        var topicChannelSubscriptionSuggestion: String? = null

        when (type) {
            "article", "brief" -> {
                val script = document.getElementById("all-data") as? HTMLScriptElement
                if (script != null) {
                    val jsonStr = getTextDataByPrefix(script.text)
                    val thematicBannerDataStr = getTextDataByPrefix(script.text, "  w._thematicBanners = ");
                    jsonData = jsonStr?.let { Json.decodeFromString<JsonObject>(it) }
                    thematicJsonData = thematicBannerDataStr?.let { Json.decodeFromString<JsonArray>(it) }

                    val mainTopicDataStr = getTextDataByPrefix(script.text, "  w._topicChannelSubscriptionSuggestion = ")
                    val mainTopicJson = mainTopicDataStr?.let { Json.decodeFromString<JsonObject>(it) }
                    topicChannelSubscriptionSuggestion = mainTopicJson?.string("topicChannelTitle")
                 }
            }
            "gif", "short_video" -> {
                val scripts = document.getElementsByTagName("script").asList().filterIsInstance<HTMLScriptElement>()
                val script = scripts.find { it.text.contains("\"webCommonData\":{\"clientDefinitionMap\":") }
                if (script != null) {
                    val jsonStr = getTextDataByPrefix (script.text, "        var _params=(",2)
                    jsonData = jsonStr?.let { Json.decodeFromString<JsonObject>(it) }
                    thematicJsonData = jsonData?.arr("ssrData.videoMetaResponse.thematicBanners")
                    topicChannelSubscriptionSuggestion = jsonData?.string("ssrData.videoMetaResponse.topicChannelSubscriptionSuggestion.topicChannelTitle")
                }
            }
        }

        val thematics = thematicJsonData?.let { getThematics(it, topicChannelSubscriptionSuggestion) }?: emptyList()

        this.thematics = thematics
        this.embeddedJson = jsonData
    }

    private fun getThematics(thematicObject: JsonArray, mainTopic: String?): List<Thematic> {
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


    private fun getTextDataByPrefix(scriptLines: String, prefix: String = "  w._data = ", removeLast: Int = 1, removePrefix: Boolean = true): String? {
        return scriptLines
            .lineSequence()
            .firstOrNull { it.startsWith(prefix) }
            ?.let { if (removePrefix) it.substring(prefix.length, it.length - removeLast) else it }
    }

    private fun getTextDataByPattern(scriptLines: String, startPattern: String = "{\"data\":{\"MICRO_APP_SSR_DATA", endPattern: String = "}})}();"): String? {
        val begin = scriptLines.indexOf(startPattern)
        if (begin < 0) return null
        return scriptLines.substring(begin, scriptLines.length - endPattern.length)
    }

    companion object {

        private val checkList = listOf (
            CheckNoIndex(),
            CheckThematics(),
            CheckCovid()
        )

        suspend fun analyzePage(url: String, checks: List<PageCheck> = checkList): Pair<PageContext, Map<TypeCheck, Any>> {
            val response = window.fetch(url).await()
            val responseText = response.text().await()
            val document = DOMParser().parseFromString(responseText, "text/html")
            val context = PageContext(document)

            val aggregatedResults = mutableMapOf<TypeCheck, Any>()

            checks.forEach { check ->
                check.analyze(context)
                aggregatedResults.putAll(check.getResults())
            }

            return context to aggregatedResults
        }
    }
}