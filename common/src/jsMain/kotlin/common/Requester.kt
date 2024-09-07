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


class Requester(private val publisherId: String?, private val token: String?) {

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
    suspend fun getScr(from: String, to: String): Double? {
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
     * @returns List<Triple<String,Double, Double>> — список из даты в формате YYYY-M-D, дневного дохода и курса
     */
    suspend fun getTimespentRewards(from: String, to: String): List<Triple<String, Double, Double>> {
        val rewards = mutableListOf<Triple<String, Double, Double>>()
        val requestUrl =
            "https://dzen.ru/editor-api/v2/publisher/$publisherId/income2?from=$from&to=$to&orderBy=totalIncome&ascending=false&page=0&pageSize=50&total=true"
        val data = getJson(requestUrl)
        val intervals = data?.obj("total")?.arr("intervals")
        intervals?.forEach { interval ->
            val rewardData = interval.jsonObject
            val timeStamp = rewardData.long("timestamp")
            val time = timeStamp?.let { Instant.fromEpochMilliseconds(it) }
            val dateStr = time?.toDateString()
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
     * Получить информацию об ограничениях канала
     *
     * @returns Pair<Boolean, Int>? — канал ограничен (true/false), количество ограниченных публикаций
     * */
    suspend fun getStrikesInfo(): Pair<Boolean, Int>? {
        val requestUrl = "https://dzen.ru/editor-api/v2/v2/get-strikes?publisherId=$publisherId&language=ru"
        val data = getJson(requestUrl)
        val channelBlock = data?.bool("channelRestricted")
        val limitations = data?.arr("limitations")?.size
        return if (channelBlock != null && limitations != null) {
            channelBlock to limitations
        } else null
    }

    //async function getBalanceAndMetriksId()

    suspend fun getStatsActuality(): String? {
        val requestUrl =
            "https://dzen.ru/editor-api/v2/publisher/$publisherId/stats2?fields=views&publicationTypes=article&publisherId=$publisherId&allPublications=true&groupBy=flight&sortBy=addTime&sortOrderDesc=true&pageSize=1&page=0"
        val jsonResponse = getJson(requestUrl)
        val actuality = jsonResponse?.long("actuality")?.toInstant()
        return actuality?.toStringDDMMYY()
    }


    suspend fun getPublicationStat(publicationId: String): Stats? {
        val requestUrl = "https://dzen.ru/media-api/publication-view-stat?publicationId=$publicationId"
        return getData(requestUrl)
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
        customHeaders: HeadersBuilder.() -> Unit = defaultHeaders
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
        noinline customHeaders: HeadersBuilder.() -> Unit = defaultHeaders
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
        customHeaders: HeadersBuilder.() -> Unit = defaultHeaders
    ): HttpResponse {
        return client.get(requestUrl) {
            headers {
                customHeaders()
            }
        }
    }

}


/*
            localStats.views = Math.max(localStats.views, localStatsOld.views, 0);
            localStats.viewsTillEnd = Math.max(localStats.viewsTillEnd, localStatsOld.viewsTillEnd, 0);
            localStats.sumViewTimeSec = Math.max(localStats.sumViewTimeSec, localStatsOld.sumViewTimeSec, 0);
            localStats.comments = Math.max(localStats.comments, localStatsOld.comments, 0);
 */


/*
{
"publicationId": "66d80100c04b693827f257e0",
"views": 49,
"viewsTillEnd": 30
}
 */


@Serializable
data class Stats(val publicationId: String, val views: Int, val viewsTillEnd: Int)