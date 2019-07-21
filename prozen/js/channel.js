const ZEN_API_URL = "https://zen.yandex.ru/api/v3/launcher/more?country_code=ru&clid=700&";

class Channel {
    constructor (id, useShortname) {
        this.id = id;
        this.useShortname = useShortname === undefined ? false : useShortname;
        this.items = {};
    }

    async load() {
        const url = this.getApiUrl();
        this.json = await fetch(url, {credentials: 'same-origin',
            headers: {'User-Agent': "Mozilla/5.0 (Apple-iPhone7C2/1202.466; U; CPU like Mac OS X; en) AppleWebKit/420+ (KHTML, like Gecko) Version/3.0 Mobile/1A543 Safari/419.3",
                'Zen-Client-Experiments': "zen-version:2.32.0",
                'Zen-Features': "{\"no_amp_links\":true,\"forced_bulk_stats\":true,\"blurred_preview\":true,\"big_card_images\":true,\"complaints_with_reasons\":true,\"pass_experiments\":true,\"video_providers\":[\"yandex-web\",\"youtube\",\"youtube-web\"],\"screen\":{\"dpi\":241},\"need_background_image\":true,\"color_theme\":\"white-background\",\"no_small_auth\":true,\"need_main_color\":true,\"need_zen_one_data\":true,\"interests_supported\":true,\"return_sources\":true,\"screens\":[\"feed\",\"category\",\"categories\",\"profile\",\"switchable_subs\",\"suggest\",\"blocked\",\"preferences\",\"blocked_suggest\",\"video_recommend\",\"language\",\"comments_counter\"],\"stat_params_with_context\":true,\"native_onboarding\":true,\"card_types\":[\"post\"]}"
            }
        }).then(response => response.json()).catch(() => undefined);
    }

    getApiUrl() {
        return ZEN_API_URL+ (this.useShortname ? "channel_name=" : "channel_id=") +this.id;
    }

    getUrl() {
        return "https://zen.yandex.ru/"+( useShortname ? id : "id/"+id);
    }

    async getLastPostCard(imgSize) {
        if (this.json === undefined) {
            await this.load();
        }
        if (this.json === undefined) {
            return undefined;
        }
        const item = this.json.items[0];
        const lastPost = {};
        console.log (item);
        if (item.type === "post") {
            lastPost.image = item.image.link !== undefined ? item.image.link.replace("post_crop_big_1080", imgSize === undefined ? "smart_crop_336x116" : imgSize) : "";
            lastPost.title = item.rich_text.html;
            lastPost.link = item.link.split("?")[0];
            lastPost.text = "";
        } else {
            lastPost.title = item.title;
            lastPost.text = item.text;
            lastPost.link = item.link.split("\?")[0];
            lastPost.image = item.image.replace("smart_crop_344x194", imgSize === undefined ? "smart_crop_336x116" : imgSize);
        }
        return lastPost;
    }
}