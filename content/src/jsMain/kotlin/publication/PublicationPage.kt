package publication

import ContentRunner
import common.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import org.w3c.dom.HTMLLinkElement
import requester.Requester
import requester.Stats


abstract class PublicationPage(val requester: Requester, val data: JsonObject) : ContentRunner {
    val channelId: String
    val publicationId: String
    var canonicalUrl: String

    var stats: PublicationStats? = null

    init {
        val zenObject = getZenObject()
        channelId = zenObject!!.first
        publicationId = zenObject.second
        canonicalUrl = getCanonicalUrl()
    }

    open suspend fun getStats() {
        val localStats = getDataStats()
        val stat = requester.getPublicationStat(publicationId)
        val noindex = isNotIndexed()
        val url = canonicalUrl // ?: "https://dzen.ru/media/id/$channelId/$publicationId"
        val (createTime, modTime) = getCreateModTime()
        stats = PublicationStats(
            createTime,
            modTime,
            maxOfNullable(localStats?.views, stat?.views),
            maxOfNullable(localStats?.viewsTillEnd, stat?.viewsTillEnd),
            localStats?.timeToRead,
            noindex,
            url
        )
    }

    private fun getCreateModTime(): Pair<Instant, Instant?> {
        val createTime = data.time("publication.addTime") ?: data.time("publication.publishTime")
        val modTime = data.time("publication.content.modTime") ?: data.time("publication.modificationTime")
        return createTime!! to modTime
    }

    private fun getDataStats(): Stats? {
        val localStats = data.obj("publication.publicationStatistics")
        val views = localStats?.int("views")
        val viewsTillEnd = localStats?.int("viewsTillEnd")
        val timeToRead = data.int("publication.content.timeToReadSeconds")
        if (views == null || viewsTillEnd == null) return null
        return Stats(publicationId, views, viewsTillEnd, timeToRead)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun run() {
        GlobalScope.launch {
            getStats()
            showStats()
        }
    }

    abstract fun showStats()

    suspend fun isNotIndexed(): Boolean {
        if (Option.CHECK_NOINDEX.isSet()) {
            val noIndexOnPage = checkNoIndex()
            return if (Option.RECHECK_NOINDEX.isSet() && noIndexOnPage) {
                checkNoIndexUrl(canonicalUrl)
            } else {
                noIndexOnPage
            }
        } else {
            return false
        }
    }

    private fun getCanonicalUrl(): String {
        return (document.head!!.querySelector("link[rel=canonical][href]") as? HTMLLinkElement)?.href
            ?: getSignificantUrl(window.location.href)
    }
}