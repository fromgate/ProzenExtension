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
    var views: Int? = null,
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
            (it as JsonObject).string("id") == id
        } as? JsonObject

        val socialCounter = socialCounters?.find {
            (it as JsonObject).string("id") == id
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
            snippet = publication.string("snippet") ?: "",
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