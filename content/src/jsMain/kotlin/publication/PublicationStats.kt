package publication

import common.toStringDDMMYYYYHHMM
import kotlinx.datetime.Instant

data class PublicationStats(
    var createTime: Instant,
    var modificationTime: Instant?,
    var views: Int?,
    var viewsTillEnd: Int?,
    val notIndexed: Boolean,
    val shortLink: String
)

fun PublicationStats.showTime(): String {
    val mod = modificationTime?.takeIf { it != createTime }?.toStringDDMMYYYYHHMM()?.let { " ($it)" } ?: ""
    return createTime.toStringDDMMYYYYHHMM() + mod
}
