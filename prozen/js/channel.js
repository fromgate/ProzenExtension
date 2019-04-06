const ZEN_API_URL = "https://zen.yandex.ru/api/v3/launcher/more?country_code=ru&";
class Channel {
    constructor (id, useShortname) {
        this.id = id;
        this.useShortname = useShortname === undefined ? false : useShortname;
        this.items = {};
    }

    async load() {
        const url = this.getApiUrl();
        this.json = await fetch(url, {credentials: 'same-origin', headers: {'Access-Control-Allow-Origin' : '*'}},).then(response => response.json());
    }

    getApiUrl() {
        return ZEN_API_URL+ (this.useShortname ? "channel_name=" : "channel_id=") +this.id;
    }

    getUrl() {
        return "https://zen.yandex.ru/"+( useShortName ? id : "id/"+id);
    }

    async getLastPostCard(imgSize) {
        if (this.json === undefined) {
            await this.load();
            return this.getLastPostCard(imgSize );
        }
        const lastPost = {};
        lastPost.title = this.json.items[0].title;
        lastPost.text = this.json.items[0].text;
        lastPost.link = this.json.items[0].link.split("\?")[0];
        lastPost.image = this.json.items[0].image.replace("smart_crop_344x194", imgSize === undefined ? "smart_crop_336x116" : imgSize);
        return lastPost;
    }
}

