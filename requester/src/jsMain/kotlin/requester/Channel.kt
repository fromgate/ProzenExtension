package requester

import common.arr
import common.obj
import common.string
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class Channel(private val id: String, private val useShortname: Boolean = false) {

    private var json: JsonObject? = null

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    fun getApiUrl(): String {
        val channelId = if (useShortname) "channel_name=$id" else "channel_id=$id"
        return "https://dzen.ru/api/v3/launcher/more?$channelId&country_code=ru"
    }

    fun getUrl(): String {
        return "https://dzen.ru/${if (useShortname) id else "id/$id"}"
    }

    suspend fun load() {
        val url = getApiUrl()
        try {
            val response = client.get(url) {
                headers {
                    append(
                        HttpHeaders.UserAgent,
                        "Mozilla/5.0 (Apple-iPhone7C2/1202.466; U; CPU like Mac OS X; en) AppleWebKit/420+ (KHTML, like Gecko) Version/3.0 Mobile/1A543 Safari/419.3"
                    )
                    append("Zen-Client-Experiments", "zen-version:2.189.0")
                    append(
                        "Zen-Features",
                        """{"no_amp_links":true,"forced_bulk_stats":true,"blurred_preview":true,"big_card_images":true,"complaints_with_reasons":true,"pass_experiments":true,"video_providers":["yandex-web","youtube","youtube-web"],"screen":{"dpi":96},"need_background_image":true,"color_theme":"white","no_small_auth":true,"need_main_color":true,"need_zen_one_data":true,"interests_supported":true,"return_sources":true,"screens":["feed","category","categories","profile","switchable_subs","suggest","blocked","preferences","subscribers","blocked_suggest","video_recommend","language","send_app_link_sms","comments_counter","social_profile","social_activity_feed","social_profile_edit","social_interests_feedback","profile_onboarding_shown","profile_complete_onboarding","profile_deactivate","profile_cancel_deactivate"],"stat_params_with_context":true,"card_types":["post"]}"""
                    )
                }
            }
            if (response.status == HttpStatusCode.OK) {
                json = response.body()
            }
        } catch (e: Exception) {
            console.log("Failed to get Channel Data")
        }
    }

    suspend fun getLastPostCard(imgSize: String? = null): Excerpt? {
        if (json == null) {
            load()
        }
        if (json == null) {
            return null
        }

        val item = json?.arr("items")?.find { item ->
            item.jsonObject.string("type").let { it == "card" || it == "brief" }
        }?.jsonObject ?: return null

        return if (item.string("type") == "brief") Excerpt(
            title = item.obj("rich_text.json")?.let { jsonToText(it) },
            text = "",
            link = item.string("share_link"),
            imageUrl = briefImage(item)
        ) else Excerpt(
            title = item.string("title"),
            text = item.string("text"),
            link = item.string("link"),
            imageUrl = item.string("image")?.replace("smart_crop_344x194", imgSize ?: "smart_crop_336x116")
        )
    }

    fun briefImage(item: JsonObject): String? {
        val items = item.arr("items") ?: return null
        for (block in items) {
            block.jsonObject.string("image.link")?.let { return it }
        }
        return null
    }


    fun jsonToText(jsonObj: JsonObject): String {
        return jsonObj.jsonArray
            .filter { it.jsonObject.string("type") == "text" }
            .joinToString(" ") { it.jsonObject.string("data") ?: "" }
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("  ", "")
            .trim()

    }
}

data class Excerpt(val title: String?, val text: String?, val link: String?, val imageUrl: String?)