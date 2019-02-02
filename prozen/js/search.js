const API_URL = "https://zen.yandex.ru/api/v3/launcher/more?country_code=ru&";
const VISIABLE = ["search_msg","spinner","search_result","search_msg_empty","search_not_found"];
const SEARCH_PLACEHOLDER = ["кора осины", "продзен", "варенье из огурцов", "смысл жизни", "показы"];

let id;
let publications = [];

showElement ("search_msg");

chrome.storage.local.get("prozenId", function (result) {
    id = result.prozenId;
});

document.getElementById('search_button').onclick= searchClick;
randomizeSearchPlaceholer();

//////////////////////////////////////////////////////////////////////////////////////
function searchClick() {
    const searchString = document.getElementById("search").value;
    clearSearchResults();
    if (searchString && searchString.length !== 0) {
        showElement("spinner");
        if (publications.length === 0) {
            loadPublicationsAndSearch ();
        } else {
            executSearch();
        }
    } else {
        showElement("search_msg_empty");
    }
    return false;
}

function cardMatch(card, searchString) {
    const ln = searchString.split(" ");
    let foundTitle = true;
    let foundDescription = true;
    const title = card.title.toLocaleLowerCase();
    const description = card.description === undefined ? "" : card.description.toLocaleLowerCase();
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

function executSearch() {
    const searchStr = document.getElementById("search").value;
    const foundCards = [];
    for (let i = 0; i < publications.length; i++) {
        const card = publications [i];
        if (cardMatch(card, searchStr)) {
            foundCards.push(card);
        }
    }
    if (foundCards.length>0) {
        showElement("search_result");
        for (let i = 0; i < foundCards.length; i++) {
            const card = foundCards[i];
            addSearchResult(card.title, card.description, card.url);
        }
    } else {
        showElement("search_not_found");
    }
}

function loadPublicationsAndSearch () {
    let url = API_URL + id;
    loadPageData(url).then(cards => {
        publications = cards;
        executSearch();
    });
}

async function loadPageData(initUrl) {
    let url = initUrl;
    const cards = [];
    while (true) {
        const request = await fetch(url);
        let json;
        try {
            json = await request.json();
        } catch (e) {
        }
        if (!request.ok || json === undefined) {
            break;
        }
        const items = json.items;
        for (let i = 0; i< items.length; i++) {
            const item = items[i];
            if (item.link.startsWith("https://zen.yandex.ru")) {
                cards.push(cardFromItem(item));
            }
        }
        url = json.more.link;
    }
    return cards;
}

function randomizeSearchPlaceholer() {
    document.getElementById("search").setAttribute("placeholder", SEARCH_PLACEHOLDER[Math.floor(Math.random() * SEARCH_PLACEHOLDER.length)])
}

function cardFromItem(item) {
    const card = {};
    card.url = item.link.split("?")[0];
    card.title = item.title;
    card.description = item.text;
    publications.push(card);
    return card;
}

function addSearchResult(title, description, url) {
    const a = document.createElement("a");
    a.setAttribute("href",url);
    a.setAttribute("target","_blank");
    const div = document.createElement("div");
    div.setAttribute("class", "section");
    const strong = document.createElement("strong");
    strong.innerText = title;
    div.appendChild(strong);
    div.appendChild(document.createElement("br"));
    const span = document.createElement("span");
    span.innerText = description === undefined || description.length === 0 ? "Описание не указано" : description;
    div.appendChild(span);
    a.appendChild(div);
    const searchResult = document.getElementById("search_result");
    searchResult.appendChild(a);
    searchResult.appendChild(document.createElement("hr"));
}

function hideAll() {
    for (let i = 0; i < VISIABLE.length; i++) {
        document.getElementById(VISIABLE[i]).style.display = "none";
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