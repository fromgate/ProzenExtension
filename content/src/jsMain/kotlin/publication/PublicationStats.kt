package publication

import common.toDDMMYYYYHHMM
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
    val mod = modificationTime?.takeIf { it != createTime }?.toDDMMYYYYHHMM()?.let { " ($it)" } ?: ""
    return createTime.toDDMMYYYYHHMM() + mod
}
