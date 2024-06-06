const URL_API_MEDIA = "https://dzen.ru/media-api/id/";
const TYPES = ["article", "gif", "short_video", "gallery", "brief"]; // repost?

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

// https://zen.yandex.ru/editor-api/v2/id/60034fda05966372926b1a79/money
async function getSideBlockData() {
    debugger
    const requestUrl = `https://dzen.ru/editor-api/v2/publisher/${publisherId}/side-block`;
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

/**
 * SCR — Subscribers Coverage Rate
 *
 * @param from начальная дата в формате YYYY-MM-DD
 * @param to конечная дата в формате YYYY-MM-DD
 * @returns {Promise<number>}
 */
async function getSCR(from, to) {
    const requestUrl = `https://dzen.ru/editor-api/v2/publisher/${publisherId}/stats2?allPublications=true&addTimeFrom=${from}&addTimeTo=${to}&fields=typeSpecificViews&fields=deepViewsRate&fields=ctr&fields=vtr&fields=shares&fields=comments&fields=subscriptions&fields=likes&fields=unsubscriptions&fields=viewMap&fields=subscribers&fields=subscribersDiff&fields=impressions&fields=deepViews&fields=sumInvolvedViewTimeSec&groupBy=flight&sortBy=addTime&sortOrderDesc=true&total=true&totalLimitedByIds=false&isSubscriber=true&pageSize=100&page=0&clid=320`;
    const response = await request(requestUrl);
    const data = await response.json();


    const totalViews = data?.total?.stats?.impressions;
    const openingSubscribers = data?.openingSubscribers;
    const count = data?.publicationCount;

    if (totalViews == null || openingSubscribers == null || count == null) return null;

    return numFormat(((totalViews / count) / openingSubscribers) * 100, 2);
}

//
/**
 *
 * @param from начальная дата в формате YYYY-MM-DD
 * @param to конечная дата в формате YYYY-MM-DD
 * @returns {Promise<*[]>}
 */
async function getTimespentRewards(from, to) {
    const rewards = [];
    const requestUrl = `https://dzen.ru/editor-api/v2/publisher/${publisherId}/income2?from=${from}&to=${to}&orderBy=totalIncome&ascending=false&page=0&pageSize=50&total=true`;
    const response = await request(requestUrl);
    const data = await response.json();
    if (data?.total?.intervals?.length > 0) {
        for (const rewardData of data.total.intervals) {
            const reward = {
                timestamp: rewardData.timestamp,
                income: rewardData.income["timespent-reward"],
                viewTimeSec: rewardData.viewTimeSec
            };
            if (reward.viewTimeSec != null && reward.viewTimeSec > 0) {
                reward.dateStr = dateFormat(reward.timestamp, false);
                reward.course = reward.income / (reward.viewTimeSec / 60);
                reward.courseStr = reward.course.toFixed(3);
                rewards.push(reward);
            }
        }
    }
    return rewards;
}

// TODO перенести все запросы в класс
async function getBalanceAndMetriksId() {
    const result = {money: null, total: null, balanceDate: null, metriksId: null};
    const requestUrl = `https://dzen.ru/editor-api/v2/id/${publisherId}/money`;
    const response = await request(requestUrl);
    const data = await response.json();

    try {
        if (data.money && data.money.isMonetizationAvailable && data.money.simple != null && data.money.simple.balance != null) {
            const simpleBalance = data.money.simple.balance;
            const options = {year: "numeric", month: "long", day: "numeric"};
            result.balanceDate = new Date(data.money.simple.balanceDate).toLocaleString("ru-RU", options);
            const personalDataBalance = data.money?.simple?.balance;
            const money = parseFloat((personalDataBalance == null || simpleBalance > personalDataBalance ? simpleBalance : personalDataBalance));

            let total = money;
            for (let i = 0, len = data.money.simple.paymentHistory.length; i < len; i++) {
                if (data.money.simple.paymentHistory[i]["status"] === "completed") {
                    total += parseFloat(data.money.simple.paymentHistory[i]["amount"]);
                }
            }
            result.money = money.toLocaleString("ru-RU", {maximumFractionDigits: 2});
            result.total = total.toLocaleString("ru-RU", {maximumFractionDigits: 2});
        }

        result.metriksId = data.publisher.privateData.metrikaCounterId;
        return result;
    } catch (e) {
        console.error("PROZEN getBalanceAndMetriksId() error: ", e);
        return result;
    }

}

async function getStrikesInfo() {
    const requestUrl = `https://dzen.ru/editor-api/v2/v2/get-strikes?publisherId=${publisherId}&language=ru`;
    const response = await request(requestUrl);
    const data = await response.json();
    return {channelRestricted: data.channelRestricted, limitations: data.limitations.length};
}

function getStatsInfoAndCounter() {
    const publicationTypes = ["article", "gif", "gallery", "brief", "live"];
    const promises = [];
    for (const type of publicationTypes) {
        const requestUrl = `https://dzen.ru/editor-api/v2/publisher/${publisherId}/stats2?fields=views&publicationTypes=${type}&publisherId=${publisherId}&allPublications=true&groupBy=flight&sortBy=addTime&sortOrderDesc=true&pageSize=1&page=0`;
        promises.push(new Promise(resolve => {
            request(requestUrl)
                .then(response => {
                    response.json().then(data => {
                        const counter = {};
                        counter.actuality = dateTimeFormat(data.actuality);
                        counter[type] = data.publicationCount;
                        resolve(counter);
                    });
                })
                .catch(e => console.error("PROZEN getStatsInfoAndCounter error: ", e));
        }));
    }
    return Promise.all(promises);
}

function getStatsActuality() {
    const requestUrl = `https://dzen.ru/editor-api/v2/publisher/${publisherId}/stats2?fields=views&publicationTypes=article&publisherId=${publisherId}&allPublications=true&groupBy=flight&sortBy=addTime&sortOrderDesc=true&pageSize=1&page=0`;
    return new Promise(resolve => {
        request(requestUrl).then(response => {
            response.json().then(data => {
                const actuality = dateTimeFormat(data.actuality);
                resolve(actuality);
            });
        });
    });
}

async function getStatsInfo(getCounter = false) {
    const publicationTypes = ["article", "gif", "gallery", "brief", "live"];
    const counters = {};
    let actuality = null;
    for (const type of publicationTypes) {
        const requestUrl = `https://dzen.ru/editor-api/v2/publisher/${publisherId}/stats2?fields=views&publicationTypes=${type}&publisherId=${publisherId}&allPublications=true&groupBy=flight&sortBy=addTime&sortOrderDesc=true&pageSize=1&page=0`;
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
    const requestUrl = `https://dzen.ru/media-api/publication-view-stat?publicationId=${publicationId}`;
    return request(requestUrl).then(response => response.json());
}

function loadArticle(publicationId) {
    const requestUrl = `https://dzen.ru/media-api/get-publication?publicationId=${publicationId}`;
    return request(requestUrl).then(response => response.json());
}

function loadPublicationsCount(publicationType) {
    const url = `https://dzen.ru/media-api/count-publications-by-state?state=published&type=${publicationType}&publisherId=${publisherId}`;
    return request(url).then(response => response.json());
}

function loadPublications(publicationType, count) {
    const url = `https://dzen.ru/media-api/get-publications-by-state?state=published&pageSize=${count}&type=${publicationType}&publisherId=${publisherId}`;
    return request(url).then(response => response.json());
}

async function loadAllPublications(sort = false) {
    const publications = [];
    const maxPageSize = 500;
    const counts = await getPublishedPublicationsCount();
    for (const [publicationType, count] of Object.entries(counts)) {
        let leftPublications = count;
        let publicationIdAfter = undefined;
        while (leftPublications > 0) {
            const countToGet = leftPublications > maxPageSize ? maxPageSize : leftPublications;
            leftPublications -= countToGet;
            const result = await getPublicationsByView(countToGet, publicationType, undefined, undefined, publicationIdAfter);
            const cards = publicationDataToArray(result);
            publicationIdAfter = cards.slice(-1)[0].id;
            publications.push(...cards);
        }
    }
    if (sort) {
        publications.sort((a, b) => {
            return b.addTime - a.addTime;
        });
    }
    return publications;
}

/**
 * Конвертирует данные, полученные от getPublicationsByView в массив «карточек» публикаций
 * @param publicationData
 */
function publicationDataToArray(publicationData) {
    const cards = [];
    for (let i = 0, len = publicationData.publications.length; i < len; i++) {
        const pubData = {};
        const publication = publicationData.publications[i];

        const counter = publicationData.publicationCounters[i];
        const socials = publicationData.socialCounters[i];
        if (publication.id === counter.publicationId && publication.id === socials.publicationId) {
            pubData.id = publication.id;
            pubData.impressions = counter.impressions;
            pubData.clicks = counter.clicks;
            pubData.shares = counter.shares;
            pubData.deepViews = counter.deepViews;
            pubData.typeSpecificViews = counter.typeSpecificViews;
            pubData.subscriptions = counter.subscriptions;
            pubData.sumViewTimeSec = counter.sumViewTimeSec;
            pubData.commentCount = socials.commentCount;
            pubData.likeCount = socials.likeCount;
            pubData.type = publication.content.type;
            pubData.addTime = publication.addTime;
            pubData.publishTime = publication.publishTime;
            pubData.modTime = publication.content.modTime;
            pubData.title = publication.content.preview.title;
            pubData.snippet = publication.content.preview.snippet;
            pubData.isBanned = publication.isBanned;
            pubData.commentsState = publication.commentsFlagState;
            if (pubData.commentsState !== "off") {
                pubData.commentsState = publication.visibleComments;
            }
            pubData.url = `https://dzen.ru/media/id/${publisherId}/${publication.id}`;
            cards.push(pubData);
        }
    }
    return cards;
}

/* не используется */
function loadPublicationsStat(publicationIds) {
    const ids = encodeURIComponent(publicationIds.join(","));
    const url = `https://dzen.ru/media-api/publisher-publications-stat?publicationsIds=${ids}&publisherId=${publisherId}`;
    return request(url).then(response => response.json());
}

/* не используется */
function loadPublicationsPublisher() {
    const countUrl = `https://dzen.ru/media-api/count-publications-by-state?state=published&publisherId=${publisherId}`;
    return fetch(countUrl, {credentials: "same-origin", headers: {"X-Csrf-Token": token}})
        .then(response => response.json())
        .then(data => {
            const pageSize = data.count;
            const requestUrl = `https://dzen.ru/media-api/get-publications-by-state?state=published&${pageSize}&publisherId=${publisherId}`;
            return request(requestUrl).then(response => response.json());
        });
}

/* Deprecated - Method not allowed */
async function countGroupedPublicationsByType() {
    const requestUrl = `https://dzen.ru/editor-api/v2/count-grouped-publications-by-type?publisherId=${publisherId}`;
    const response = await request(requestUrl);
    return await response.json();
}

async function getPublicationsCount() {
    const requestUrl = `https://dzen.ru/editor-api/v2/publisher/${publisherId}/publications-count?publisherId=${publisherId}`;
    const response = await request(requestUrl);
    return await response.json();
}

async function getPublishedPublicationsCount() {
    const counters = await getPublicationsCount();
    const published = {};
    for (const [key, value] of Object.entries(counters.items)) {
        published[key] = value.published != null ? value.published : 0;
    }
    return published;
}

async function getPublicationsByView(pageSize, types, view, query, publicationIdAfter) {
    const url = new URL("editor-api/v3/publications", "https://dzen.ru");
    url.searchParams.set("state", "published");
    url.searchParams.append("pageSize", pageSize != null ? pageSize : 10);
    url.searchParams.append("publisherId", publisherId);

    if (query != null) {
        url.searchParams.append("query", query);
    }
    if (types != null) {
        url.searchParams.append("types", types);
    }

    if (view != null) {
        url.searchParams.append("view", view);
    }
    if (publicationIdAfter != null) {
        url.searchParams.append("publicationIdAfter", publicationIdAfter);
    }
    const response = await request(url.href);
    return await response.json();
}

/* Deprecate */
async function getPublicationsByFilter(pageSize, types, publicationIdAfter, query) {
    const url = new URL("editor-api/v2/get-publications-by-filter", "https://dzen.ru");
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
    return await response.json();
}

/* Deprecated */
async function getAllPublications() {
    const counters = await getPublishedPublicationsCount();
    const promises = [];
    for (const [key, value] of Object.entries(counters)) {
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
    let url = `https://dzen.ru/id/${id}`;
    if (id.startsWith("channel_name")) {
        url = `https://dzen.ru/${id.replace("channel_name=", "")}`;
    } else if (id.startsWith("channel_id")) {
        url = `https://dzen.ru/id/${id.replace("channel_id=", "")}`;
    }
    return checkHasNoneUrl(url);
}


//<meta name="robots" content="index, follow" />
function checkHasNoneUrl(url) {
    return new Promise(resolve => {
        const xhr = new XMLHttpRequest();
        xhr.onload = () => {
            const metas = xhr.responseXML.head.getElementsByTagName("meta");
            let hasNone = false;
            for (const meta of metas) {
                if ((meta.getAttribute("name") === "robots"
                        || meta.getAttribute("property") === "robots")
                    && meta.getAttribute("content").includes("noindex")) {
                    hasNone = true;
                    break;
                }
            }
            resolve(hasNone);
        };
        xhr.onerror = () => {
            resolve(false);
        };
        xhr.open("GET", url);
        xhr.responseType = "document";
        xhr.send();
    });
}


/* Не используется
async function monthlySubscribers() {
    const requestUrl =`https://dzen.ru/editor-api/v2/publisher/${publisherId}/monthly-subscribers`;
    const response = await request(requestUrl);
    return await response.json();
}
 */

async function getBannedUsers() {
    const requestUrl = "https://dzen.ru/api/comments/banned-users";
    const response = await request(requestUrl);
    return await response.json();
}

async function getPublicationsStatsSubscribers(ids) {
    const subscribersViews = [];
    if (ids != null && ids.length > 0) {
        let publicationIds;
        if (Array.isArray(ids)) {
            const idsParams = [];
            ids.forEach(id => idsParams.push(`publicationIds=${id}`));
            publicationIds = idsParams.join("&");
        } else {
            publicationIds = `publicationIds=${ids}`;
        }
        const requestUrl = `https://dzen.ru/editor-api/v2/publisher/${publisherId}/stats2?publisherId=${publisherId}&${publicationIds}&fields=typeSpecificViews&groupBy=ageGender&isSubscriber=true&from=2022-01-25`;
        const response = await request(requestUrl);
        const json = await response.json();
        const pubData = json.publications;

        if (pubData != null && pubData.length > 0) {
            pubData.forEach(stat => {
                const publicationId = stat.publication.publicationId;
                subscribersViews [publicationId] = stat.stats.typeSpecificViews;
            });
        }
    }
    return subscribersViews;
}

async function getPublicationsByFilterAndSubscribers(pageSize, types, publicationIdAfter, view, query) {
    const data = await getPublicationsByView(pageSize, types, view, query, publicationIdAfter);

    const ids = [];
    for (const publication of data.publications) {
        if (publication.publishTime >= 1643058000000) { // Запрашивать только по публикациям новее 25.01.2022
            ids.push(publication.id);
        }
    }
    const subscribersViews = await getPublicationsStatsSubscribers(ids);
    for (const publication of data.publications) {
        if (subscribersViews.hasOwnProperty(publication.id)) {
            publication.subscribersViews = subscribersViews[publication.id];
        } else {
            publication.subscribersViews = 0;
        }
    }
    return data;
}

// Дата в формате: YYYY-MM-DD
async function getStatsPage(publicationType, addTimeFrom, addTimeTo, pageSize = 100, page = 0) {
    const requestUrl = `https://dzen.ru/editor-api/v2/publisher/${publisherId}/stats2?publisherId=${publisherId}&publicationTypes=${publicationType}&allPublications=true&addTimeFrom=${addTimeFrom}&addTimeTo=${addTimeTo}&fields=shows&fields=views&fields=typeSpecificViews&fields=viewsTillEnd&fields=sumViewTimeSec&fields=viewTillEndAvgTimeSec&fields=viewTillEndRate&fields=ctr&fields=impressions&fields=comments&fields=subscriptions&fields=likes&fields=unsubscriptions&fields=subscribersDeepViews&groupBy=flight&sortBy=addTime&sortOrderDesc=true&total=true&pageSize=${pageSize}&page${page}`;
    const response = await request(requestUrl);
    const json = await response.json();
    const stats = [];
    if (json != null && json.publications != null && json.publications.length > 0) {
        json.publications.forEach(it => {
            const stat = {};
            stat.id = it.publication.publicationId;
            stat.type = it.publication.publicationType;
            stat.deleted = it.publication.deleted;
            stat.shows = it.stats.shows;
            stat.impressions = it.stats.impressions;
            stat.comments = it.stats.comments;
            stat.views = it.stats.views;
            stat.viewsTillEnd = it.stats.viewsTillEnd;
            stat.typeSpecificViews = it.stats.typeSpecificViews; // Похоже это замена дочиткам
            stat.subscribersViews = it.stats.subscribersDeepViews; // Просмотры от подписчиков?
            stat.subscriptions = it.stats.subscriptions; // Сколько подписалось
            stat.unsubscriptions = it.stats.unsubscriptions; // Сколько отписалось
            stat.sumViewTimeSec = it.stats.sumViewTimeSec;
            stats.push(stat);
        });
    }
    return stats;
}

function request(requestUrl) {
    const callerName = request.caller == null ? "FoxCall" : request.caller.name;
    const headers = {
        credentials: "same-origin",
        headers: {
            "X-Prozen-Request": callerName
        }
    };
    if (typeof token !== "undefined") {
        headers.headers["X-Csrf-Token"] = token;
    }
    return fetch(requestUrl, headers);
}

