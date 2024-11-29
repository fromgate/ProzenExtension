package publication

import ContentRunner
import common.*
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import requester.Requester
import requester.Stats


abstract class PublicationPage(val requester: Requester, val data: JsonObject) : ContentRunner {
    val channelId: String
    val publicationId: String

    var stats: PublicationStats? = null

    init {
        val zenObject = getZenObject()
        channelId = zenObject!!.first
        publicationId = zenObject.second
    }

    open suspend fun getStats() {
        val localStats = getDataStats()
        val stat = requester.getPublicationStat(publicationId)
        val noindex = isNotIndexed()
        val url = "https://dzen.ru/media/id/$channelId/$publicationId"
        val (createTime, modTime) = getCreateModTime()
        stats = PublicationStats(
            createTime,
            modTime,
            maxOfNullable(localStats?.views, stat?.views),
            maxOfNullable(localStats?.viewsTillEnd, stat?.viewsTillEnd),
            noindex,
            url
        )
    }

    private fun getCreateModTime(): Pair<Instant, Instant?> {
        val createTime = data.obj("publication")?.obj("content")?.long("modTime")?.toInstant()
        val modTime = data.obj("publication")?.obj("content")?.long("modTime")?.toInstant()
        return createTime!! to modTime
    }

    private fun getDataStats(): Stats? {
        val localStats = data.obj("publication")?.obj("publicationStatistics")
        val views = localStats?.int("views")
        val viewsTillEnd = localStats?.int("viewsTillEnd")
        if (views == null || viewsTillEnd == null) return null
        return Stats(publicationId, views, viewsTillEnd)
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
        if (Option.CHECK_NOINDEX.isSet())  {
            val noIndexOnPage = checkNoIndex()
            return if (Option.RECHECK_NOINDEX.isSet() && noIndexOnPage) {
                checkNoIndexUrl(window.location.href)
            } else {
                noIndexOnPage
            }
        } else {
            return false
        }
    }
}