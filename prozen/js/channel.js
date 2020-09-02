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
                'Zen-Client-Experiments': "zen-version:2.189.0",
                'Zen-Features': "{\"no_amp_links\":true,\"forced_bulk_stats\":true,\"blurred_preview\":true,\"big_card_images\":true,\"complaints_with_reasons\":true,\"pass_experiments\":true,\"video_providers\":[\"yandex-web\",\"youtube\",\"youtube-web\"],\"screen\":{\"dpi\":96},\"need_background_image\":true,\"color_theme\":\"white\",\"no_small_auth\":true,\"need_main_color\":true,\"need_zen_one_data\":true,\"interests_supported\":true,\"return_sources\":true,\"screens\":[\"feed\",\"category\",\"categories\",\"profile\",\"switchable_subs\",\"suggest\",\"blocked\",\"preferences\",\"subscribers\",\"blocked_suggest\",\"video_recommend\",\"language\",\"send_app_link_sms\",\"comments_counter\",\"social_profile\",\"social_activity_feed\",\"social_profile_edit\",\"social_interests_feedback\",\"profile_onboarding_shown\",\"profile_complete_onboarding\",\"profile_deactivate\",\"profile_cancel_deactivate\"],\"stat_params_with_context\":true,\"card_types\":[\"post\"]}"
            }
        }).then(response => response.json()).catch(() => undefined);
    }

    getApiUrl() {
        return ZEN_API_URL+ (this.useShortname ? "channel_name=" : "channel_id=") +this.id;
    }

    getUrl() {
        return "https://zen.yandex.ru/"+( useShortname ? id : "id/"+id);
    }

    stripHtml(str) {
        if ((str===null) || (str==='')) {
            return "";
        } else  {
            str = str.toString();
        }
        return str.replace(/<[^>]*>/g, ' ').replace(/\s{2,}/,' ');
    }

    async getLastPostCard(imgSize) {
        if (this.json === undefined) {
            await this.load();
        }
        if (this.json === undefined) {
            return undefined;
        }

        let item = null;
        for (let i =0; i< this.json.items.length; i++) {
            if (this.json.items[i].type === "card") {
                item = this.json.items[i];
                break;
            }
        }

        if (item === null) {
            return undefined;
        }

        const lastPost = {};
        if (item.type === "post") {
            lastPost.image = item.image.link !== undefined ? item.image.link.replace("post_crop_big_360", imgSize === undefined ? "smart_crop_336x116" : imgSize) : "";
            lastPost.title = this.stripHtml(item.rich_text.html);
            lastPost.link = item.link.split("?")[0];
            lastPost.text = "";
        } else {
            lastPost.title = item.title;
            lastPost.text = item.text;
            lastPost.link = item.link.split("\?")[0];
            lastPost.image = item.image.replace("smart_crop_344x194", imgSize === undefined ? "smart_crop_336x116" : imgSize);
        }
        if (!lastPost.image.endsWith("smart_crop_336x116")) {
            const newImage = await this.getCorrectImage(lastPost.link);
            if (newImage !== undefined && newImage !== null ) {
                lastPost.image = newImage;
            }
        }
        return lastPost;
    }

    async getCorrectImage(url) {
        const html = await fetch(url)
            .then(response => response.text())
            .then(str => (new window.DOMParser()).parseFromString(str, "text/html"));
        if (!html) {
            return null;
        }
        const initData = html.getElementById("init_data");
        const json = JSON.parse(initData.innerText);
        if (!json) {
            return null;
        }
        const imageId = json.publication.content.preview.image.id;
        if (!imageId) {
            return null;
        }
        const imageData = json.images[imageId];
        return "https://avatars.mds.yandex.net/get-" + imageData.namespace + "/"
            + imageData.groupId +"/" + imageData.imageName +"/" + "smart_crop_336x116";
    }
}