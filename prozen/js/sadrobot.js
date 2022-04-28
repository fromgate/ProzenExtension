const API_URL = "https://zen.yandex.ru/api/v3/launcher/more?country_code=ru&clid=700&";
const NOINDEX_KEY = "prozen-noindex-agree-";
const ROBOTS_NOINDEX = "noindex"
const ROBOTS_OK = "ok"
const ROBOTS_FAIL = "fail"
const NO_ADV = "no_adv"

let AGREE = false;

var id;
let publications = [];
let newPublications = []
let publisherId;
let token;

const VISIBLE = ["start_text", "spinner", "progress", "search_result", "disclaimer", "search_msg_empty", "not_found", "channel_none"];

showWarning();
document.getElementById("agree").onclick = clickAgree;
document.getElementById("start_button").onclick = loadPublicationsAndSearch;

start();

function start() {
    showElement("spinner");
    loadData().then(data => {
        id = data.id;
        token = data.token;
        publisherId = data.publisherId;
        AGREE = data.agree;
        checkHasNone(id).then(none => {
            if (none) {
                showElement("channel_none");
            } else {
                setTimeout(showWarning, 1);
            }
        });
    });
}

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

function getChannelId() {
    chrome.storage.local.get(["prozenId"], result => {
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


async function executeSearch(pubs, limitCount = -1) {
    showElement("search_result");
    let count = 0;
    let countRobots = 0;
    let links = ""
    const maxCount = limitCount <0 ? publications.length : Math.min (limitCount, publications.length);
    showProgress(0, maxCount);
    for (const card of publications) {
        if (!["post", "narrative"].includes(card.type)) {
            count++;
            progress(count);
            const checkState = await checkRobotNoNoIndex(card);
            if (checkState.size > 0 && !checkState.has(ROBOTS_OK)) {
                addSearchResult(card, checkState);
                if (checkState.has (ROBOTS_NOINDEX)) {
                    links += `${card.url}\n`;
                }
                scrollToBottom();
                countRobots++;
            }
            if (count >= maxCount) {
                break;
            }
        }
    }

    if (countRobots === 0) {
        showElement("not_found");
    } else {
        addListFooter(count, countRobots, links);
    }
    hideProgress();
}


function loadPublicationsAndSearch() {
    let url = API_URL + id;
    showElement("spinner");
    const findAll = document.getElementById("radio_find_all").checked;
    loadAllPublications(true).then(cards => {
        publications = cards;
        executeSearch(publications, findAll ? -1 : 20);
    });
    return false;
}

async function checkRobotNoNoIndex(card) {
    return new Promise(resolve => {
        const xhr = new XMLHttpRequest();
        xhr.onload = () => {
            const document = xhr.responseXML
            const checks = new Set()
            const metas = document.head.getElementsByTagName('meta');
            for (let i = 0; i < metas.length; i++) {
                const meta = metas[i];
                if (meta.getAttribute('name') === "robots") {
                    if (meta.getAttribute('content') === "noindex") {
                        checks.add(ROBOTS_NOINDEX);
                    }
                } else if (meta.getAttribute("property") === "robots"
                    && meta.getAttribute("content") === "none") {
                    checks.add(ROBOTS_NOINDEX);
                }
            }
            const scriptData = document.getElementById("all-data")
            // checkNoAdv (scriptData.innerText)

            if (checks.size === 0) {
                checks.add(ROBOTS_OK)
            }
            resolve(checks);
        };
        xhr.onerror = () => {
            resolve(ROBOTS_FAIL)
        }
        xhr.open("GET", card.url);
        xhr.responseType = "document";
        xhr.send();
    });
}

function checkNoAdv (scriptLines) {
    const prefix = "  w._data = ";
    let wData = ""
    const lines = scriptLines.split("\n");

    for (let i = 0; i< lines.length; i++) {
        const line = lines[i];
        if (line.startsWith(prefix)) {
            wData = line.slice(prefix.length, -1);
            break;
        }
    }
}

function addSearchResult(card, state = new Set([ROBOTS_OK])) {
    const a = document.createElement("a");
    a.setAttribute("href", card.url);
    a.setAttribute("target", "_blank");
    const div = cardToDiv(card, state.has (ROBOTS_FAIL));
    a.appendChild(div);
    const searchResult = document.getElementById("search_result");
    searchResult.appendChild(a);
    searchResult.appendChild(document.createElement("hr"));
}

function cardToDiv(card, fail = false) {
    const div = document.createElement("div");
    div.setAttribute("class", "section");
    const icon = document.createElement("span");
    switch (card.type) {
        case "article":
            icon.setAttribute("class", "icon_views span_icon");
            icon.setAttribute("title", "Статья");
            break;
        case "story":
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
        case "post":
            icon.setAttribute("class", "icon_post span_icon");
            icon.setAttribute("title", "Пост (старый)");
            break;
        case "brief":
            icon.setAttribute("class", "icon_post span_icon");
            icon.setAttribute("title", "Пост");
            break;
    }
    div.appendChild(icon);

    if (fail) {
        const marked = document.createElement("mark");
        marked.innerText = "Ошибка проверки!";
        marked.className = "inline-block";
        marked.setAttribute("title", "Расширение не смогло проверить статью (возможно ошибка связи).\nНадо проверить статью вручную или, если таких ошибок много,\nповторить проверку позднее.")
        div.appendChild(marked);
    }

    const strong = document.createElement("strong");
    strong.innerText = card.title;
    div.appendChild(strong);
    if (card.type === "article" || card.type === "gif" ) {
        div.appendChild(document.createElement("br"));
        const span = document.createElement("span");
        span.innerText = card.description === undefined || card.description.length === 0 ? "Описание не указано" : card.description;
        div.appendChild(span);
    }
    return div;
}

function addListFooter(totalCount, robotsCount, links) {
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
    if (links) {
        const button = createElement("button");
        button.innerText = "Скопировать ссылки в буфер обмена";
        button.setAttribute("title", "Ссылки на публикации с ошибкой проверки скопированы не будут");
        button.addEventListener("click", copyTextToClipboard.bind(null, links));
        div.append(button);
    }
    const searchResult = document.getElementById("search_result");
    searchResult.appendChild(div);
}


function scrollToBottom() {
    window.scrollTo(0, document.body.scrollHeight);
}

function loadData() {
    return new Promise(resolve => {
        const data = {id: null, agree: false}
        chrome.storage.local.get(["prozenId", "prozenToken", "prozenPublisherId"], result => {
            data.id = result.prozenId;
            data.token = result.prozenToken;
            data.publisherId = result.prozenPublisherId;
            if (data.id !== undefined) {
                chrome.storage.local.get([NOINDEX_KEY + data.id], result => {
                    const agree = result [NOINDEX_KEY + data.id];
                    if (agree !== undefined) {
                        data.agree = agree;
                    } else {
                        data.agree = false;
                    }
                    resolve(data);
                });
            }
        });
    });
}

class PublicationData {
    constructor(wData) {
        const jsonData = JSON.parse(wData);
        this.type = this.jsonData.publication.content.type;
        this.hasAdvBlocks = true; // только для статей?
        if (this.type === "article") {
            if (jsonData.adData.length >0) {
                this.hasAdvBlocks = jsonData.adData.params.hasOwnProperty("articleMobileTopBlockParams");
            }
        }
    }
}