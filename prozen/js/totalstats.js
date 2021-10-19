let token;
let publisherId;
let publications;

const picker = new Litepicker({
    element: document.getElementById('start-date'),
    elementEnd: document.getElementById('end-date'),
    singleMode: false,
    dropdowns: {"minYear": 2017, "months": false, "years": true},
    numberOfColumns: 2,
    numberOfMonths: 2,
    format: "DD-MM-YYYY",
    lang: "ru-RU",
    tooltipText: {one: "день", few: "дня", many: "дней"},
    position: "bottom left",
    allowRepick: true,
    plugins: ['ranges'],
    ranges: {
        position: 'left',
        customRanges: getCustomRanges()
    }
})

main();

//////////////////////////////////////////////////////////////////////////////////////

async function main() {
    picker.setDateRange("30-05-2017", Date());
    document.getElementById("range-filter").onclick = showStatsByRange;
    showSpinner();
    token = await getToken();
    publisherId = await getPublisherId();
    publications = await getAllPublications();
    const stats = countStats();
    showStats(stats);
    hideSpinner();
}

function getToken() {
    return new Promise(resolve => {
        chrome.storage.local.get("prozenToken", result => {
            resolve(result.prozenToken);
        });
    });
}

function getPublisherId() {
    return new Promise((resolve) => {
        chrome.storage.local.get("prozenPublisherId", result => {
            resolve(result.prozenPublisherId);
        });
    });
}

function getCustomRanges() {
    const ranges = {};
    const today = new Date()
    const year = today.getFullYear();
    ranges["Текущий месяц"] = [new Date (new Date().setDate(1)), today];
    ranges["Прошлый месяц"] = [new Date (today.getFullYear(), today.getMonth()-1), new Date (today.getFullYear(), today.getMonth())];
    ranges["Последние 30 дней"] = [new Date (new Date().setDate(today.getDate()-30)), today];
    ranges["Последние 180 дней"] = [new Date (new Date().setDate(today.getDate()-180)), today];
    ranges[year.toString() + " год"] = [new Date(year, 0), today];
    ranges[(year - 1).toString() + " год"] = [new Date(year - 1, 0), new Date(year, 0)];
    ranges[(year - 2).toString() + " год"] = [new Date(year - 2, 0), new Date(year - 1, 0)];
    ranges[(year - 3).toString() + " год"] = [new Date(year - 3, 0), new Date(year - 2, 0)];
    ranges["Всё время"] = [new Date(2017, 4, 30), today];
    return ranges;
}

function showStats(stats) {
    let fieldsets = 0;
    stats.forEach((stat, publicationType) => {
        if (stat.count === 0) {
            showFieldset(publicationType, false);
        } else {
            showFieldset(publicationType, true);
            fieldsets++;
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
            const publicationsPerDayStr = stat.count === 0 || days === 0 ? "" : " (" + numFormat(stat.count / days, 2) + " в день)";

            document.getElementById(publicationType + "-count").textContent = numFormat(stat.count) + publicationsPerDayStr;
            document.getElementById(publicationType + "-feedShows").textContent = numFormat(stat.feedShows);
            document.getElementById(publicationType + "-views").textContent = numFormat(stat.views) + viewsPercentStr;
            document.getElementById(publicationType + "-viewstillend").textContent = numFormat(stat.viewsTillEnd) + viewsTillEndPercentStr;
            document.getElementById(publicationType + "-firstpost").textContent = minAddTimeStr;
        }
    });
    showNotFound (fieldsets === 0);
}

function getMedian(arrOfNums) {
    let result;
    const sortedArr = arrOfNums.sort((num1, num2) => num1 - num2);
    const medianIndex = Math.floor(sortedArr.length / 2);
    if (arrOfNums.length % 2 === 0) {
        result = (sortedArr[medianIndex-1] + sortedArr[medianIndex]) / 2;
    } else {
        result = sortedArr[medianIndex];
    }
    return result;
}

function countStats() {
    const stats = new Map();
    for (let i = 0; i < TYPES.length; i++) {
        stats.set(TYPES[i], {count: 0,
            feedShows: 0, feedShowsMed: 0,  feedShowsAvg: 0,
            shows: 0, showsMed: 0, showsAvg: 0,
            views: 0, viewsMed: 0, viewsAvg: 0,
            viewsTillEnd: 0, viewsTillEndMed: 0,viewsTillEndAvg: 0,
            minAddTime: 0});
    }
    if (publications == null) {
        return stats;
    }

    const dateStart = picker.getStartDate();
    const dateEnd = picker.getEndDate();



    for (let i = 0; i < publications.length; i++) {
        const publication = publications[i];
        if (publication.addTime < dateStart.getTime() || publication.addTime > dateEnd.getTime()) {
            continue;
        }
        const type = publication.content.type;
        if (type === "story") {
            continue;
        }
        const stat = stats.get(type);
        stat.count++;
        stat.feedShows += publication.privateData.statistics.feedShows;
        stat.views += publication.privateData.statistics.views;
        stat.shows += publication.privateData.statistics.shows;
        stat.viewsTillEnd += publication.privateData.statistics.viewsTillEnd;
        stat.minAddTime = (publication.addTime !== undefined) && (publication.addTime < stat.minAddTime || stat.minAddTime === 0) ? publication.addTime : stat.minAddTime;
        stats.set(type, stat);
    }
    // Count totals stats
    const total = {count: 0,
        feedShows: 0, feedShowsMed: 0,  feedShowsAvg: 0,
        shows: 0, showsMed: 0, showsAvg: 0,
        views: 0, viewsMed: 0, viewsAvg: 0,
        viewsTillEnd: 0, viewsTillEndMed: 0,viewsTillEndAvg: 0,
        minAddTime: 0};

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
                    cards.push(pubData);
                    recordCount++;
                    document.getElementById("records_count").innerText = "Загружено: " + paucal(recordCount, " публикация", " публикации", " публикаций");
                }
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

function showStatsByRange() {
    if (publications) {
        const stats = countStats(publications);
        showStats(stats);
    }
}

function showFieldset (type, show) {
    const fieldset = document.getElementById(`${type}-fieldset`);
    fieldset.style.display = show ? "block" : "none";
}

function showNotFound(show) {
    document.getElementById("not-found").style.display = show ? "block" : "none";
}