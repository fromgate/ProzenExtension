const API_URL = "https://dzen.ru/api/v3/launcher/more?country_code=ru&clid=700&";

const NOINDEX_KEY = "prozen-noindex-agree-";

const CHECK_RESULT_NOINDEX = "check-noindex";
const CHECK_RESULT_OK = "check-ok";
const CHECK_RESULT_FAIL = "check-fail";
const CHECK_RESULT_PAGEDATA_FAIL = "check-fail-pagedata";
const CHECK_RESULT_BANNED = "check-banned";
const CHECK_RESULT_404 = "check-404";
const CHECK_RESULT_PAGEDATA_COVID = "check-covid";
const CHECK_RESULT_PAGEDATA_DMCAMUSIC = "check-music-dmca";
const CHECK_RESULT_PAGEDATA_NOADV = "check-adblocks";
const CHECK_COMMENTS_OFF = "check-comments-off";
const CHECK_COMMENTS_SUBSCRIBERS = "check-comments-subscribers";
const CHECK_COMMENTS_ALL = "check-comments-all";

const CHECK_THEMATIC_BEAUTY = "beauty";
const CHECK_THEMATIC_SPORT = "sport";
const CHECK_THEMATIC_TRAVEL = "travel";
const CHECK_THEMATIC_FOOD = "food";
const CHECK_THEMATIC_SCIENCE = "science";
const CHECK_THEMATIC_CULTURE = "culture";
const CHECK_THEMATIC_ECONOMY = "economy";
const CHECK_THEMATIC_IT = "it";
const CHECK_THEMATIC_AUTO = "auto";
const CHECK_THEMATIC_GAMES = "games";
const CHECK_THEMATIC_PETS = "pets";
const CHECK_THEMATIC_SPECPROJECTS = "specprojects";
const CHECK_THEMATIC_UNKNOWN = "unknown";
const CHECK_THEMATIC_UNSET = "unset";
const THEMATICS = [CHECK_THEMATIC_BEAUTY, CHECK_THEMATIC_SPORT, CHECK_THEMATIC_TRAVEL,
    CHECK_THEMATIC_FOOD, CHECK_THEMATIC_SCIENCE, CHECK_THEMATIC_CULTURE, CHECK_THEMATIC_ECONOMY,
    CHECK_THEMATIC_IT, CHECK_THEMATIC_AUTO, CHECK_THEMATIC_GAMES, CHECK_THEMATIC_PETS, CHECK_THEMATIC_SPECPROJECTS];

const ALL_CHECK_RESULT_MESSAGES = {};

const picker = new Litepicker({
    element: document.getElementById("start-date"),
    elementEnd: document.getElementById("end-date"),
    singleMode: false,
    dropdowns: {"minYear": 2017, "months": false, "years": true},
    numberOfColumns: 2,
    numberOfMonths: 2,
    format: "DD-MM-YYYY",
    lang: "ru-RU",
    tooltipText: {one: "день", few: "дня", many: "дней"},
    position: "bottom left",
    allowRepick: true,
    plugins: ["ranges"],
    ranges: {
        position: "left",
        customRanges: getCustomRanges()
    }
});
picker.getDayAfterEnd = function () {
    const dateEnd = this.getEndDate();
    dateEnd.setDate(dateEnd.getDate() + 1);
    return dateEnd;
};

picker.isDateInRage = function (date) {
    const dateStart = this.getStartDate();
    const dateEnd = this.getDayAfterEnd();
    return (date >= dateStart.getTime() && date <= dateEnd.getTime());
};

function getCustomRanges() {
    const ranges = {};
    const today = new Date();
    const year = today.getFullYear();
    ranges["Последние 7 дней"] = [new Date(new Date().setDate(today.getDate() - 7)), today];
    ranges["Последние 30 дней"] = [new Date(new Date().setDate(today.getDate() - 30)), today];
    ranges["Текущий месяц"] = [new Date(new Date().setDate(1)), today];
    ranges["Последние 180 дней"] = [new Date(new Date().setDate(today.getDate() - 180)), today];
    ranges[year.toString() + " год"] = [new Date(year, 0), today];
    ranges[(year - 1).toString() + " год"] = [new Date(year - 1, 0), new Date(year, 0)];
    ranges[(year - 2).toString() + " год"] = [new Date(year - 2, 0), new Date(year - 1, 0)];
    ranges[(year - 3).toString() + " год"] = [new Date(year - 3, 0), new Date(year - 2, 0)];
    return ranges;
}

picker.setDateRange(new Date(new Date().setDate(new Date().getDate() - 30)), new Date());

function clearDateRange() {
    picker.setDateRange("30-05-2017", new Date());
}

document.getElementById("range-clear").onclick = clearDateRange;

ALL_CHECK_RESULT_MESSAGES [CHECK_RESULT_BANNED] = {
    tag: "❌", name: "Публикация ограничена",
    text: "На публикации стоит отметка о блокировке.\nСкорее всего она не показывается пользователям.\nНайдите её в Студии и если она действительно заблокирована, обратитесь в службу поддержки."
};
ALL_CHECK_RESULT_MESSAGES [CHECK_RESULT_NOINDEX] = {
    tag: "🤖", name: "Снята индексация", text: `Обнаружен мета-тег <meta name="robots" content="noindex" />
Публикация не индексируется поисковиками.
Примечание: связь этого тега с показами,
пессимизацией и иными ограничениями канала
официально не подтверждена.`
};
ALL_CHECK_RESULT_MESSAGES [CHECK_RESULT_FAIL] = {
    tag: "❓", name: "Ошибка загрузки (ошибка связи?)",
    text: "Расширение не смогло загрузить страницу публикации (возможно ошибка связи).\n"
        + "Надо проверить статью вручную или, если таких ошибок много,\n"
        + "повторить проверку позднее."
};
ALL_CHECK_RESULT_MESSAGES [CHECK_RESULT_404] = {
    tag: "⛔", name: "Ошибка 404 (страницы нет)",
    text: "Страница публикации вернула 404-ую ошибку.\nЭто может быть признаком блокировки публикации."
};
ALL_CHECK_RESULT_MESSAGES [CHECK_RESULT_PAGEDATA_COVID] = {
    tag: "😷", name: "Коронавирусная метка",
    text: "На публикации обнаружена метка об упоминании COVID-19"
};
ALL_CHECK_RESULT_MESSAGES [CHECK_RESULT_PAGEDATA_DMCAMUSIC] = {
    tag: "🎹", name: "DMCA (музыка)",
    text: "Материал содержит музыку, нарушающую чьи-то авторские права (Предположительно!)"
};
ALL_CHECK_RESULT_MESSAGES [CHECK_RESULT_PAGEDATA_NOADV] = {
    tag: "🪙", name: "Реклама не обнаружена",
    text: "У статьи отключены рекламные блоки"
};
ALL_CHECK_RESULT_MESSAGES [CHECK_RESULT_PAGEDATA_FAIL] = {
    tag: "⚠️", name: "Сбой обработки (ошибка расширения?)",
    text: "Сбой обработки данных страницы.\n" +
        "Не проверено наличие монетизации и метки COVID-19.\n" +
        "Надо проверить публикацию вручную или, если таких ошибок много,\n" +
        "повторить проверку позднее.\n\n"
};
ALL_CHECK_RESULT_MESSAGES[CHECK_COMMENTS_OFF] = {
    tag: "🤐",
    name: "Комментарии отключены",
    text: "В публикации отключены комментарии"
};
ALL_CHECK_RESULT_MESSAGES[CHECK_COMMENTS_SUBSCRIBERS] = {
    tag: "🤫",
    name: "Комментарии для подписчиков",
    text: "Комментарии в публикации открыты только для подписчиков"
};
ALL_CHECK_RESULT_MESSAGES[CHECK_COMMENTS_ALL] = {
    tag: "😬",
    name: "Комментарии для всех",
    text: "Комментарии в публикации открыты для всех"
};

// thematics: beauty sport travel food science culture economy it auto games
ALL_CHECK_RESULT_MESSAGES[CHECK_THEMATIC_BEAUTY] = {
    tag: "💄", name: "Красота и стиль",
    text: "Тематика: красота и стиль"
};
ALL_CHECK_RESULT_MESSAGES[CHECK_THEMATIC_SPORT] = {tag: "⚽", name: "Спорт", text: "Тематика: спорт"};
ALL_CHECK_RESULT_MESSAGES[CHECK_THEMATIC_TRAVEL] = {
    tag: "✈️", name: "Путешествия",
    text: "Тематика: путешествия"
};
ALL_CHECK_RESULT_MESSAGES[CHECK_THEMATIC_FOOD] = {tag: "🍰", name: "Еда", text: "Тематика: еда"};
ALL_CHECK_RESULT_MESSAGES[CHECK_THEMATIC_SCIENCE] = {tag: "🔬", name: "Наука", text: "Тематика: наука"};
ALL_CHECK_RESULT_MESSAGES[CHECK_THEMATIC_CULTURE] = {tag: "🖼️", name: "Культура", text: "Тематика: культура"};
ALL_CHECK_RESULT_MESSAGES[CHECK_THEMATIC_ECONOMY] = {tag: "💸", name: "Экономика", text: "Тематика: экономика"};
ALL_CHECK_RESULT_MESSAGES[CHECK_THEMATIC_IT] = {tag: "💻", name: "Технологии", text: "Тематика: технологии"};
ALL_CHECK_RESULT_MESSAGES[CHECK_THEMATIC_AUTO] = {tag: "🚗", name: "Авто", text: "Тематика: авто"};
ALL_CHECK_RESULT_MESSAGES[CHECK_THEMATIC_GAMES] = {tag: "🎮", name: "Игры", text: "Тематика: игры"};
ALL_CHECK_RESULT_MESSAGES[CHECK_THEMATIC_PETS] = {tag: "🐈", name: "Питомцы", text: "Тематика: питомцы"};
ALL_CHECK_RESULT_MESSAGES[CHECK_THEMATIC_SPECPROJECTS] = {tag: "🌟", name: "Спецпроекты", text: "Тематика: cпецпроекты"};
ALL_CHECK_RESULT_MESSAGES[CHECK_THEMATIC_UNKNOWN] = {
    tag: "🧩", name: "Неизвестная тема",
    text: "Подтема или неизвестная (новая?) тематика"
};
ALL_CHECK_RESULT_MESSAGES[CHECK_THEMATIC_UNSET] = {tag: "🎈", name: "Нет темы", text: "Тематика не указана"};


let AGREE = false;

let id;
let publications = [];
let newPublications = [];
let publisherId;
let token;
const switchIds = [];
const disabledByDefault = [CHECK_COMMENTS_ALL, CHECK_COMMENTS_SUBSCRIBERS, CHECK_COMMENTS_OFF,
    CHECK_THEMATIC_UNSET].concat(THEMATICS);

const VISIBLE = ["init_screen", "start_text", "spinner", "progress", "search_result", "disclaimer",
    "search_msg_empty", "not_found", "nothing_to_check", "channel_none"];

showWarning();
document.getElementById("agree").onclick = clickAgree;
document.getElementById("start_button").onclick = loadPublicationsAndSearch;
document.getElementById("thematic-clear").onclick = () => {
    setThematics(false);
};
document.getElementById("thematic-all").onclick = () => {
    setThematics(true);
};

initSwitches();
loadOptions();
start();

function start() {
    showElement("spinner");
    loadData().then(data => {
        id = data.id;
        token = data.token;
        publisherId = data.publisherId;
        AGREE = data.agree;
        setTimeout(showWarning, 1);
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

function setThematics(check) {
    THEMATICS.forEach(thematic => {
        setCheckbox(thematic, check);
    });
    setCheckbox(CHECK_THEMATIC_UNSET, false);
    saveOptions();
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
            chrome.storage.local.get([NOINDEX_KEY + id], result => {
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


// Фильтр результатов провеки (показывать только нужные)
function getShowState(checkState) {
    const showState = new Set();
    for (let state of checkState) {
        if (displayCheckResult(state)) {
            showState.add(state);
        }
    }
    return showState;
}

// article brief short_video gif gallery
function isContentTypeSelected(type) {
    const el = document.getElementById(`content-${type}`);
    if (el == null || el.checked == null) return false;
    return el.checked;
}

async function executeSearch() {
    showElement("search_result");
    let count = 0;
    let countRobots = 0;
    let links = "";
    const checks = new Set();

    const publicationsToCheck = publications.filter((card) => {
        return isContentTypeSelected(card.type) && picker.isDateInRage(card.addTime);
    });

    if (publicationsToCheck.length > 0) {
        showProgress(0, publicationsToCheck.length);

        for (const card of publicationsToCheck) {
            if (!["post", "narrative", "gallery"].includes(card.type)) {
                count++;
                progress(count);

                const checkState = new Set();
                if (card.isBanned) {
                    checkState.add(CHECK_RESULT_BANNED);
                }

                if (fullCheck()) {
                    const loadState = await checkRobotNoNoIndex(card);
                    loadState.forEach(item => checkState.add(item));
                }

                if (checkState.size === 0) {
                    checkState.add(CHECK_RESULT_OK);
                }

                if (checkState.size > 0 && !checkState.has(CHECK_RESULT_OK)) {
                    const showState = getShowState(checkState);
                    showState.forEach(item => checks.add(item));
                    if (showState.size > 0) {
                        addSearchResult(card, showState);
                        links += `${card.url}\n`;
                        scrollToBottom();
                        countRobots++;
                    }
                }
            }
        }

        if (countRobots === 0) {
            showElement("not_found");
        } else {
            addLegend(checks);
            addListFooter(count, countRobots, links);
        }
        hideProgress();
    } else {
        showElement("nothing_to_check");
    }
}


function loadPublicationsAndSearch() {
    showElement("spinner");
    //const findAll = document.getElementById("radio_find_all").checked;
    loadAllPublications(true).then(cards => {
        publications = cards;
        executeSearch();
    });
    return false;
}

async function checkRobotNoNoIndex(card) {
    return new Promise(resolve => {
        const xhr = new XMLHttpRequest();
        xhr.onload = () => {
            const document = xhr.responseXML;
            const checks = new Set();

            if (xhr.status === 404) {
                checks.add(CHECK_RESULT_404);
            } else {
                const metas = document.head.getElementsByTagName("meta");
                for (let i = 0; i < metas.length; i++) {
                    const meta = metas[i];
                    if (meta.getAttribute("name") === "robots") {
                        if (meta.getAttribute("content").includes("noindex")) {
                            checks.add(CHECK_RESULT_NOINDEX);
                        }
                    } else if (meta.getAttribute("property") === "robots"
                        && meta.getAttribute("content").includes("none")) {
                        checks.add(CHECK_RESULT_NOINDEX);
                    }
                }
                if (!["gif", "short_video"].includes(card.type)) {
                    const scriptData = document.getElementById("all-data");
                    if (scriptData?.innerText != null) {
                        const pageChecks = checkPublicationPage(scriptData.innerText);
                        pageChecks.forEach(item => checks.add(item));
                    } else {
                        checks.add(CHECK_RESULT_PAGEDATA_FAIL);
                    }
                } else {
                    const scripts = document.body.getElementsByTagName("script"); //querySelector("script");
                    let videoScriptData = null;

                    for (let i = 0; i < scripts.length; i++) {
                        const scriptData = scripts[i];
                        const content = scriptData?.textContent;
                        if (content != null && content.includes("MICRO_APP_SSR_DATA")) {
                            videoScriptData = content;
                            break;
                        }
                    }
                    if (videoScriptData != null) {
                        const pageChecks = checkVideoPage(videoScriptData);
                        pageChecks.forEach(item => checks.add(item));
                    } else {
                        checks.add(CHECK_RESULT_PAGEDATA_FAIL);
                    }
                }
            }
            if (checks.size === 0) {
                checks.add(CHECK_RESULT_OK);
            }
            resolve(checks);
        };
        xhr.onerror = () => {
            resolve(CHECK_RESULT_FAIL);
        };
        xhr.open("GET", card.url);
        xhr.responseType = "document";
        xhr.send();
    });
}

function getDataLine(scriptLines, prefix, removePrefix = true) {
    let wData = "";
    const lines = scriptLines.split("\n");

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (removePrefix && line.startsWith(prefix)) {
            wData = line.slice(prefix.length, -1);
            break;
        }
    }
    return wData;
}


function getVideoDataLine(scriptLines) {
    let txtData = "";
    if (scriptLines.includes("{\"data\":{\"MICRO_APP_SSR_DATA")) {
        const begin = scriptLines.indexOf("{\"MICRO_APP_SSR_DATA");
        if (begin > 0) {
            txtData = scriptLines.slice(begin, -1 * ("}})}();".length));
        }
    }
    return txtData;
}


function checkVideoPage(scriptLines) {
    const publicationChecks = new Set();
    let vData = getVideoDataLine(scriptLines);

    try {
        const videoObj = JSON.parse(vData);
        //const serverStateObj = videoObj[Object.keys(videoObj)[0]];
        //const items = serverStateObj.videoViewer.items;
        //const item = items[Object.keys(items)[0]];
        const item = videoObj.MICRO_APP_SSR_DATA.settings.exportData.video;
        // /MICRO_APP_SSR_DATA/settings/exportData/video/covid_19
        if (item.covid_19 || item.covid19) {
            publicationChecks.add(CHECK_RESULT_PAGEDATA_COVID);
        }

        // /MICRO_APP_SSR_DATA/settings/exportData/video/isDmcaMusicCopyright
        if (item.isDmcaMusicCopyright) {
            publicationChecks.add(CHECK_RESULT_PAGEDATA_DMCAMUSIC);
        }

        // /MICRO_APP_SSR_DATA/settings/exportData/video/adDisable

        // /MICRO_APP_SSR_DATA/settings/exportData/video/commentsEnabled
        if (!item.commentsEnabled) {
            publicationChecks.add(CHECK_COMMENTS_OFF);
        }

        // /MICRO_APP_SSR_DATA/settings/exportData/video/adBlocks/TOP_SIDEBAR/rsyaAdData/blockId
        const adBlocks = item?.adBlocks;
        if (adBlocks?.TOP_SIDEBAR?.rsyaAdData?.blockId == null
            && adBlocks?.BOTTOM_PLAYER?.rsyaAdData?.blockId == null
            && adBlocks?.LIVE_ADS_BANNER?.rsyaAdData?.blockId == null) {
            publicationChecks.add(CHECK_RESULT_PAGEDATA_NOADV);
        }

        // /data/MICRO_APP_SSR_DATA/settings/exportData/video/thematicBanners
        const thematicBanners = item?.thematicBanners;
        checkThematicBanners(thematicBanners).forEach(checkBanner => publicationChecks.add(checkBanner));

    } catch (e) {
        console.log(e);
        publicationChecks.add(CHECK_RESULT_PAGEDATA_FAIL);
    }
    return publicationChecks;
}

function checkThematicBanners(thematicBanners) {
    const checkBanners = new Set();
    if (thematicBanners?.length > 0) {
        thematicBanners.forEach(thematicBanner => {
            if (THEMATICS.includes(thematicBanner.tabId)) {
                checkBanners.add(thematicBanner.tabId);
            } else {
                checkBanners.add(CHECK_THEMATIC_UNKNOWN);
            }
        });
    } else {
        checkBanners.add(CHECK_THEMATIC_UNSET);
    }
    return checkBanners;
}

function checkPublicationPage(scriptLines) {
    const publicationChecks = new Set();
    const wData = getDataLine(scriptLines, "  w._data = ");
    const thematicBannerData = getDataLine(scriptLines, "  w._thematicBanners = ");

    try {
        const thematicBanners = JSON.parse(thematicBannerData);
        checkThematicBanners(thematicBanners).forEach(checkBanner => publicationChecks.add(checkBanner));

        const pageObj = JSON.parse(wData);
        const type = pageObj.publication.content.type;

        if (pageObj.publication.covid19Mentioned) {
            publicationChecks.add(CHECK_RESULT_PAGEDATA_COVID);
        }

        if (pageObj.publication.dmcaMusicCopyright) {
            publicationChecks.add(CHECK_RESULT_PAGEDATA_DMCAMUSIC);
        }

        if (type === "article" && (
            pageObj.adData?.adBlocks?.["desktop-footer"]?.rsyaAdData?.blockId == null
            && pageObj.adData?.adBlocks?.["desktop-header"]?.rsyaAdData?.blockId == null
            && pageObj.adData?.adBlocks?.["desktop-right"]?.rsyaAdData?.blockId == null
            && pageObj.adData?.adBlocks?.["desktop-inside"]?.rsyaAdData?.blockId == null
        )) {
            publicationChecks.add(CHECK_RESULT_PAGEDATA_NOADV);
        }

        switch (pageObj.publication.visibleComments) {
            case "invisible":
                publicationChecks.add(CHECK_COMMENTS_OFF);
                break;
            case "subscribe-visible":
                publicationChecks.add(CHECK_COMMENTS_SUBSCRIBERS);
                break;
            case "visible":
                publicationChecks.add(CHECK_COMMENTS_ALL);
                break;
        }
    } catch (e) {
        console.log(e);
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

function displayCheckResult(resultId) {
    const checkbox = document.getElementById(resultId);
    return checkbox == null || checkbox.checked;
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
        case "short_video":
            icon.setAttribute("class", "icon_video span_icon");
            icon.setAttribute("title", "Ролик");
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

    for (const [key, value] of Object.entries(ALL_CHECK_RESULT_MESSAGES)) {
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
    if (["article", "gif", "short_video"].includes(card.type)) {
        div.appendChild(document.createElement("br"));
        const span = document.createElement("span");
        span.innerText = card.snippet == null || card.snippet.length === 0 ? "Описание не указано" : card.snippet.slice(0, 100);
        div.appendChild(span);
    }
    return div;
}

function addLegend(checks) {
    const div = document.createElement("div");
    div.setAttribute("class", "section");
    const p = document.createElement("p");

    const strong1 = document.createElement("strong");
    strong1.innerText = "Условные обозначения";
    p.append(strong1);

    const br = document.createElement("br");
    p.append(br);

    Array.from(checks).forEach((item, i) => {
        const checkItem = ALL_CHECK_RESULT_MESSAGES[item];
        const iconSpan = document.createElement("span");
        iconSpan.innerText = `${checkItem.tag} ${checkItem.name}`;
        iconSpan.title = checkItem.text;
        if (i > 0) {
            const dot = document.createElement("span");
            dot.innerText = ", ";
            p.append(dot);
        }
        p.append(iconSpan);
    });
    const hr = document.createElement("hr");
    p.append(hr);
    div.append(p);

    const searchResult = document.getElementById("search_result");
    searchResult.appendChild(div);

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
    text2.innerText = "Соответствует критериям: ";
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


function onCheckboxClick(switchId) {
    setCheckbox(switchId, document.getElementById(switchId).checked, true);
}

function loadData() {
    return new Promise(resolve => {
        const data = {id: null, agree: false};
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

function loadOptions() {
    chrome.storage.local.get(switchIds, options => {
        switchIds.forEach(switchId => {
            let save = false;
            if (options.hasOwnProperty(switchId)) {
                setCheckbox(switchId, options[switchId]);
            } else {
                const initValue = !disabledByDefault.includes(switchId);
                setCheckbox(switchId, initValue);
                save = true;
            }
            if (save) {
                saveOptions();
            }
        });
    });
}

function setCheckbox(switchId, switchState, save = false) {
    const switchEl = document.getElementById(switchId);
    switchEl.checked = switchState;
    if (save) {
        saveOptions();
    }
}

function saveOptions() {
    const options = {};
    switchIds.forEach(switchId => {
        options[switchId] = document.getElementById(switchId).checked;
    });
    chrome.storage.local.set(options);
}

function initSwitches() {
    const switchElements = document.getElementsByClassName("switch-checkbox");
    for (let i = 0; i < switchElements.length; i++) {
        const el = switchElements[i];
        const switchId = el.id;
        switchIds.push(switchId);
        document.getElementById(switchId).addEventListener("click", onCheckboxClick.bind(null, switchId));
    }
}

function fullCheck() {
    let fullCheck = false;
    for (let state of switchIds) {
        if (state !== CHECK_RESULT_BANNED) {
            if (displayCheckResult(state)) {
                fullCheck = true;
                break;
            }
        }
    }
    return fullCheck;
}