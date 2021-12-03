const URL_API_MEDIA = "https://zen.yandex.ru/media-api/id/";
const URL_API_PUBLICATION_VIEW_STAT = "https://zen.yandex.ru/media-api/publication-view-stat?publicationId=";
const URL_API_GET_PUBLICATION = "https://zen.yandex.ru/media-api/get-publication?publicationId=";

class Requester {
    constructor(publisherId, token) {
        this.publisherId = publisherId;
        this.token = token;
    }

    setPublisherId(publisherId) {
        this.publisherId = publisherId;
    }

    setToken(token) {
        this.token = token;
    }

    // TODO перенести все методы в класс
}

async function getSideBlockData() {
    const requestUrl = `https://zen.yandex.ru/editor-api/v2/publisher/${publisherId}/side-block`;
    const response = await request(requestUrl);
    const data = await response.json();
    const result = {};
    result.karmaStatus = data.karma.karma.status;
    result.karmaValue = data.karma.karma.karma[0].values.finalScore;
    result.bonusShows = data.karma.karma.totalBonusShows;
    result.publisherId = data.publisher.publisher.id;
    result.metrikaCounterId = data.publisher.publisher.privateData.metrikaCounterId;
    result.publicationsPublished = data.publicationsCount.published;
    result.publicationsDraft = data.publicationsCount.draft;
    result.publicationsDelayed = data.publicationsCount.delayed;
    return result;
}

// TODO перенести все запросы в класс
async function getBalanceAndMetriksId() {
    const result = {money: null, total: null, balanceDate: null, metriksId: null}
    const requestUrl = URL_API_MEDIA + publisherId + "/money";
    const response = await request(requestUrl);
    const data = await response.json();
    if (data.money && data.money.isMonetizationAvailable && data.money.simple != null && data.money.simple.balance != null) {
        const simpleBalance = data.money.simple.balance;
        const options = {year: 'numeric', month: 'long', day: 'numeric'};
        result.balanceDate = new Date(data.money.simple.balanceDate).toLocaleString("ru-RU", options);
        if (data.money.simple.personalData != null) {
            const personalDataBalance = data.money.simple.personalData.balance;
            const money = parseFloat((simpleBalance > personalDataBalance ? simpleBalance : personalDataBalance));

            let total = money;
            for (let i = 0, len = data.money.simple.paymentHistory.length; i < len; i++) {
                if (data.money.simple.paymentHistory[i]["status"] === "completed") {
                    total += parseFloat(data.money.simple.paymentHistory[i]["amount"]);
                }
            }
            result.money = money.toLocaleString("ru-RU", {maximumFractionDigits: 2});
            result.total = total.toLocaleString("ru-RU", {maximumFractionDigits: 2})
        }
    }
    result.metriksId = data.publisher.privateData.metrikaCounterId;
    return result;
}

async function getStrikesInfo() {
    const requestUrl = `https://zen.yandex.ru/editor-api/v2/v2/get-strikes?publisherId=${publisherId}&language=ru`
    const response = await request(requestUrl);
    const data = await response.json();
    return {channelRestricted: data.channelRestricted, limitations: data.limitations.length};
}

function getStatsInfoAndCounter() {
    const publicationTypes = ["article", "gif", "gallery", "brief", "live"];
    const promises = []
    for (const type of publicationTypes) {
        const requestUrl = `https://zen.yandex.ru/editor-api/v2/publisher/${publisherId}/stats2?fields=views&publicationTypes=${type}&publisherId=${publisherId}&allPublications=true&groupBy=flight&sortBy=addTime&sortOrderDesc=true&pageSize=1&page=0`;
        promises.push(new Promise(resolve => {
            request(requestUrl)
                .then(response => {
                    response.json().then(data => {
                        const counter = {}
                        counter.actuality = dateTimeFormat(data.actuality);
                        counter[type] = data.publicationCount;
                        resolve(counter);
                    })
                })
        }));
    }
    return Promise.all(promises);
}

function getStatsActuality() {
    const requestUrl = `https://zen.yandex.ru/editor-api/v2/publisher/${publisherId}/stats2?fields=views&publicationTypes=article&publisherId=${publisherId}&allPublications=true&groupBy=flight&sortBy=addTime&sortOrderDesc=true&pageSize=1&page=0`;
    return new Promise(resolve => {
        request(requestUrl).then(response => {
            response.json().then(data => {
                const actuality = dateTimeFormat(data.actuality);
                resolve(actuality);
            })
        })
    })
}

async function getStatsInfo(getCounter = false) {
    const publicationTypes = ["article", "gif", "gallery", "brief", "live"];
    const counters = {};
    let actuality;
    for (const type of publicationTypes) {
        const requestUrl = `https://zen.yandex.ru/editor-api/v2/publisher/${publisherId}/stats2?fields=views&publicationTypes=${type}&publisherId=${publisherId}&allPublications=true&groupBy=flight&sortBy=addTime&sortOrderDesc=true&pageSize=1&page=0`;
        const response = await request(requestUrl);
        const data = await response.json();
        if (actuality == null) {
            actuality = dateTimeFormat(data.actuality);
            if (!getCounter) {
                break;
            }
        }
        counters[type] = data.publicationCount;
    }
    return {actuality: actuality, counters: counters};
}


function loadPublicationStat(publicationId) {
    const url = URL_API_PUBLICATION_VIEW_STAT + encodeURIComponent(publicationId);
    return request(url).then(response => response.json());
}

function loadArticle(publicationId) {
    const url = URL_API_GET_PUBLICATION + publicationId;
    return request(url).then(response => response.json());
}

const COUNT_PUBLICATIONS_API_URL = "https://zen.yandex.ru/media-api/count-publications-by-state?state=published&type=";

function loadPublicationsCount(publicationType) {
    const url = COUNT_PUBLICATIONS_API_URL + encodeURIComponent(publicationType) + "&publisherId=" + publisherId;
    return request(url).then(response => response.json());
}

const GET_PUBLICATIONS_API_URL = "https://zen.yandex.ru/media-api/get-publications-by-state?state=published&pageSize=";

function loadPublications(publicationType, count) {
    const url = GET_PUBLICATIONS_API_URL + encodeURIComponent(count) + "&type=" + encodeURIComponent(publicationType) + "&publisherId=" + publisherId;
    return request(url).then(response => response.json());
}

// deprecated?
const TYPES = ["article", "gif", "gallery", "brief", "narrative", "post"]; // repost?
/*
async function loadAllPublications() {
    const publications = [];
    for (let i = 0; i < TYPES.length; i++) {
        const publicationType = TYPES[i];
        const response = await loadPublicationsCount(publicationType).then(response => {
            return response;
        });
        const count = response.count;
        const result = await loadPublications(publicationType, count).then(response => {
            const cards = [];
            for (let i = 0, len = response.publications.length; i < len; i++) {
                const pubData = {};
                const publication = response.publications[i];
                pubData.id = publication.id;
                pubData.feedShows = publication.privateData.statistics.feedShows;
                pubData.shows = publication.privateData.statistics.shows;
                pubData.views = publication.privateData.statistics.views;
                pubData.viewsTillEnd = publication.privateData.statistics.viewsTillEnd;
                pubData.comments = publication.privateData.statistics.comments;
                pubData.likes = publication.privateData.statistics.likes;
                pubData.sumViewTimeSec = publication.privateData.statistics.sumViewTimeSec;
                pubData.addTime = publication.addTime !== undefined ? publication.addTime : 0;
                pubData.type = publication.content.type;
                cards.push(pubData);
            }
            return cards;
        });
        publications.push(...result);
    }
    return publications;
} */

async function loadAllPublications(sort = false) {
    const publications = [];
    let recordCount = 0;
    for (let i = 0; i < TYPES.length; i++) {
        const publicationType = TYPES[i];
        const response = await loadPublicationsCount(publicationType).then(response => {
            return response;
        });
        const count = response.count;
        const result = await loadPublications(publicationType, count).then(response => {
            const cards = [];
            if (response !== undefined && response.publications !== undefined) {
                for (let i = 0, len = response.publications.length; i < len; i++) {
                    const pubData = {};
                    const publication = response.publications[i];
                    pubData.id = publication.id;
                    pubData.feedShows = publication.privateData.statistics.feedShows;
                    pubData.shows = publication.privateData.statistics.shows;
                    pubData.views = publication.privateData.statistics.views;
                    pubData.viewsTillEnd = publication.privateData.statistics.viewsTillEnd;
                    pubData.comments = publication.privateData.statistics.comments;
                    pubData.likes = publication.privateData.statistics.likes;
                    pubData.sumViewTimeSec = publication.privateData.statistics.sumViewTimeSec;
                    pubData.addTime = publication.addTime !== undefined ? publication.addTime : 0;
                    pubData.type = publication.content.type;
                    pubData.tags = new Set(publication.privateData.tags);
                    pubData.title = publication.content.preview.title;
                    pubData.description = publication.content.preview.snippet;
                    pubData.url = `https://zen.yandex.ru/media/id/${publisherId}/${publication.id}`;
                    cards.push(pubData);
                    recordCount++;
                }
            }
            return cards;
        });
        publications.push(...result);
    }

    if (sort) {
        publications.sort((a, b) => {
            const addTimeA = a.addTime;
            const addTimeB = b.addTime;
            if (addTimeA < addTimeB) return 1;
            if (addTimeA > addTimeB) return -1;
            return 0;
        });
    }
    return publications;
}

const URL_API_PUBLICATIONS = "https://zen.yandex.ru/media-api/publisher-publications-stat?publicationsIds=";

function loadPublicationsStat(publicationIds) {
    const url = URL_API_PUBLICATIONS + encodeURIComponent(publicationIds.join(",")) + "&publisherId=" + publisherId;
    return request(url).then(response => response.json());
}

const URL_API_COUNT_PUBLISHED = "https://zen.yandex.ru/media-api/count-publications-by-state?state=published&publisherId=";
const URL_API_PUBLICATIONS_PUBLISHED = "https://zen.yandex.ru/media-api/get-publications-by-state?state=published&pageSize=%pageSize%&publisherId=%publisherId%";

function loadPublicationsPublisher() {
    const countUrl = URL_API_COUNT_PUBLISHED + publisherId;
    return fetch(countUrl, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}})
        .then(response => response.json())
        .then(data => {
            const pageSize = data.count;
            const url = URL_API_PUBLICATIONS_PUBLISHED.replace("%pageSize%", pageSize).replace("%publisherId%", publisherId);
            return request(url).then(response => response.json());
        });
}

async function countGroupedPublicationsByType() {
    const requestUrl = `https://zen.yandex.ru/editor-api/v2/count-grouped-publications-by-type?publisherId=${publisherId}`;
    const response = await request(requestUrl);
    return await response.json();
}

async function getPublicationsByFilter(pageSize, types, publicationIdAfter, query) {
    const url = new URL("editor-api/v2/get-publications-by-filter", "https://zen.yandex.ru");
    url.searchParams.set("group", "published");
    url.searchParams.append("publisherId", publisherId);
    url.searchParams.append("pageSize", pageSize != null ? pageSize : 5);
    if (types != null) {
        url.searchParams.append("types", types);
    }
    if (publicationIdAfter != null) {
        url.searchParams.append("publicationIdAfter", publicationIdAfter);
    }
    if (query != null) {
        url.searchParams.append("query", query);
    }
    const response = await request(url.href);
    const data = await response.json();
    return data;
}

async function getAllPublications() {
    const counters = await countGroupedPublicationsByType()
    const promises = [];
    for (const [key, value] of Object.entries(counters.countedPublicationsByType)) {
        promises.push(getPublicationsByFilter(value, key));
    }
    const data = await Promise.all(promises);

    const publications = [];
    for (const result of data) {
        publications.push(...result.publications);
    }
    return publications;
}

function checkHasNone(id) {
    let url = `https://zen.yandex.ru/id/${id}`;
    if (id.startsWith("channel_name")) {
        url = `https://zen.yandex.ru/${id.replace("channel_name=", "")}`;
    } else if (id.startsWith("channel_id")) {
        url = `https://zen.yandex.ru/id/${id.replace("channel_id=", "")}`;
    }
    return checkHasNoneUrl(url);
}

function checkHasNoneUrl(url) {
    return new Promise(resolve => {
        const xhr = new XMLHttpRequest();
        xhr.onload = () => {
            const metas = xhr.responseXML.head.getElementsByTagName("meta");
            let hasNone = false;
            for (const meta of metas) {
                if (meta.getAttribute("property") === "robots"
                    && meta.getAttribute("content") === "none") {
                    hasNone = true;
                    break;
                }
            }
            resolve(hasNone);
        };
        xhr.open("GET", url);
        xhr.responseType = "document";
        xhr.send();
    });
}

async function loadPageData(initUrl, loadAll) {
    const header = new Headers({
        "Zen-Client-Experiments": "zen-version:2.32.0",
        "Zen-Features": "{\"no_amp_links\":true,\"forced_bulk_stats\":true,\"blurred_preview\":true,\"big_card_images\":true,\"complaints_with_reasons\":true,\"pass_experiments\":true,\"video_providers\":[\"yandex-web\",\"youtube\",\"youtube-web\"],\"screen\":{\"dpi\":241},\"need_background_image\":true,\"color_theme\":\"white-background\",\"no_small_auth\":true,\"need_main_color\":true,\"need_zen_one_data\":true,\"interests_supported\":true,\"return_sources\":true,\"screens\":[\"feed\",\"category\",\"categories\",\"profile\",\"switchable_subs\",\"suggest\",\"blocked\",\"preferences\",\"blocked_suggest\",\"video_recommend\",\"language\",\"comments_counter\"],\"stat_params_with_context\":true,\"native_onboarding\":true,\"card_types\":[\"post\"]}"
    });

    let url = initUrl;
    const cards = [];
    while (true) {
        const request = await fetch(url, {headers: header, method: "GET"});
        let json;
        try {
            json = await request.json();
        } catch (e) {
        }
        if (!request.ok || json === undefined || json.items === undefined) {
            break;
        }
        const items = json.items;
        for (let i = 0; i < items.length; i++) {
            const item = items[i];
            if (item.link !== undefined && item.link.startsWith("https://zen.yandex.ru")) {
                cards.push(cardFromItem(item));
            }
        }
        if (loadAll) {
            url = json.more.link;
        } else {
            break;
        }
    }
    return cards;
}

async function monthlySubscribers() {
    const requestUrl = `https://zen.yandex.ru/editor-api/v2/publisher/${publisherId}/monthly-subscribers`;
    const response = await request(requestUrl);
    return await response.json();
}

async function getBannedUsers() {
    const requestUrl = "https://zen.yandex.ru/api/comments/banned-users";
    const response = await request(requestUrl);
    return await response.json();
}

/* Deprecated */
async function getUserKarma() {
    const requestUrl = `https://zen.yandex.ru/editor-api/v2/get-user-karma?publisherId=${publisherId}`
    const response = await request(requestUrl);
    return await response.json();
}

function request(requestUrl) {
    const headers = {
        credentials: "same-origin",
        headers: {
            "X-Prozen-Request": "countGroupedPublicationsByType"
        }
    }
    if (typeof token !== "undefined") {
        headers.headers["X-Csrf-Token"] = token
    }
    return fetch(requestUrl, headers);
}