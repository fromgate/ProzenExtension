const API_URL = "https://zen.yandex.ru/api/v3/launcher/more?country_code=ru&clid=700&";
const NOINDEX_KEY = "prozen-noindex-agree-";
let AGREE = false;

var id;
let publications = [];

const VISIBLE = ["start_text", "spinner", "progress", "search_result", "disclaimer", "search_msg_empty", "not_found"];

showWarning();
document.getElementById("agree").onclick = clickAgree;
document.getElementById("start_button").onclick = loadPublicationsAndSearch;

function showWarning() {
    if (AGREE) {
        showElement("start_text");
    } else {
        showElement("disclaimer");
    }
}

function clickAgree() {
    AGREE = true;
    saveAgree();
    showElement("start_text");
    return false;
}

function saveAgree() {
    const object = {};
    if (id === undefined || id === null) {
        return;
    }
    object [NOINDEX_KEY + id] = AGREE;
    chrome.storage.local.set(object);
}

getChannelId();

function getChannelId() {
    chrome.storage.local.get(["prozenId"], function (result) {
        id = result.prozenId;
        if (id !== undefined) {
            chrome.storage.local.get([NOINDEX_KEY + id], function (result) {
                const agree = result [NOINDEX_KEY + id];
                if (agree !== undefined) {
                    AGREE = agree;
                } else {
                    AGREE = false;
                }
                showWarning();
            });
        }
    });
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

function showProgress(value, maxValue) {
    document.getElementById("progress").style.display = "block";
    const progressBar = document.getElementById("progress-bar");
    progressBar.setAttribute("value", value);
    progressBar.setAttribute("max", maxValue);
}

function progress(newValue) {
    const progressBar = document.getElementById("progress-bar");
    progressBar.setAttribute("value", newValue);
}

function hideProgress() {
    document.getElementById("progress").style.display = "none";
}

function clearSearchResults() {
    const element = document.getElementById("search_result");
    while (element.firstChild) {
        element.removeChild(element.firstChild);
    }
}


async function executeSearch(pubs) {
    showElement("search_result");
    showProgress(0, pubs.length);
    const card = pubs[4];
    const check = await checkRobotNoNoIndex(card);
    let count = 0;
    let countRobots = 0;
    for (const card of publications) {

        count++;
        progress(count);
        const robots = await checkRobotNoNoIndex(card);
        if (robots) {
            addSearchResult(card);
            scrollToBottom();
            countRobots++;
        }
    }
    if (countRobots === 0) {
        showElement("not_found");
    } else {
        addListFooter(count, countRobots);
    }
    hideProgress();
}


function loadPublicationsAndSearch() {
    let url = API_URL + id;
    showElement("spinner");
    const findAll = document.getElementById("find_all").checked;
    loadPageData(url, findAll).then(cards => {
        publications = cards;
        executeSearch(publications);
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
        console.log(url);
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
        if (loadAll) {
            url = json.more.link;
        } else {
            break;
        }
    }
    return cards;
}


async function checkRobotNoNoIndex(card) {
    return new Promise(function (resolve,) {
        const xhr = new XMLHttpRequest();
        xhr.onload = function () {
            const metas = xhr.responseXML.head.getElementsByTagName('meta');
            for (let i = 0; i < metas.length; i++) {
                if (metas[i].getAttribute('name') === "robots") {
                    if (metas[i].getAttribute('content') === "noindex") {
                        resolve(true);
                    }
                    break;
                }
            }
            resolve(false);
        };
        xhr.open("GET", card.url);
        xhr.responseType = "document";
        xhr.send();
    });
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

function addListFooter(totalCount, robotsCount) {
    const div = document.createElement("div");
    div.setAttribute("class", "section");
    const p = document.createElement("p");
    const text1 = document.createElement("span");
    text1.innerText = "Проверено публикаций: ";
    const strong1 = document.createElement("strong");
    strong1.innerText = totalCount;
    const br = document.createElement("br");
    const text2 = document.createElement("span");
    text2.innerText = "Не индексируется: ";
    const strong2 = document.createElement("strong");
    strong2.innerText = robotsCount;
    p.append(text1, strong1, br, text2, strong2);
    div.append(p);
    const searchResult = document.getElementById("search_result");
    searchResult.appendChild(div);
}

function scrollToBottom() {
    window.scrollTo(0, document.body.scrollHeight);
}