const COUNT_PUBLICATIONS_API_URL = "https://zen.yandex.ru/media-api/count-publications-by-state?state=published&type="
const GET_PUBLICATIONS_API_URL = "https://zen.yandex.ru/media-api/get-publications-by-state?state=published&pageSize="

var token;
getToken();

document.getElementById('totalstats_button').onclick = totalStatsClick;

//////////////////////////////////////////////////////////////////////////////////////

function getToken() {
    chrome.storage.local.get("prozenToken", function (result) {
        token = result.prozenToken;
    });
}

function getTotalStats(publicationType) {
    loadPublicationsCount(publicationType).then(response => {
        document.getElementById(publicationType + "-count").textContent = response.count;
        let aCount = response.count;
        loadPublications(publicationType, aCount).then(response => {
            var shows = 0;
            var views = 0;
            var viewsTillEnd = 0;                
            for(var i = 0, len = response.publications.length; i < len; i++ ) {
                let publication = response.publications[i];
                shows += publication.privateData.statistics.feedShows;
                views += publication.privateData.statistics.views;
                viewsTillEnd += publication.privateData.statistics.viewsTillEnd;
            }
            document.getElementById(publicationType + "-shows").textContent = shows;
            document.getElementById(publicationType + "-views").textContent = views;
            document.getElementById(publicationType + "-viewstillend").textContent = viewsTillEnd;
        })
});
}

function totalStatsClick() {
    getTotalStats("article");
    getTotalStats("narrative");
    getTotalStats("post");
    getTotalStats("gif");
    return false;
}

function loadPublicationsCount(publicationType) {
    const url=COUNT_PUBLICATIONS_API_URL + encodeURIComponent(publicationType);
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}

function loadPublications(publicationType, count) {
    const url=GET_PUBLICATIONS_API_URL + encodeURIComponent(count) + "&type=" + encodeURIComponent(publicationType);
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}