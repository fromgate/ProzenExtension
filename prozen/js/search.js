const API_URL = "https://zen.yandex.ru/api/v3/launcher/more?country_code=ru&clid=700&";

const VISIBLE = ["search_msg", "spinner", "search_result", "search_msg_empty", "search_not_found"];
const SEARCH_PLACEHOLDER = ["кора осины", "продзен", "варенье из огурцов", "смысл жизни", "показы"];

var id;
let publications = [];

showElement("search_msg");
getChannelId();

document.getElementById('search_button').onclick = searchClick;
document.getElementById('show_articles').onclick = showArticles;
document.getElementById('show_narratives').onclick = showNarratives;
document.getElementById('show_posts').onclick = showPosts;
document.getElementById('show_videos').onclick = showVideos;
randomizeSearchPlaceholer();

//////////////////////////////////////////////////////////////////////////////////////

function showArticles() {
    showByType("card");
    return false;
}

function showNarratives() {
    showByType("story");
    return false;
}

function showPosts() {
    showByType("post");
    return false;
}

function showVideos() {
    showByType("gif");
    return false;
}

function getChannelId() {
    chrome.storage.local.get(["prozenId", "prozenSearch"], function (result) {
        id = result.prozenId;
        const searchString = result.prozenSearch;
        if (searchString.length > 0) {
            document.getElementById("search").value = searchString;
            searchClick();
        }
    });
}

function updateSerchStats() {
    if (publications.length !== 0) {
        const count = {};
        count['card'] = 0;
        count['story'] = 0;
        count['post'] = 0;
        count['gif'] = 0;

        for (let i = 0; i < publications.length; i++) {
            count [publications[i].type]++;
        }
        document.getElementById('show_articles').innerText = "Статьи: " + count.card;
        document.getElementById('show_narratives').innerText = "Нарративы: " + count.story;
        document.getElementById('show_posts').innerText = "Посты: " + count.post;
        document.getElementById('show_videos').innerText = "Видео: " + count.gif;
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
    for (let i = 0; i < publications.length; i++) {
        const card = publications [i];
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


function searchClick() {
    const searchString = document.getElementById("search").value;
    clearSearchResults();
    if (searchString && searchString.length !== 0) {
        showElement("spinner");
        if (publications.length === 0) {
            loadPublicationsAndSearch();
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
    let url = API_URL + id;
    loadPageData(url).then(cards => {
        publications = cards;
        updateSerchStats();
        executeShowByType(pubType);
    });
}

function loadPublicationsAndSearch() {
    let url = API_URL + id;
    loadPageData(url).then(cards => {
        publications = cards;
        updateSerchStats();
        executSearch();
    });
}

async function loadPageData(initUrl) {
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
    card.type = item.type; // card — статья, story — нарратив, post — post, gif — видео
    card.url = item.link.split("?")[0];
    card.title = card.type === "post" ? postJsonToText(item.rich_text.json) : item.title;
    card.description = card.type === "post" ? "" : item.text;
    publications.push(card);
    return card;
}

function postJsonToText(json) {
    if (json === undefined || json.length === 0) {
        return "";
    }
    let str;
    for (let i = 0; i < json.length; i++) {
        const obj = json[i];
        if (obj.type === "text") {
            if (str === undefined) {
                str = obj.data
            } else {
                str = str + " " + obj.data;
            }
        }
    }
    return str;
}

function addSearchResult(card) {
    const a = document.createElement("a");
    a.setAttribute("href", card.url);
    a.setAttribute("target", "_blank");
    const div = cardToDiv(card);
    a.appendChild(div);
    const searchResult = document.getElementById("search_result");
    searchResult.appendChild(a);
    searchResult.appendChild(document.createElement("hr"));
}

function cardToDiv(card) {
    const div = document.createElement("div");
    div.setAttribute("class", "section");
    const icon = document.createElement("span");
    switch (card.type) {
        case "card":
            icon.setAttribute("class", "icon_views span_icon");
            icon.setAttribute("title", "Статья");
            break;
        case "story":
            icon.setAttribute("class", "icon_narrative span_icon");
            icon.setAttribute("title", "Нарратив");
            break;
        case "gif":
            icon.setAttribute("class", "icon_video span_icon");
            icon.setAttribute("title", "Видео / GIF");
            break;
        case "post":
            icon.setAttribute("class", "icon_post span_icon");
            icon.setAttribute("title", "Пост");
            break;
    }
    div.appendChild(icon);
    const strong = document.createElement("strong");
    strong.innerText = card.title;
    div.appendChild(strong);
    if (card.type === "card" || card.type === "story") {
        div.appendChild(document.createElement("br"));
        const span = document.createElement("span");
        span.innerText = card.description === undefined || card.description.length === 0 ? "Описание не указано" : card.description;
        div.appendChild(span);
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