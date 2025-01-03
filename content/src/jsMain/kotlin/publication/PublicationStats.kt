package publication

import common.toDDMMYYYYHHMM
import kotlinx.datetime.Instant

data class PublicationStats(
    var createTime: Instant,
    var modificationTime: Instant?,
    var views: Int?,
    var viewsTillEnd: Int?,
    var timeToRead: Int?,
    val notIndexed: Boolean,
    val shortLink: String,
)

fun PublicationStats.showTime(): String {
    val createTimeStr = createTime.toDDMMYYYYHHMM()
    val modTimeStr =
        modificationTime?.toDDMMYYYYHHMM()?.takeIf {
            it != createTimeStr
        }?.let { " ($it)" } ?: ""
    return createTimeStr + modTimeStr
}
