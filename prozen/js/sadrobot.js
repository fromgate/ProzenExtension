const API_URL = "https://dzen.ru/api/v3/launcher/more?country_code=ru&clid=700&";

const NOINDEX_KEY = "prozen-noindex-agree-";

const CHECK_RESULT_NOINDEX = "noindex"
const CHECK_RESULT_OK = "ok";
const CHECK_RESULT_FAIL = "fail";
const CHECK_RESULT_PAGEDATA_FAIL = "fail_page_data";
const CHECK_RESULT_BANNED = "isBanned";
const CHECK_RESULT_404 = "404";
const CHECK_RESULT_PAGEDATA_COVID = "covid19"
const CHECK_RESULT_PAGEDATA_DMCAMUSIC = "DMCA_Music"
const CHECK_RESULT_PAGEDATA_NOADV = "no_adv";

const CHECK_RESULT_MESSAGES = {}

CHECK_RESULT_MESSAGES [CHECK_RESULT_BANNED] = {
    tag: "❌",
    text: "На публикации стоит отметка о блокировке.\nСкорее всего она не показывается пользователям.\nНайдите её в Студии и если она действительно заблокирована, обратитесь в службу поддержки."
};
CHECK_RESULT_MESSAGES [CHECK_RESULT_NOINDEX] = {
    tag: "🤖", text: `Обнаружен мета-тег <meta name="robots" content="noindex" />
Публикация не индексируется поисковиками.
Примечание: связь этого тега с показами,
пессимизацией и иными ограничениями канала
официально не подтверждена.`
};
CHECK_RESULT_MESSAGES [CHECK_RESULT_FAIL] = {
    tag: "❓",
    text: "Расширение не смогло загрузить страницу публикации (возможно ошибка связи).\nНадо проверить статью вручную или, если таких ошибок много,\nповторить проверку позднее."
};
CHECK_RESULT_MESSAGES [CHECK_RESULT_404] = {
    tag: "⛔",
    text: "Страница публикации вернула 404-ую ошибку.\nЭто может быть признаком блокировки публикации."
};
CHECK_RESULT_MESSAGES [CHECK_RESULT_PAGEDATA_COVID] = {
    tag: "😷",
    text: "На публикации обнаружена метка об упоминании COVID-19"
};
CHECK_RESULT_MESSAGES [CHECK_RESULT_PAGEDATA_DMCAMUSIC] = {
    tag: "🎹",
    text: "Материал содержит музыку, нарушающую чьи-то авторские права (Предположительно!)"
};
CHECK_RESULT_MESSAGES [CHECK_RESULT_PAGEDATA_NOADV] = {tag: "🪙", text: "У статьи отключены рекламные блоки"};
CHECK_RESULT_MESSAGES [CHECK_RESULT_PAGEDATA_FAIL]= {tag: "⁉️", text: "Сбой обработки данных страницы.\nНе проверено наличие монетизации и метки COVID-19.\nНадо проверить публикацию вручную или, если таких ошибок много,\nповторить проверку позднее."}

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
    const maxCount = limitCount < 0 ? publications.length : Math.min(limitCount, publications.length);
    showProgress(0, maxCount);
    for (const card of publications) {
        if (!["post", "narrative"].includes(card.type)) {
            count++;
            progress(count);

            const checkState = new Set()
            if (card.isBanned) {
                checkState.add(CHECK_RESULT_BANNED);
            }
            const loadState = await checkRobotNoNoIndex(card);
            loadState.forEach(item => checkState.add(item));

            if (checkState.size === 0) {
                checkState.add(CHECK_RESULT_OK);
            }

            if (checkState.size > 0 && !checkState.has(CHECK_RESULT_OK)) {
                addSearchResult(card, checkState);
                if (checkState.has(CHECK_RESULT_NOINDEX)) {
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

            if (xhr.status === 404) {
                checks.add(CHECK_RESULT_404);
            } else {
                const metas = document.head.getElementsByTagName('meta');
                for (let i = 0; i < metas.length; i++) {
                    const meta = metas[i];
                    if (meta.getAttribute('name') === "robots") {
                        if (meta.getAttribute('content') === "noindex") {
                            checks.add(CHECK_RESULT_NOINDEX);
                        }
                    } else if (meta.getAttribute("property") === "robots"
                        && meta.getAttribute("content") === "none") {
                        checks.add(CHECK_RESULT_NOINDEX);
                    }
                }
                if (card.type !== "gif") {
                    const scriptData = document.getElementById("all-data");
                    if (scriptData?.innerText != null) {
                        const pageChecks = checkPublicationPage(scriptData.innerText);
                        pageChecks.forEach(item => checks.add(item));
                    } else {
                        checks.add(CHECK_RESULT_PAGEDATA_FAIL);
                    }
                } else {
                    const scriptData = document.body.querySelector("script");
                    if (scriptData?.innerText != null) {
                        const pageChecks = checkVideoPage(scriptData.innerText);
                        pageChecks.forEach(item => checks.add(item));
                    } else {
                        checks.add(CHECK_RESULT_PAGEDATA_FAIL);
                    }
                }
            }
            if (checks.size === 0) {
                checks.add(CHECK_RESULT_OK)
            }
            resolve(checks);
        };
        xhr.onerror = () => {
            resolve(CHECK_RESULT_FAIL)
        }
        xhr.open("GET", card.url);
        xhr.responseType = "document";
        xhr.send();
    });
}


function getDataLine (scriptLines, prefix) {
    let wData = "";
    const lines = scriptLines.split("\n");

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (line.startsWith(prefix)) {
            wData = line.slice(prefix.length, -1);
            break;
        }
    }
    return wData;
}

function checkVideoPage(scriptLines) {
    const publicationChecks = new Set();
    let vData = getDataLine(scriptLines, "        var data = ");
    try {
        const videoObj = JSON.parse(vData);
        const serverStateObj = videoObj[Object.keys(videoObj)[0]];
        const items = serverStateObj.videoViewer.items;
        const item = items[Object.keys(items)[0]];
        if (item.covid_19 || item.covid19) {
            publicationChecks.add(CHECK_RESULT_PAGEDATA_COVID);
        }

        if (item.isDmcaMusicCopyright) {
            publicationChecks.add(CHECK_RESULT_PAGEDATA_DMCAMUSIC);
        }

        if (item?.adBlocks?.DOC2DOC?.rsyaAdData?.blockId == null) {
            publicationChecks.add (CHECK_RESULT_PAGEDATA_NOADV);
        }


        /*
                if (serverStateObj.videoViewer?.adBlocks?.DOC2DOC?.rsyaAdData?.blockId == null) {
            publicationChecks.add (CHECK_RESULT_PAGEDATA_NOADV);
        }
         */
    } catch (e) {
        publicationChecks.add(CHECK_RESULT_PAGEDATA_FAIL);
    }
    return publicationChecks;
}
function checkPublicationPage(scriptLines) {
    const publicationChecks = new Set();
    const wData = getDataLine(scriptLines, "  w._data = ");
    try {
        const pageObj = JSON.parse(wData);
        const type = pageObj.publication.content.type;

        if (pageObj.publication.covid19Mentioned) {
            publicationChecks.add(CHECK_RESULT_PAGEDATA_COVID);
        }

        if (pageObj.publication.dmcaMusicCopyright) {
            publicationChecks.add(CHECK_RESULT_PAGEDATA_DMCAMUSIC)
        }

        if (type === "article" && pageObj.adData?.topAdv?.rsyaAdData?.blockId == null) {
            publicationChecks.add(CHECK_RESULT_PAGEDATA_NOADV);
        }

    } catch (e) {
        publicationChecks.add(CHECK_RESULT_PAGEDATA_FAIL);
    }
    return publicationChecks;
}

function addSearchResult(card, state = new Set([CHECK_RESULT_OK])) {
    const a = document.createElement("a");
    a.setAttribute("href", card.url);
    a.setAttribute("target", "_blank");
    const div = cardToDiv(card, state);
    a.appendChild(div);
    const searchResult = document.getElementById("search_result");
    searchResult.appendChild(a);
    searchResult.appendChild(document.createElement("hr"));
}

function cardToDiv(card, state) {
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


    for (const [key, value] of Object.entries(CHECK_RESULT_MESSAGES)) {
        if (state.has(key)) {
            const marked = document.createElement("mark");
            marked.innerText = value.tag;
            marked.setAttribute("title", value.text);
            div.appendChild(marked);
        }

    }

    const strong = document.createElement("strong");
    strong.innerText = card.title;
    div.appendChild(strong);
    if (card.type === "article" || card.type === "gif") {
        div.appendChild(document.createElement("br"));
        const span = document.createElement("span");
        span.innerText = card.snippet == null || card.snippet.length === 0 ? "Описание не указано" : card.snippet.slice(0, 100);
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
    text2.innerText = "Требует внимания: ";
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
