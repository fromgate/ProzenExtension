package publication

import common.*
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.datetime.Instant


const val NO_INDEX_TITLE = "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
        "Публикация не индексируется поисковиками.\n" +
        "Если вы недавно редактировали статью, то это нормально.\n" +
        "Примечание: связь этого тега с показами,\n" +
        "пессимизацией и иными ограничениями канала\n" +
        "официально не подтверждена."
const val SHORT_LINK_TITLE = "Сокращённая ссылка на статью.\nКликните, чтобы скопировать её в буфер обмена."

abstract class PublicationPage(val requester: Requester, val data: JsonObject) {
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
        val noindex = checkNoIndex()
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
    fun run() {
        GlobalScope.launch {
            getStats()
            showStats()
        }
    }
    abstract fun showStats()
}