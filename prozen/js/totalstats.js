const COUNT_PUBLICATIONS_API_URL = "https://zen.yandex.ru/media-api/count-publications-by-state?state=published&type=";
const GET_PUBLICATIONS_API_URL = "https://zen.yandex.ru/media-api/get-publications-by-state?state=published&pageSize=";
const TYPES = ["article", "narrative", "post", "gif"];

var token;
var publisherId;

main();

//////////////////////////////////////////////////////////////////////////////////////

function getToken() {
    return new Promise((resolve) => {
        chrome.storage.local.get("prozenToken", function (result) {
            resolve(result.prozenToken);
        });
    });
}

function getPublisherId() {
    return new Promise((resolve) => {
        chrome.storage.local.get("prozenPublisherId", function (result) {
            resolve(result.prozenPublisherId);
        });
    });
}

async function main() {
    showSpinner();
    token = await getToken();
    publisherId = await getPublisherId();
    const publications = await loadAllPublications();
    const stats = countStats(publications);
    showStats(stats);
    hideSpinner();
}

function showStats(stats) {
    stats.forEach((stat, publicationType) => {
        const viewsPercent = stat.feedShows === 0 || publicationType === "post" ? 0 : stat.shows / stat.feedShows * 100;
        const viewsPercentStr = viewsPercent === 0 ? "" : " (" + numFormat(viewsPercent, 2) + "%)";
        const viewsTillEndPercent = stat.viewsTillEnd === 0 ? 0 : stat.viewsTillEnd / stat.views * 100;
        const viewsTillEndPercentStr = viewsTillEndPercent === 0 ? "" : " (" + numFormat(viewsTillEndPercent, 2) + "%)";
        let minAddTimeStr = "-";
        const days = daysSinceDate(stat.minAddTime);
        if (stat.minAddTime > 0) {
            minAddTimeStr = dateFormat(stat.minAddTime);
            if (days > 0) {
                minAddTimeStr += "; Прошло: " + paucalDay(days) + " (" + daysReadable(days) + ")";
            }
        }
        const publicationsPerDayStr = stat.count === 0 && days !== 0 ? "" : " (" + numFormat(stat.count / days, 2) + " в день)";
        document.getElementById(publicationType + "-count").textContent = numFormat(stat.count) + publicationsPerDayStr;
        document.getElementById(publicationType + "-feedShows").textContent = numFormat(stat.feedShows);
        document.getElementById(publicationType + "-views").textContent = numFormat(stat.views) + viewsPercentStr;
        document.getElementById(publicationType + "-viewstillend").textContent = numFormat(stat.viewsTillEnd) + viewsTillEndPercentStr;
        document.getElementById(publicationType + "-firstpost").textContent = minAddTimeStr;
    });
}


function countStats(publications) {
    const stats = new Map();

    for (let i = 0; i < TYPES.length; i++) {
        stats.set(TYPES[i], {count: 0, feedShows: 0, shows: 0, views: 0, viewsTillEnd: 0, minAddTime: 0});
    }

    for (let i = 0; i < publications.length; i++) {
        const publication = publications[i];
        const type = publication.type;
        const stat = stats.get(type);
        stat.count++;
        stat.feedShows += publication.feedShows;
        stat.views += publication.views;
        stat.shows += publication.shows;
        stat.viewsTillEnd += publication.viewsTillEnd;
        stat.minAddTime = (publication.addTime !== undefined) && (publication.addTime < stat.minAddTime || stat.minAddTime === 0) ? publication.addTime : stat.minAddTime;
        stats.set(type, stat);
    }
    // Count totals stats
    const total = {count: 0, feedShows: 0, shows: 0, views: 0, viewsTillEnd: 0, minAddTime: 0};
    stats.forEach((stat, type) => {
        total.count += stat.count;
        total.feedShows += stat.feedShows;
        total.shows += stat.shows;
        total.views += stat.views;
        total.viewsTillEnd += stat.viewsTillEnd;
        total.minAddTime = (stat.minAddTime < total.minAddTime || total.minAddTime === 0) && stat.minAddTime > 0 ? stat.minAddTime : total.minAddTime;
    });
    stats.set("total", total);
    return stats;
}

/* Deprecated */
async function getStats(publicationType) {
    const response = await loadPublicationsCount(publicationType).then(response => {
        return response;
    });
    const count = response.count;
    const result = await loadPublications(publicationType, count).then(response => {
        let feedShows = 0;
        let shows = 0;
        let views = 0;
        let viewsTillEnd = 0;
        let minAddTime = 0;
        for (let i = 0, len = response.publications.length; i < len; i++) {
            const publication = response.publications[i];
            feedShows += publication.privateData.statistics.feedShows;
            shows += publication.privateData.statistics.shows;
            views += publication.privateData.statistics.views;
            viewsTillEnd += publication.privateData.statistics.viewsTillEnd;
            minAddTime = (publication.addTime !== undefined) && (publication.addTime < minAddTime || minAddTime === 0) ? publication.addTime : minAddTime;
        }
        return {feedShows: feedShows, shows: shows, views: views, viewsTillEnd: viewsTillEnd, minAddTime: minAddTime};
    });
    result.count = count;
    return result;
}

function loadPublicationsCount(publicationType) {
    const url = COUNT_PUBLICATIONS_API_URL + encodeURIComponent(publicationType)+"&publisherId=" + publisherId;
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}

function loadPublications(publicationType, count) {
    const url = GET_PUBLICATIONS_API_URL + encodeURIComponent(count) + "&type=" + encodeURIComponent(publicationType) + "&publisherId=" + publisherId;
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}

async function loadAllPublications() {
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
            for (let i = 0, len = response.publications.length; i < len; i++) {
                const pubData = {};
                const publication = response.publications[i];
                pubData.id = publication.id;
                pubData.feedShows = publication.privateData.statistics.feedShows;
                pubData.shows= publication.privateData.statistics.shows;
                pubData.views = publication.privateData.statistics.views;
                pubData.viewsTillEnd = publication.privateData.statistics.viewsTillEnd;
                pubData.comments = publication.privateData.statistics.comments;
                pubData.likes = publication.privateData.statistics.likes;
                pubData.sumViewTimeSec = publication.privateData.statistics.sumViewTimeSec;
                pubData.addTime = publication.addTime !== undefined ? publication.addTime : 0;
                pubData.type = publication.content.type;
                cards.push(pubData);
                recordCount++;
                document.getElementById("records_count").innerText = "Загружено: " + paucal(recordCount, " публикация"," публикации"," публикаций");
            }
            return cards;
        });
        publications.push(...result);
    }
    return publications;
}

function hideSpinner() {
    document.getElementById("spinner").style.display = "none";
    document.getElementById("stats").style.display = "block";
    document.getElementById("records_count").innerText = "";
}

function showSpinner() {
    document.getElementById("spinner").style.display = "block";
    document.getElementById("stats").style.display = "none";
    document.getElementById("records_count").innerText = "";
}

function numFormat(num, digits) {
    return num.toLocaleString(undefined, {maximumFractionDigits: digits === undefined ? 0 : digits});
}

function dateFormat(unixTime) {
    const date = new Date(unixTime);
    const day = "0" + date.getDate();
    const month = "0" + (date.getMonth() + 1);
    const year = "" + date.getFullYear();
    const hours = "0" + date.getHours();
    const minutes = "0" + date.getMinutes();
    return day.substr(-2) + "." + month.substr(-2) + "."
        + year.substr(-2) + "\u00A0" + hours.substr(-2) + ":" + minutes.substr(-2);
}

function daysSinceDate(unixTime) {
    const date1 = new Date(unixTime).getTime();
    const date2 = new Date().getTime();
    const oneDay = 1000 * 60 * 60 * 24;
    const difference = date2 - date1;
    return Math.round(difference / oneDay);
}

function daysReadable(daysInterval) {
    let days = daysInterval;
    const years = Math.floor(days / 365);
    days = days % 365;
    const months = Math.floor(days / 30);
    days = days % 30;
    return paucalYear(years) + ", " + paucalMonth(months) + ", " + paucalDay(days);
}

function secToText(seconds) {
    let time = seconds;
    const hours = Math.floor(time / 3600);
    time = time % 3600;
    const min = Math.floor(time / 60);
    const sec = Math.floor(time % 60);
    if (isNaN(hours) || isNaN(min) || isNaN(sec)) return "не определено";
    return (hours > 0 ? hours + " час. " : "") + (min > 0 ? min + " мин. " : "") + sec + " сек.";
}

function paucalYear(num) {
    return paucal(num, "год", "года", "лет");
}

function paucalMonth(num) {
    return paucal(num, "месяц", "месяца", "месяцев");
}

function paucalDay(num) {
    return paucal(num, "день", "дня", "дней");
}

function paucal(num, p1, p234, p) {
    const x = num % 100;
    if (x >= 10 && x < 20) {
        return num + " " + p;
    }
    const numStr = infiniteAndNanToStr(num, 0);
    switch (num % 10) {
        case 1:
            return numStr + " " + p1;
        case 2:
        case 3:
        case 4:
            return numStr + " " + p234;
        default:
            return numStr + " " + p;
    }
}

function infiniteAndNanToStr(num, digits) {
    return infiniteAndNan(num).toLocaleString(undefined, {maximumFractionDigits: digits === undefined ? 0 : digits})
}

function infiniteAndNan(number) {
    return isNaN(number) ? 0 : (isFinite(number) ? number : 0);
}