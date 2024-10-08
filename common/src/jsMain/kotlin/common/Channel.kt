package common

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.RequestCredentials
import org.w3c.fetch.RequestInit
import org.w3c.fetch.SAME_ORIGIN
import kotlin.js.Json

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
class Channel(private val id: String, private val useShortname: Boolean = false) {

    private var json: Json? = null

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
            val response = window.fetch(
                url, RequestInit(
                    credentials = RequestCredentials.SAME_ORIGIN,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Apple-iPhone7C2/1202.466; U; CPU like Mac OS X; en) AppleWebKit/420+ (KHTML, like Gecko) Version/3.0 Mobile/1A543 Safari/419.3",
                        "Zen-Client-Experiments" to "zen-version:2.189.0",
                        "Zen-Features" to "{\"no_amp_links\":true,\"forced_bulk_stats\":true,\"blurred_preview\":true,\"big_card_images\":true,\"complaints_with_reasons\":true,\"pass_experiments\":true,\"video_providers\":[\"yandex-web\",\"youtube\",\"youtube-web\"],\"screen\":{\"dpi\":96},\"need_background_image\":true,\"color_theme\":\"white\",\"no_small_auth\":true,\"need_main_color\":true,\"need_zen_one_data\":true,\"interests_supported\":true,\"return_sources\":true,\"screens\":[\"feed\",\"category\",\"categories\",\"profile\",\"switchable_subs\",\"suggest\",\"blocked\",\"preferences\",\"subscribers\",\"blocked_suggest\",\"video_recommend\",\"language\",\"send_app_link_sms\",\"comments_counter\",\"social_profile\",\"social_activity_feed\",\"social_profile_edit\",\"social_interests_feedback\",\"profile_onboarding_shown\",\"profile_complete_onboarding\",\"profile_deactivate\",\"profile_cancel_deactivate\"],\"stat_params_with_context\":true,\"card_types\":[\"post\"]}"
                    )
                )
            ).await()

            if (response.ok) {
                json = response.json().await() as Json?
            } else {
                json = null
                console.error("Error fetching data: ${response.statusText}")
            }
        } catch (e: Throwable) {
            json = null
            console.error("Exception during fetch: ${e.message}")
        }
    }

    suspend fun getLastPostCard(imgSize: String? = null): Excerpt? {
        if (json == null) load()
        if (json == null) return null
        val items = json.unsafeCast<Json>()["items"] as? Array<dynamic> ?: return null
        val item = items.firstOrNull { item ->
            val type = item["type"] as? String
            type == "card" || type == "brief"
        } ?: return null
        val type = item["type"] as? String
        return when (type) {
            "card" -> Excerpt(
                title = item["title"] as? String ?: "",
                text = item["text"] as? String ?: "",
                link = (item["link"] as? String)?.split("?")?.firstOrNull() ?: "",
                imageUrl = (item["image"] as? String)?.replace(
                    "smart_crop_344x194",
                    imgSize ?: "smart_crop_336x116"
                )
            )

            "brief" -> Excerpt(
                title = (item["rich_text"] as? Json)?.let { jsonToText(it) },
                text = "",
                link = item["share_link"] as? String ?: "",
                imageUrl = briefImage(item as Json)
            )

            else -> null
        }
    }

    fun briefImage(item: Json): String? {
        val items = item["items"] as? Array<dynamic> ?: return null
        for (b in items) {
            val block = b as Json
            return (block["image"] as? Json)?.get("link") as String?
        }
        return null
    }

    fun jsonToText(obj: Json): String? {
        val items = obj as? Array<dynamic>
        return items?.filter {
            it["type"] == "text"
        }?.joinToString { it["data"] as? CharSequence ?: "" }
            ?.replace("\r\n", " ")
            ?.replace("\n", " ")
            ?.replace("  ", "")
            ?.trim()
    }
}

data class Excerpt(val title: String?, val text: String?, val link: String?, val imageUrl: String?)