package common

import kotlinx.serialization.json.JsonObject

data class Card(
    val title: String,
    val id: String,
    val publisherId: String,
    val addTime: Long?,
    val modTime: Long,
    val publishTime: Long,
    val feedShows: Int,
    val clicks: Int,
    val views: Int?,
    val viewsTillEnd: Int,
    val subscribersViews: Int?,
    val sumViewTimeSec: Int,
    val likes: Int,
    val comments: Int,
    val type: String,
    val subscriptions: Int = 0,
    val shares: Int = 0
)

fun JsonObject.toCard(): Card? {
    return try {
        Card(
            title = this.obj("content")!!.obj("preview")!!.string("title")!!,
            id = this.string("id")!!,
            publisherId = this.string("publisherId")!!,
            addTime = this.long("addTime"),
            modTime = this.obj("content")!!.long("modTime")!!,
            publishTime = this.long("publishTime") ?: 0,
            feedShows = this.int("feedShows") ?: 0,
            clicks = this.int("clicks") ?: 0,
            views = this.int("views") ?: 0,
            viewsTillEnd = this.int("typeSpecificViews") ?: 0,
            subscribersViews = this.int("subscribersViews") ?: 0,
            sumViewTimeSec = this.int("sumViewTimeSec") ?: 0,
            likes = this.int("likeCount") ?: 0,
            comments = this.int("commentCount") ?: 0,
            type = this.obj("content")!!.string("type")!!,
            subscriptions = this.int("subscriptions") ?: 0,
            shares = this.int("shares") ?: 0,
        )
    } catch (e: Exception) {
        null
    }
}
