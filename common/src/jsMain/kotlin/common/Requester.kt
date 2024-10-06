package common

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.w3c.dom.url.URL


open class Requester(val publisherId: String?, val token: String?) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    fun hasToken(): Boolean = this.token != null
    fun hasPublisherId(): Boolean = this.publisherId != null

    val defaultHeaders: HeadersBuilder.() -> Unit = {
        append(
            HttpHeaders.UserAgent,
            "Mozilla/5.0 (Apple-iPhone7C2/1202.466; U; CPU like Mac OS X; en) AppleWebKit/420+ (KHTML, like Gecko) Version/3.0 Mobile/1A543 Safari/419.3"
        )
        append("Zen-Client-Experiments", "zen-version:2.189.0")
        append(
            "Zen-Features",
            "{\"no_amp_links\":true,\"forced_bulk_stats\":true,\"blurred_preview\":true,\"big_card_images\":true,\"complaints_with_reasons\":true,\"pass_experiments\":true,\"video_providers\":[\"yandex-web\",\"youtube\",\"youtube-web\"],\"screen\":{\"dpi\":96},\"need_background_image\":true,\"color_theme\":\"white\",\"no_small_auth\":true,\"need_main_color\":true,\"need_zen_one_data\":true,\"interests_supported\":true,\"return_sources\":true,\"screens\":[\"feed\",\"category\",\"categories\",\"profile\",\"switchable_subs\",\"suggest\",\"blocked\",\"preferences\",\"subscribers\",\"blocked_suggest\",\"video_recommend\",\"language\",\"send_app_link_sms\",\"comments_counter\",\"social_profile\",\"social_activity_feed\",\"social_profile_edit\",\"social_interests_feedback\",\"profile_onboarding_shown\",\"profile_complete_onboarding\",\"profile_deactivate\",\"profile_cancel_deactivate\"],\"stat_params_with_context\":true,\"card_types\":[\"post\"]}"
        )
        append("X-Prozen-Request", "kot")
        if (token != null) {
            append("X-Csrf-Token", token)
        }
    }


    /**
     * SCR — Subscribers Coverage Rate
     *
     * @param from начальная дата в формате YYYY-MM-DD
     * @param to конечная дата в формате YYYY-MM-DD
     * @returns Double - значение SCR
     */
    open suspend fun getScr(from: String, to: String): Double? {
        val requestUrl =
            "https://dzen.ru/editor-api/v2/publisher/$publisherId/stats2?allPublications=true&addTimeFrom=$from&addTimeTo=$to&fields=typeSpecificViews&fields=deepViewsRate&fields=ctr&fields=vtr&fields=shares&fields=comments&fields=subscriptions&fields=likes&fields=unsubscriptions&fields=viewMap&fields=subscribers&fields=subscribersDiff&fields=impressions&fields=deepViews&fields=sumInvolvedViewTimeSec&groupBy=flight&sortBy=addTime&sortOrderDesc=true&total=true&totalLimitedByIds=false&isSubscriber=true&pageSize=100&page=0&clid=320"
        val data = getJson(requestUrl)
        val totalViews = data?.obj("total")?.obj("stats")?.int("impressions")?.toDouble()
        val openingSubscribers = data?.int("openingSubscribers")?.toDouble()
        val count = data?.int("publicationCount")?.toDouble()
        if (totalViews == null || openingSubscribers == null || count == null) return null
        val scr = ((totalViews / count) / openingSubscribers) * 100
        return scr
    }


    /**
     * Получить вознаграждение за период (по дням)
     *
     * @param from начальная дата в формате YYYY-MM-DD
     * @param to конечная дата в формате YYYY-MM-DD
     * @returns List<Triple<String, Double, Double>> — список из даты в формате YYYY-M-D, дневного дохода и курса
     */
    open suspend fun getTimespentRewards(from: String, to: String): List<Triple<String, Double, Double>> {
        val rewards = mutableListOf<Triple<String, Double, Double>>()
        val requestUrl =
            "https://dzen.ru/editor-api/v2/publisher/$publisherId/income2?from=$from&to=$to&orderBy=totalIncome&ascending=false&page=0&pageSize=50&total=true"
        val data = getJson(requestUrl)
        val intervals = data?.obj("total")?.arr("intervals")
        intervals?.forEach { interval ->
            val rewardData = interval.jsonObject
            val timeStamp = rewardData.long("timestamp")
            val time = timeStamp?.let { Instant.fromEpochMilliseconds(it) }
            val dateStr = time?.toDDMMYY()
            val income = rewardData.obj("income")?.double("timespent-reward")
            val viewTimeSec = rewardData.int("viewTimeSec")

            if (dateStr != null && income != null && viewTimeSec != null && viewTimeSec > 0) {
                val course = income / (viewTimeSec / 60)
                rewards.add(Triple(dateStr, income, course))
            }
        }
        return rewards
    }

    /**
     * Получение информации об ограничениях канала
     *
     * @returns Pair<Boolean, Int>? — канал ограничен (true/false), количество ограниченных публикаций
     * */
    open suspend fun getStrikesInfo(): Pair<Boolean, Int>? {
        val requestUrl = "https://dzen.ru/editor-api/v2/v2/get-strikes?publisherId=$publisherId&language=ru"
        val data = getJson(requestUrl)
        val channelBlock = data?.bool("channelRestricted")
        val limitations = data?.arr("limitations")?.size
        return if (channelBlock != null && limitations != null) {
            channelBlock to limitations
        } else null
    }

    /**
     * Получение числа заблокированных комментаторов
     *
     * @return Int? — число заблокированных пользователей, null — ошибка
     */
    open suspend fun getBannedUsers(): Int? {
        val requestUrl = "https://dzen.ru/api/comments/banned-users";
        val data = getJson(requestUrl)
        return data?.arr("bannedUsers")?.size
    }

    open suspend fun getStatsActuality(): String? {
        val requestUrl =
            "https://dzen.ru/editor-api/v2/publisher/$publisherId/stats2?fields=views&publicationTypes=article&publisherId=$publisherId&allPublications=true&groupBy=flight&sortBy=addTime&sortOrderDesc=true&pageSize=1&page=0"
        val jsonResponse = getJson(requestUrl)
        val actuality = jsonResponse?.long("actuality")?.toInstant()
        return actuality?.toDDMMYYYYHHMM(2)
    }


    suspend fun getPublicationStat(publicationId: String): Stats? {
        val requestUrl = "https://dzen.ru/media-api/publication-view-stat?publicationId=$publicationId"
        return getData(requestUrl)
    }

    /**
     * Получить данные о канале
     *
     * @returns Pair<Int?, Long?> — счётчик метрики, время регистрации
     */
    open suspend fun getChannelData(): Pair<Int?, Instant?> {
        val requestUrl = "https://dzen.ru/editor-api/v2/id/$publisherId/money"
        val jsonResponse = getJson(requestUrl)
        val publisher = jsonResponse?.obj("publisher")
        val metrikaCounterId = publisher?.obj("privateData")?.int("metrikaCounterId")
        //val audience = publisher?.int("audience")
        val regTime = publisher?.long("regTime")?.toInstant()
        return Pair(metrikaCounterId, regTime)
    }

    /**
     * Получение списка публикаций карточек публикаций
     *
     * @param pageSize —
     * @param types —
     * @param view —
     * @param query —
     * @param publicationIdAfter  —
     *
     * @return JsonObject? — список публикаций
     */
    suspend fun getPublicationsByView(
        pageSize: Int? = null,
        types: String? = null,
        view: String? = null,
        query: String? = null,
        publicationIdAfter: String? = null,
    ): List<Card> {
        val url = URL("editor-api/v3/publications", "https://dzen.ru")
        with(url.searchParams) {
            set("state", "published")
            append("pageSize", pageSize?.toString() ?: "10")
            append("publisherId", publisherId!!)

            query?.let { append("query", it) }
            types?.let { append("types", it) }
            view?.let { append("view", it) }
            publicationIdAfter?.let { append("publicationIdAfter", it) }
        }
        val jsonObject = getJson(url.href)
        return jsonObject?.let(::studioRequestDataToCards) ?: emptyList()
    }

    suspend fun getPublicationsStatsSubscribers(ids: List<String>): Map<String, Int> {
        val subscribersViews = mutableMapOf<String, Int>()
        if (ids.isNotEmpty()) {
            val url = URL("editor-api/v2/publisher/$publisherId/stats2", "https://dzen.ru")
            with(url.searchParams) {
                append("publisherId", publisherId!!)
                ids.forEach {
                    append("publicationIds", it)
                }
                append("fields", "typeSpecificViews")
                append("groupBy", "ageGender")
                append("isSubscriber", "true")
                append("from", "2022-01-25")
            }

            val jsonObject = getJson(url.href)
            val pubData = jsonObject?.arr("publications")

            pubData?.forEach {
                (it as? JsonObject)?.let {
                    val publicationId = it.obj("publication")?.string("publicationId")
                    val typeSpecificViews = it.obj("stats")?.int("typeSpecificViews")
                    if (publicationId != null && typeSpecificViews != null) {
                        subscribersViews[publicationId] = typeSpecificViews
                    }
                }

            }
        }
        return subscribersViews
    }

    suspend fun getPublicationsByFilterAndSubscribers(
        pageSize: Int? = 10,
        types: String? = null,
        publicationIdAfter: String? = null,
        view: String? = null,
        query: String? = null,
    ): List<Card> {
        val cards = getPublicationsByView(pageSize, types, view, query, publicationIdAfter)
        val ids = cards.map { it.id }
        val subscribersViews = getPublicationsStatsSubscribers(ids);
        cards.forEach { it.subscribersViews = subscribersViews[it.id] ?: 0 }
        return cards
    }

    /**
     * Выполнить GET-запрос по указанному URL и вернуть ответ в виде объекта JSON
     *
     * @param requestUrl: String — адрес для запроса
     * @param customHeaders: HeadersBuilder —
     * @return JsonObject —
     */
    suspend fun getJson(
        requestUrl: String,
        customHeaders: HeadersBuilder.() -> Unit = defaultHeaders,
    ): JsonObject? {
        val response = getHttpResponse(requestUrl, customHeaders)
        return if (response.status == HttpStatusCode.OK) {
            response.body()
        } else {
            null
        }
    }

    suspend inline fun <reified T> getData(
        requestUrl: String,
        noinline customHeaders: HeadersBuilder.() -> Unit = defaultHeaders,
    ): T? {
        val response = getHttpResponse(requestUrl, customHeaders)
        return if (response.status == HttpStatusCode.OK) {
            Json.decodeFromString<T>(response.bodyAsText())
        } else {
            null
        }
    }

    suspend fun getHttpResponse(
        requestUrl: String,
        customHeaders: HeadersBuilder.() -> Unit = defaultHeaders,
    ): HttpResponse {
        return client.get(requestUrl) {
            headers {
                customHeaders()
            }
        }
    }

}

@Serializable
data class Stats(val publicationId: String, val views: Int, val viewsTillEnd: Int)