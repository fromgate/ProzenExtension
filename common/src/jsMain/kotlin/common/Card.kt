package common

import kotlinx.serialization.json.JsonObject

data class Card(
    val id: String,
    val type: String,
    val title: String,
    val snippet: String?,
    val publisherId: String,
    var addTime: Long? = null,
    var modTime: Long? = null,
    var publishTime: Long? = null,
    var feedShows: Int? = null,
    var clicks: Int? = null,
    var viewsTillEnd: Int? = null,
    var subscriptions: Int? = null,
    var subscribersViews: Int? = null,
    var sumViewTimeSec: Int? = null,
    var ctr: Double? = null,
    var vtr: Double? = null,
    var likes: Int? = null,
    var comments: Int? = null,
    var shares: Int? = null,
)

fun Card.isOldFormat(): Boolean {
    return this.type in listOf("post", "narrative", "story")
}

fun Card.showsStr(): String = this.feedShows?.format() ?: "0"

fun Card.timeStr(): String? {
    val modStr = this.modTime?.toInstant()?.toDDMMYYYYHHMM(2)
    val createStr = this.addTime?.toInstant()?.toDDMMYYYYHHMM(2) ?: modStr
    return if (modStr != createStr) "$createStr ($modStr)" else createStr
}

fun Card.views(): Int = if (this.type == "article") this.clicks!! else this.viewsTillEnd!!

fun Card.timeStrTitle(): Pair<String, String> {
    val modStr = this.modTime?.toInstant()?.toDDMMYYYYHHMM(2)
    val createStr = this.addTime?.toInstant()?.toDDMMYYYYHHMM(2) ?: modStr

    var title = "Время создания: $createStr"
    if (modStr != createStr) title += "\nВремя редактирования: $modStr"
    return createStr!! to title
}

fun Card.timeCrateAndModStr(): Pair<String, String?> {
    val modStr = this.modTime?.toInstant()?.toDDMMYYYYHHMM(2)
    val createStr = this.addTime?.toInstant()?.toDDMMYYYYHHMM(2) ?: modStr
    return if (modStr != createStr) createStr!! to modStr else createStr!! to null
}

fun Card.viewsStrAndTitle(): Pair<String, String> {
    val (views, ctr, title) = if (this.type == "article") {
        Triple(this.clicks!!, (this.clicks!!.toDouble() / this.feedShows!!) * 100, "Клики (CTR)")
    } else {
        Triple(this.viewsTillEnd!!, (this.viewsTillEnd!!.toDouble() / this.feedShows!!) * 100, "Просмотры (VTR)")
    }
    val viewsStr = "${views.format()} (${ctr.format()}%)"
    return viewsStr to title
}

fun Card.fullReadsStrTitle(): Pair<String, String> {
    val viewsTillEndPercent = ((this.viewsTillEnd!!.toDouble() / views()) * 100).format()
    val viewsTillEndStr = "${this.viewsTillEnd!!.format()} ($viewsTillEndPercent%)"
    val viewsTillEndTitle = if (this.type == "article") "Дочитывания" else "Просмотры"
    return viewsTillEndStr to viewsTillEndTitle
}

fun Card.subscribersViewStr(): String = if (this.subscribersViews == null || this.subscribersViews == 0) {
    "0"
} else {
    val subscribersViewsPercent = try {
        ((this.subscribersViews!!.toDouble() / this.viewsTillEnd!!.toDouble()) * 100).format()
    } catch (e: Exception) {
        ""
    }
    "${subscribersViews!!.format()} ($subscribersViewsPercent%)"
}

fun Card.likes() = this.likes?.format() ?: "0"

fun Card.comments() = this.comments?.format() ?: "0"

fun Card.reposts() = this.shares?.format() ?: "0"

fun Card.subscriptions() = this.subscriptions?.format() ?: "0"

fun Card.er(): String {
    val sum = (this.comments ?: 0) + (this.likes ?: 0) + (this.subscriptions ?: 0) + (this.shares ?: 0)
    val erViews = listOf(this.viewsTillEnd, this.clicks, this.feedShows).first { it != null && it > 0 }
    return if (erViews != null) {
        "${((sum.toDouble() / erViews) * 100).format()}%"
    } else {
        "0"
    }
}

fun Card.url(): String = "https://dzen.ru/media/id/${this.publisherId}/${this.id}"

fun Card.repostUrl(): String = "https://dzen.ru/media/zen/login?briefEditorPublicationId=draft&repostId=${this.id}"

/**
 *  Формирует список карт на основе данных полученных по запросу (студия, список публикаций).
 *  Используется в методе Requester.getPublicationsByView()
 *
 *  @param jsonObject результат запроса, содержит данные о публикациях, статистике и т.п.
 *
 *  @return List<Card> - список карточек публикаций
 */
fun studioRequestDataToCards(jsonObject: JsonObject): List<Card> {
    val publications = jsonObject.arr("publications")


    val publicationCounters = jsonObject.arr("publicationCounters")
    val socialCounters = jsonObject.arr("socialCounters")
    val cards = mutableListOf<Card>()
    publications?.forEach { it ->
        val publication = it as JsonObject
        val id = publication.string("id")!!
        val publicationCounter = publicationCounters?.find {
            (it as JsonObject).string("publicationId") == id
        } as? JsonObject

        val socialCounter = socialCounters?.find {
            (it as JsonObject).string("publicationId") == id
        } as? JsonObject
        val card = studioRequestDataToCard(publication, publicationCounter, socialCounter)
        card?.let(cards::add)
    }
    return cards
}

fun studioRequestDataToCard(
    publication: JsonObject,
    publicationCounter: JsonObject?,
    socialCounter: JsonObject?,
    subscribersViews: Int = 0,
): Card? {
    return try {
        Card(
            id = publication.string("id")!!,
            type = publication.obj("content")!!.string("type")!!,
            title = publication.obj("content")!!.obj("preview")!!.string("title")!!,
            snippet = publication.obj("content")!!.obj("preview")!!.string("snippet") ?: "",
            publisherId = publication.string("publisherId")!!,
            addTime = publication.long("addTime"),
            modTime = publication.obj("content")!!.long("modTime")!!,
            publishTime = publication.long("publishTime") ?: 0,

            feedShows = publicationCounter?.int("impressions") ?: 0,
            clicks = publicationCounter?.int("clicks") ?: 0,
            //views = publicationCounter.int("views") ?: 0,
            viewsTillEnd = publicationCounter?.int("typeSpecificViews") ?: 0,
            subscriptions = publicationCounter?.int("subscriptions") ?: 0,
            sumViewTimeSec = publicationCounter?.int("sumViewTimeSec") ?: 0,
            ctr = publicationCounter?.double("ctr") ?: 0.0,
            vtr = publicationCounter?.double("vtr") ?: 0.0,

            shares = publicationCounter?.int("shares") ?: 0,

            likes = socialCounter?.int("likeCount") ?: 0,
            comments = socialCounter?.int("commentCount") ?: 0,

            subscribersViews = subscribersViews
        )
    } catch (e: Exception) {
        null
    }
}