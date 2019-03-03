const COUNT_PUBLICATIONS_API_URL = "https://zen.yandex.ru/media-api/count-publications-by-state?state=published&type="
const GET_PUBLICATIONS_API_URL = "https://zen.yandex.ru/media-api/get-publications-by-state?state=published&pageSize="

var token;

main();

//////////////////////////////////////////////////////////////////////////////////////

function getToken() {
    return new Promise((resolve) => {
        chrome.storage.local.get("prozenToken", function (result) {
            resolve (result.prozenToken);
        });
    });
}

async function main() {
    showSpinner();
    token = await getToken();
    const types = ["article", "narrative", "post","gif"];
    for (let i = 0; i < types.length; i++) {
        const publicationType = types[i];
        const stat = await getStats(publicationType);
        const viewsPercent = stat.shows === 0 ? 0 : stat.views / stat.shows * 100;
        const viewsPercentStr = viewsPercent === 0 ?  "" : " ("+numFormat (viewsPercent, 2)+"%)";

        const viewsTillEndPercent = stat.viewsTillEnd === 0 ? 0 : stat.viewsTillEnd / stat.views * 100;
        const viewsTillEndPercentStr  = viewsTillEndPercent === 0 ?  "" : " ("+numFormat (viewsTillEndPercent, 2)+"%)";


        let minAddTimeStr = "-";
        const days = daysSinceDate(stat.minAddTime);
        if (stat.minAddTime > 0) {
            minAddTimeStr = dateFormat(stat.minAddTime);
            if (days > 0) {
                minAddTimeStr += "; Прошло "+ days +" дней (" + daysReadable(days) + ")";
            }
        }

        const publicationsPerDayStr = stat.count === 0 && days !== 0 ? "" : " (" + numFormat(stat.count / days, 2) + " в день)";

        document.getElementById(publicationType + "-count").textContent = numFormat (stat.count)+ publicationsPerDayStr;
        document.getElementById(publicationType + "-shows").textContent = numFormat (stat.shows);
        document.getElementById(publicationType + "-views").textContent = numFormat (stat.views) +viewsPercentStr;
        document.getElementById(publicationType + "-viewstillend").textContent = numFormat (stat.viewsTillEnd) + viewsTillEndPercentStr;


        document.getElementById(publicationType + "-firstpost").textContent = minAddTimeStr;
    }
    hideSpinner();
}

async function getStats(publicationType) {
    const response = await loadPublicationsCount(publicationType).then(response => {
        return response;
    });

    const count = response.count;
    const result = await loadPublications(publicationType, count).then(response => {
        let shows = 0;
        let views = 0;
        let viewsTillEnd = 0;
        let minAddTime = 0;
        for(let i = 0, len = response.publications.length; i < len; i++ ) {
            const publication = response.publications[i];
            shows += publication.privateData.statistics.feedShows;
            views += publication.privateData.statistics.views;
            viewsTillEnd += publication.privateData.statistics.viewsTillEnd;
            minAddTime =  publication.addTime < minAddTime || minAddTime === 0 ? publication.addTime : minAddTime;
        }
        return {shows : shows, views : views, viewsTillEnd : viewsTillEnd, minAddTime : minAddTime};
    });
    result.count = count;
    return result;
}

function loadPublicationsCount(publicationType) {
    const url=COUNT_PUBLICATIONS_API_URL + encodeURIComponent(publicationType);
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}

function loadPublications(publicationType, count) {
    const url=GET_PUBLICATIONS_API_URL + encodeURIComponent(count) + "&type=" + encodeURIComponent(publicationType);
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}

function hideSpinner() {
    document.getElementById("spinner").style.display = "none";
    document.getElementById("stats").style.display = "block";
}

function showSpinner() {
    document.getElementById("spinner").style.display = "block";
    document.getElementById("stats").style.display = "none";
}


function numFormat(num, digits) {
    return num.toLocaleString(undefined, {maximumFractionDigits: digits === undefined ? 0 : digits});
}

function dateFormat(unixTime) {
    const date = new Date(unixTime);
    const day = "0" + date.getDate();
    const month = "0" + (date.getMonth() + 1);
    const year = "" +date.getFullYear();
    const hours = "0" + date.getHours();
    const minutes = "0" + date.getMinutes();
    return day.substr(-2) + "." + month.substr(-2) + "."
        +year.substr(-2) + "\u00A0"+hours.substr(-2)+":"+minutes.substr(-2) ;
}

function daysSinceDate(unixTime) {
    const date1 = new Date (unixTime).getTime();
    const date2 = new Date().getTime();
    const oneDay=1000*60*60*24;
    const difference = date2 - date1;
    return Math.round(difference/oneDay);
}

function daysReadable (daysInterval) {
    let days = daysInterval;
    const years = Math.floor(days / 365);
    days = days % 365;
    const months = Math.floor(days / 30);
    days = days % 30;
    return years  +" лет, " + months + " месяцев, " + days + " дней";
}


function secToText(seconds) {
    let time = seconds;
    const hours = Math.floor(time / 3600);
    time = time % 3600;
    const min = Math.floor(time / 60);
    const sec = Math.floor(time % 60);
    if (isNaN(hours) || isNaN(min) || isNaN(sec)) return "не определено";
    return (hours > 0 ? hours +" час. " : "") + (min > 0 ? min  +" мин. " : "") + sec+ " сек.";
}