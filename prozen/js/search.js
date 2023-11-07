const VISIBLE = ["search_msg", "spinner", "search_result", "search_msg_empty", "search_not_found"];

let token;
let publications = [];
let publisherId;
let searchHistoryList = [];

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
picker.getDayAfterEnd = function () {
    const dateEnd = this.getEndDate();
    dateEnd.setDate(dateEnd.getDate() + 1);
    return dateEnd;
}
picker.setDateRange("30-05-2017", Date());

function getCustomRanges() {
    const ranges = {};
    const today = new Date()
    const year = today.getFullYear();
    ranges["Текущий месяц"] = [new Date(new Date().setDate(1)), today]
    ranges["Прошлый месяц"] = [new Date(today.getFullYear(), today.getMonth() - 1), new Date(today.getFullYear(), today.getMonth())]
    ranges["Последние 30 дней"] = [new Date(new Date().setDate(today.getDate() - 30)), today]
    ranges["Последние 180 дней"] = [new Date(new Date().setDate(today.getDate() - 180)), today]
    ranges[year.toString() + " год"] = [new Date(year, 0), today]
    ranges[(year - 1).toString() + " год"] = [new Date(year - 1, 0), new Date(year, 0)]
    ranges[(year - 2).toString() + " год"] = [new Date(year - 2, 0), new Date(year - 1, 0)]
    ranges[(year - 3).toString() + " год"] = [new Date(year - 3, 0), new Date(year - 2, 0)]
    return ranges
}

showElement("search_msg");
getChannelId();
initButtons();

function initButtons() {
    document.getElementById('search_button').onclick = searchClick;
    document.getElementById('search-clear').onclick = searchClear;
    document.getElementById("range-clear").onclick = clickSearchAllTime;
    TYPES.forEach(pubType => {
        document.getElementById("show-" + pubType).addEventListener("click", showByType.bind(null, pubType));
    });

    const searchField = document.getElementById('search');
    searchField.addEventListener("keyup", event => {
        if (event.key === "Enter") {
            event.preventDefault();
            document.getElementById("search_button").click();
        }
    });
}

function clickSearchAllTime() {
    clearDateRange();
    searchClick();
}

function clearDateRange() {
    if (publications.length > 0) {
        let min = Date.now();
        let max = 0;
        publications.forEach(publication => {
            if (publication.addTime < min) {
                min = publication.addTime;
            }
            if (publication.addTime > max) {
                max = publication.addTime;
            }
        });
        picker.setDateRange(picker.DateTime(new Date(min)), picker.DateTime(new Date(max)));
    } else {
        picker.setDateRange("30-05-2017", new Date());
    }
}

function getChannelId() {
    chrome.storage.local.get(["prozenSearch", "prozenToken", "prozenPublisherId"], result => {
        publisherId = result.prozenPublisherId;
        token = result.prozenToken;
        const searchString = result.prozenSearch;
        if (searchString.length > 0) {
            document.getElementById("search").value = searchString;
            searchClick();
        }
    });
    loadSearchHistory();
}

function loadSearchHistory() {
    chrome.storage.local.get(["prozenSearchHistory"], result => {
        const searchHistoryListStr = result.prozenSearchHistory;
        if (searchHistoryListStr != null) {
            searchHistoryList = searchHistoryListStr.split(",");
        }
        updateSearchHistory();
    });
}

function saveSearchHistory() {
    if (searchHistoryList != null && searchHistoryList.length > 0) {
        chrome.storage.local.set({prozenSearchHistory: searchHistoryList.join(",")});
    }
}

function updateSearchHistory() {
    const searchListEl = document.getElementById("search-list");
    searchListEl.replaceChildren();
    if (searchHistoryList != null && searchHistoryList.length > 0) {
        for (let history of searchHistoryList) {
            const option = document.createElement("option");
            option.value = history;
            searchListEl.appendChild(option);
        }
    }
    saveSearchHistory();
}

function addNewSearchHistory(newSearchString) {
    if (searchHistoryList == null) {
        searchHistoryList = [];
    }
    searchHistoryList = searchHistoryList.filter(e => e !== newSearchString)
    searchHistoryList.unshift(newSearchString);
    if (searchHistoryList.length > 10) {
        searchHistoryList.pop();
    }
    updateSearchHistory();
}

function updateSearchStats() {
    if (publications.length !== 0) {
        const count = {};
        TYPES.forEach(pubType => count[pubType] = 0);
        for (let i = 0; i < publications.length; i++) {
            count [publications[i].type]++;
        }
        document.getElementById('show-article').innerText = "Статьи: " + count.article;
        document.getElementById('show-brief').innerText = "Посты: " + count.brief;
        document.getElementById('show-gif').innerText = "Видео: " + count.gif;
        document.getElementById('show-short_video').innerText = "Ролики: " + count.short_video;
        document.getElementById('show-gallery').innerText = "Галереи: " + count.gallery;
    }
}

function showByType(pubType) {
    clearSearchResults();
    showElement("spinner");
    if (publications.length === 0) {
        loadPublicationsAndShowByType(pubType)
    } else {
        executeShowByType(pubType);
    }
}

function executeShowByType(pubType) {
    const foundCards = [];
    const dateStart = picker.getStartDate();
    const dateEnd = picker.getDayAfterEnd();
    for (let i = 0; i < publications.length; i++) {
        const card = publications [i];
        if (card.addTime < dateStart.getTime() || card.addTime > dateEnd.getTime()) {
            continue;
        }
        if (card.type === pubType) {
            foundCards.push(card);
        }
    }
    if (foundCards.length > 0) {
        showElement("search_result");
        for (let i = 0; i < foundCards.length; i++) {
            const card = foundCards[i];
            addSearchResult(card);
        }
    } else {
        showElement("search_not_found");
    }
}

function searchClear() {
    document.getElementById("search").value = "";
}

function searchClick() {
    const searchString = document.getElementById("search").value;
    addNewSearchHistory(searchString);
    clearSearchResults();
    showElement("spinner");
    if (publications.length === 0) {
        loadPublicationsAndSearch();
    } else {
        executeSearch();
    }
}

function cardMatch(card, searchString) {
    if (searchString === undefined || searchString.length === 0) {
        return true;
    }
    const ln = searchString.split(" ");
    let foundTitle = true;
    let foundDescription = true;
    const title = card.title.toLocaleLowerCase();
    const description = card.snippet == null ? "" : card.snippet.toLocaleLowerCase();
    for (let i = 0; i < ln.length; i++) {
        const str = ln[i].toLocaleLowerCase();
        if (!title.includes(str)) {
            foundTitle = false;
        }
        if (!description.includes(str)) {
            foundDescription = false;
        }
    }
    return foundTitle || foundDescription;
}

function executeSearch() {
    const searchStr = document.getElementById("search").value;
    const dateStart = picker.getStartDate();
    const dateEnd = picker.getDayAfterEnd();//.getEndDate();
    //dateEnd.setDate(dateEnd.getDate() + 1);
    const foundCards = [];
    for (let i = 0; i < publications.length; i++) {
        const card = publications [i];
        if (card.addTime < dateStart.getTime() || card.addTime > dateEnd.getTime()) {
            continue;
        }
        if (cardMatch(card, searchStr)) {
            foundCards.push(card);
        }
    }
    if (foundCards.length > 0) {
        showElement("search_result");
        for (let i = 0; i < foundCards.length; i++) {
            const card = foundCards[i];
            addSearchResult(card);
        }
    } else {
        showElement("search_not_found");
    }
}

function loadPublicationsAndShowByType(pubType) {
    loadAllPublications(true).then(cards => {
        publications = cards;
        updateSearchStats();
        executeShowByType(pubType);
    });
}

function loadPublicationsAndSearch() {
    loadAllPublications(true).then(cards => {
        publications = cards;
        updateSearchStats();
        executeSearch();
    });
}

function addSearchResult(card) {
    const searchResult = document.getElementById("search_result");
    const div = cardToDiv(card);
    if (card.url !== undefined && card.url !== "") {
        const a = document.createElement("a");
        a.setAttribute("href", card.url);
        a.setAttribute("target", "_blank");
        a.appendChild(div);
        searchResult.appendChild(a);
    } else {
        searchResult.appendChild(div);
    }
    searchResult.appendChild(document.createElement("hr"));
}

function cardToDiv(card) {
    const div = document.createElement("div");
    div.setAttribute("class", "section");
    const icon = document.createElement("span");
    switch (card.type) {
        case "article":
            icon.setAttribute("class", "icon_views span_icon");
            icon.setAttribute("title", "Статья");
            break;
        case "narrative":
            icon.setAttribute("class", "icon_narrative span_icon");
            icon.setAttribute("title", "Нарратив");
            break;
        case "gallery":
            icon.setAttribute("class", "icon_narrative span_icon");
            icon.setAttribute("title", "Галерея");
            break;
        case "gif":
            icon.setAttribute("class", "icon_video span_icon");
            icon.setAttribute("title", "Видео");
            break;
        case "short_video":
            icon.setAttribute("class", "icon_video span_icon");
            icon.setAttribute("title", "Ролик");
            break;
        case "brief":
            icon.setAttribute("class", "icon_post span_icon");
            icon.setAttribute("title", "Пост");
            break;
        case "post":
            icon.setAttribute("class", "icon_post span_icon");
            icon.setAttribute("title", "Пост (старый)");
            break;
    }
    div.appendChild(icon);
    const strong = document.createElement("strong");
    strong.innerText = card.title;
    div.appendChild(strong);
    if (card.type === "article" || card.type === "narrative") {
        div.appendChild(document.createElement("br"));
        const span = document.createElement("span");
        span.innerText = card.snippet == null || card.snippet.length === 0 ? "Описание не указано" : card.snippet;
        div.appendChild(span);
    } else if (card.type === "gallery") {
        strong.innerText = card.title == null || card.title.length === 0 ? "Описание не указано" : card.title;
    } else if (card.type === "post" || card.type === "brief") {
        strong.innerText = card.snippet == null || card.snippet.length === 0 ? "Описание не указано" : card.snippet;
    }
    return div;
}

function hideAll() {
    for (let i = 0; i < VISIBLE.length; i++) {
        document.getElementById(VISIBLE[i]).style.display = "none";
    }
}

function showElement(id) {
    hideAll();
    document.getElementById(id).style.display = "block";
}

function clearSearchResults() {
    const element = document.getElementById("search_result");
    while (element.firstChild) {
        element.removeChild(element.firstChild);
    }
}