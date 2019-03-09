start();

const URL_API_PUBLICATIONS = "https://zen.yandex.ru/media-api/publisher-publications-stat?publicationsIds=";
const URL_ZEN_ID = "https://zen.yandex.ru/id/";
const URL_API_MEDIA = "https://zen.yandex.ru/media-api/id/";
const URL_API_GET_PUBLICATION = "https://zen.yandex.ru/media-api/get-publication?publicationId=";
const URL_API_PUBLICATION_VIEW_STAT = "https://zen.yandex.ru/media-api/publication-view-stat?publicationId=";

const publications = new Map();
const observers = [];
let token;
let data;
let publisherId;

function main() {
    const pageType = getPageType();
    if (pageType === "unknown") {
        return;
    }
    if (pageType === "article") {
        setTimeout (articleShowStats, 300);
        return;
    }
    publisherId = getPublisherId();
    if (token === undefined || publisherId === undefined) {
        return;
    }
    if (pageType !== "edit") {
        showBalanceAndMetrics ();
    }

    if (pageType === "main") {
        addSearchInput();
        registerTargetObserver();
    }
}

///////////////////////////////////
// Functions
///////////////////////////////////

function start() {
    window.browser = (function () {
        return window.msBrowser ||
            window.browser ||
            window.chrome;
    })();

    const css = createElement("link");
    css.setAttribute("rel", "stylesheet");
    css.setAttribute("type", "text/css");
    css.setAttribute("href", browser.extension.getURL("css/prozen.css"));
    document.head.appendChild(css);

    const script = createElement("script");
    script.setAttribute("type","text/javascript");
    script.setAttribute("src", browser.extension.getURL("js/page.js"));
    document.body.appendChild(script);

    window.addEventListener("message", function(event) {
        if (event.source !== window)
            return;
        if (event.data.type && (event.data.type === "prozen-data")) {
            token = event.data.text;
            data = event.data.jsonData;
            main ();
        }
    });
}

function registerTargetObserver() {
    const target = document.getElementsByClassName("publications-groups-view")[0];
    const observer = new MutationObserver(function(mutations) {
        mutations.forEach(function(mutation) {
            if (mutation.type === 'childList') {
                setUnprocessedPublications();
                loadCardsAll();
                processCards();
                registerCardObservers();
            }
        });
    });
    observer.observe(target, {childList: true});
}

function registerCardObservers() {
    for (let i = 0; i < observers.length; i++) {
        const oldObserver = observers.pop();
        oldObserver.disconnect();
    }
    const targets = document.getElementsByClassName('publications-groups-view__pubs-container');
    for (let i = 0; i < targets.length; i++) {
        const target = targets[i];
        const observer = new MutationObserver(function(mutations) {
            mutations.forEach(function(mutation) {
                if (mutation.type === 'childList') {
                    if (mutation.addedNodes !== undefined && mutation.addedNodes.length > 0) {
                        mutation.addedNodes.forEach(function (node) {
                            const ids = loadCards (node);
                            processCards(ids);
                        });
                    }
                }
            });
        });
        const config = {
            attributes: false,
            childList: true,
            characterData: false
        };
        observer.observe(target, config);
        observers.push(observer);
    }
}

function loadCardsAll() {
    return loadCards(document);
}

function loadCards(soureElement) {
    const ids = [];
    const cards = soureElement.getElementsByClassName("card-cover-publication");
    for (i = 0; i < cards.length; i++) {
        const card = cards[i];
        const cardLinks = card.getElementsByTagName("a");
        if (cardLinks === undefined || cardLinks.length === 0) {
            continue;
        }
        const postLink = cardLinks[0].getAttribute("href");
        if (postLink.startsWith("/profile/editor/id/")) {
            continue;
        }
        const publicationId = getPostIdFromUrl(postLink);
        if (publications.has(publicationId)) {
            publications.get(publicationId).card = card;
        } else {
            publications.set(publicationId, {});
            publications.get(publicationId).card = card;
            publications.get(publicationId).processed = false;
        }
        ids.push(publicationId);
    }
    return ids;
}

function articleShowStats() {
    if (data == null) {
        return;
    }
    const postId = getPostIdFromUrl (window.location.pathname);
    const dayMod = dateFormat(data.publication.content.modTime);
    const dayCreate = data.publication.addTime === undefined ? dayMod : dateFormat(data.publication.addTime);
    const showTime = dayMod !== dayCreate ? dayCreate+" ("+dayMod+ ")" : dayCreate;
    loadPublicationStat(postId).then (function (articleData) {
        const sumViewTimeSec = articleData.sumViewTimeSec;
        const views = articleData.views;
        const viewsTillEnd = articleData.viewsTillEnd;

        const elArticleDate = document.getElementsByClassName("article-stat__date")[0];
        elArticleDate.innerText = showTime;
        let counters = document.getElementsByClassName("article-stat__count");
        counters[0].innerText = views.toLocaleString(undefined, {maximumFractionDigits: 0});

        if (counters.length === 1) {
            ////article-stat__counts-wrapper
            const wrapper1 = document.getElementsByClassName("article-stat__counts-wrapper")[0];

            const wrapper2 = createElement("div", "article-stat__counts-wrapper");
            const spanIcon2 = createElement("span", "article-stat__icon article-stat__icon_type_perusal-black");
            const spanCount2 = createElement("span", "article-stat__count");
            wrapper2.appendChild(spanIcon2);
            wrapper2.appendChild(spanCount2);
            wrapper1.insertAdjacentElement("afterend", wrapper2);

            const wrapper3 = createElement("div", "article-stat__counts-wrapper");
            const spanIcon3 = createElement("span", "article-stat__icon article-stat__icon_type_time-black");
            const spanCount3 = createElement("span", "article-stat__count");
            wrapper3.appendChild(spanIcon3);
            wrapper3.appendChild(spanCount3);
            wrapper2.insertAdjacentElement("afterend", wrapper3);
            counters = document.getElementsByClassName("article-stat__count");
        }
        counters[1].innerText = viewsTillEnd.toLocaleString(undefined, {maximumFractionDigits: 0}) + " ("+infiniteAndNan(viewsTillEnd/views*100).toFixed(2)+"%)";
        counters[2].innerText = secToText(infiniteAndNan(sumViewTimeSec / viewsTillEnd));
        removeByClass("article-stat-tip");
        if (checkNoIndex()) {
            const spanIcon4 = createElement("span", "article-stat__icon icon_sad_robot");
            const wrapper4 = createElement("div", "article-stat__counts-wrapper", spanIcon4);
            wrapper4.setAttribute("title", "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
                "Статья не индексируется поисковиками.\n" +
                "Примечание: связь этого тега с показами,\n" +
                "пессимизацией и иными ограничениями канала\n" +
                "официально не подтверждена.");
            const wrapper3 = document.getElementsByClassName("article-stat__counts-wrapper")[2];
            wrapper3.insertAdjacentElement("afterend", wrapper4);
        }
    });
}

function getPostIdFromUrl(url) {
    const ln = url.replace("?from=editor", "").split("-");
    return ln[ln.length-1];
}

function getPublisherId() {
    const path = window.location.pathname;
    switch (getPageType()) {
        case "main":
            const helpButtonLinks = document.getElementsByClassName("help-button__link");
            return helpButtonLinks[0].getAttribute("href").split("=")[1];
        case "money":
        case "edit":
        case "karma":
        case "stats":
            return path.split("/")[4];
    }
    return "";
}

function getPageType() {
    const path = window.location.pathname;
    if (path.startsWith("/media/")) {
        if (data != null && data.isArticle ===  true) {
            return "article";
        }
    } else if (path.startsWith("/profile/editor/")) {
        if (path.endsWith("/money/simple")) {
            return "money";
        }
        if (path.endsWith("/edit")) {
            return "edit";
        }
        if (path.endsWith("/karma")) {
            return "karma";
        }
        if (path.endsWith("/publications-stat")) {
            return "stats";
        }
        const helpButtonLinks = document.getElementsByClassName("help-button__link");
        if (helpButtonLinks !== undefined && helpButtonLinks.length > 0) {
            return "main";
        }
    }
    return "unknown";
}

function showBalanceAndMetrics() {
    const url = URL_API_MEDIA + publisherId + "/money";
    const data = fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
    data.then(response => {
        if (response.money.isMonetezationAvaliable) {
            let money = parseFloat (response.money.simple.personalData.balance.toFixed(2));

            let total = money;
            for (var i = 0, len = response.money.simple.paymentHistory.length; i < len; i++) {
                total += parseFloat(response.money.simple.paymentHistory[i]["amount"]);
            }
            setBalance (money, total);
        }
        addMetricsButton(response.publisher.privateData.metrikaCounterId);
        addSearchButton();
        addTotalStatsButton();
        addRobotIconIfNoNoIndex (publisherId);
    });
}

function setBalance(money, total) {
    const moneyA = document.getElementsByClassName("header-menu__link")[1];
    if (money !== total) {
        const totalStr = "Всего: " + total.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + " ₽";
        const moneyDate = moneyA.getAttribute("data-tip");
        if (moneyDate !== undefined && moneyDate !== null) {
            moneyA.setAttribute("data-tip",  moneyDate +  " / " + totalStr);
        } else {
            moneyA.setAttribute("data-tip",  totalStr);
        }
    }
    moneyA.innerText = money.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + " ₽";
}

function addMetricsButton(metricsId) {
    const metricsUrl = metricsId !== undefined ? "https://metrika.yandex.ru/dashboard?id=" + metricsId : "https://metrika.yandex.ru/list";
    const metricsButton = createElement("div", "header__nav-block");
    metricsButton.setAttribute("data-multiline","true");
    metricsButton.setAttribute("data-tip","Яндекс.Метрика");
    metricsButton.setAttribute("currentitem","false");
    const metricsA = createElement("a", "control button2 button2_view_classic button2_size_m button2_theme_zen-header-tab button2_type_link");
    metricsA.setAttribute("aria-pressed","false");
    metricsA.setAttribute("tabindex","0");
    metricsA.setAttribute("aria-disabled","false");
    metricsA.setAttribute("target","_blank");
    metricsA.setAttribute("href",metricsUrl);
    const aSpan = createElement("span", "button2__text");
    metricsA.appendChild(aSpan);
    const img = createElement("img");
    img.setAttribute("src","data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTgiIGhlaWdodD0iMTYiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgeG1sbnM6c3ZnPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgICA8ZyBjbGFzcz0ibGF5ZXIiPgogICAgICAgIDx0aXRsZT5MYXllciAxPC90aXRsZT4KICAgICAgICA8ZyBmaWxsPSJub25lIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiIGlkPSJzdmdfMSI+CiAgICAgICAgICAgIDxnIGZpbGw9IiMwMDc3RkYiIGlkPSJzdGF0cyIgc3Ryb2tlPSIjMDA3N0ZGIgogICAgICAgICAgICAgICB0cmFuc2Zvcm09InRyYW5zbGF0ZSgxLjAwMDAwMCwgMS4wMDAwMDApIj4KICAgICAgICAgICAgICAgIDxwb2x5Z29uIGZpbGw9IiNmZjAwMDAiCiAgICAgICAgICAgICAgICAgICAgICAgICBzdHJva2U9IiNmZjAwMDAiCiAgICAgICAgICAgICAgICAgICAgICAgICBpZD0ic3ZnXzIiCiAgICAgICAgICAgICAgICAgICAgICAgICBwb2ludHM9IjAgNyA0IDcgNCAxNCAwIDE0Ii8+CiAgICAgICAgICAgICAgICA8cG9seWdvbgogICAgICAgICAgICAgICAgICAgICAgICBmaWxsPSIjNDI3N2NhIiBzdHJva2U9IiM0Mjc3Y2EiIGlkPSJzdmdfMyIgcG9pbnRzPSI2IDQgMTAgNCAxMCAxNCA2IDE0Ii8+CiAgICAgICAgICAgICAgICA8cG9seWdvbiBmaWxsPSIjZmZjYzAwIgogICAgICAgICAgICAgICAgICAgICAgICAgc3Ryb2tlPSIjZmZjYzAwIgogICAgICAgICAgICAgICAgICAgICAgICAgaWQ9InN2Z180IgogICAgICAgICA\n" +
        "gICAgICAgICAgICAgICAgcG9pbnRzPSIxMiAwIDE2IDAgMTYgMTQgMTIgMTQiLz4KICAgICAgICAgICAgPC9nPgogICAgICAgIDwvZz4KICAgIDwvZz4KPC9zdmc+");
    aSpan.appendChild(img);
    metricsButton.appendChild(metricsA);
    const navblocks = document.getElementsByClassName("header__nav-block");
    const last = navblocks.item(navblocks.length - 1);
    last.insertAdjacentElement("afterend", metricsButton);
}

function addSearchButton() {
    const searchButton = createElement("div", "header__nav-block");
    searchButton.setAttribute("data-multiline","true");
    searchButton.setAttribute("data-tip","Поиск");
    searchButton.setAttribute("currentitem","false");
    const searchA = createElement("a", "control button2 button2_view_classic button2_size_m button2_theme_zen-header-tab button2_type_link");
    searchA.setAttribute("aria-pressed","false");
    searchA.setAttribute("tabindex","0");
    searchA.setAttribute("aria-disabled","false");
    const aSpan = createElement("span", "button2__text");
    searchA.appendChild(aSpan);
    const img = createElement("img");
    img.setAttribute("src","data:image/svg+xml,%3C%3Fxml version='1.0' encoding='UTF-8' standalone='no'%3F%3E%3Csvg xmlns:dc='http://purl.org/dc/elements/1.1/' xmlns:cc='http://creativecommons.org/ns%23' xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns%23' xmlns:svg='http://www.w3.org/2000/svg' xmlns='http://www.w3.org/2000/svg' xmlns:sodipodi='http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd' xmlns:inkscape='http://www.inkscape.org/namespaces/inkscape' width='16px' height='16px' viewBox='0 0 21 20' version='1.1' id='svg823' sodipodi:docname='search_right %5B%231505%5D.svg' inkscape:version='0.92.3 (2405546, 2018-03-11)'%3E%3Cmetadata id='metadata827'%3E%3Crdf:RDF%3E%3Ccc:Work rdf:about=''%3E%3Cdc:format%3Eimage/svg+xml%3C/dc:format%3E%3Cdc:type rdf:resource='http://purl.org/dc/dcmitype/StillImage' /%3E%3C/cc:Work%3E%3C/rdf:RDF%3E%3C/metadata%3E%3Csodipodi:namedview pagecolor='%23ffffff' bordercolor='%23666666' borderopacity='1' objecttolerance='10' gridtolerance='10' guidetolerance='10' inkscape:pageopacity='0' inkscape:pageshadow='2' inkscape:window-width='1920' inkscape:window-height='1017' id='namedview825' showgrid='false' inkscape:zoom='42.5' inkscape:cx='10.5' inkscape:cy='10' inkscape:window-x='-8' inkscape:window-y='-8' inkscape:window-maximized='1' inkscape:current-layer='icons' /%3E%3Ctitle id='title815'%3Esearch%3C/title%3E%3Cdefs id='defs817' /%3E%3Cg id='Page-1' stroke='none' stroke-width='1' fill='none' fill-rule='evenodd'%3E%3Cg style='fill:%230077ff' transform='translate(-203,-120)' id='icons'%3E%3Cpath inkscape:connector-curvature='0' id='search' d='m 215.6,120 c -4.6389,0 -8.4,3.582 -8.4,8 0,4.418 3.7611,8 8.4,8 4.63995,0 8.4,-3.582 8.4,-8 0,-4.418 -3.76005,-8 -8.4,-8 z' sodipodi:nodetypes='sssss' style='opacity:1;fill:%230077ff;fill-opacity:1' /%3E%3Crect style='opacity:1;fill:%230077ff;fill-opacity:1;stroke:%230077ff;stroke-width:1.21700835;stroke-miterlimit:4;stroke-dasharray:none;stroke-opacity:0' id='rect833' width='4.1235108' height='6.7866111' x='240.28654' y='-52.299572' transform='matrix(0.71035493,0.70384364,-0.71035493,0.70384364,0,0)' /%3E%3C/g%3E%3C/g%3E%3C/svg%3E%0A");
    aSpan.appendChild(img);
    searchButton.appendChild(searchA);
    const navblocks = document.getElementsByClassName("header__nav-block");
    const last = navblocks.item(navblocks.length - 1);
    last.insertAdjacentElement("afterend", searchButton);
    searchButton.addEventListener('click', clickSearchButton);
}

function clickSearchButton(searchString) {
    let id;
    const textToFind = searchString === undefined ? "" : searchString;
    if (data.publisher.nickname === undefined) {
        id = "channel_id=" + publisherId;
    } else {
        id = "channel_name=" + data.publisher.nickname.raw;
    }
    chrome.storage.local.set ( {prozenId : id, prozenSearch : textToFind}, function () {
        window.open(browser.extension.getURL("search.html"));
    });
}

function addTotalStatsButton() {
    const totalStatsButton = createElement("div", "header__nav-block");
    totalStatsButton.setAttribute("data-multiline","true");
    totalStatsButton.setAttribute("data-tip","Итоговая статистика");
    totalStatsButton.setAttribute("currentitem","false");
    const searchA = createElement("a", "control button2 button2_view_classic button2_size_m button2_theme_zen-header-tab button2_type_link");
    searchA.setAttribute("aria-pressed","false");
    searchA.setAttribute("tabindex","0");
    searchA.setAttribute("aria-disabled","false");
    const aSpan = createElement("span", "button2__text");
    aSpan.setAttribute("style", "margin-top: 5px");
    searchA.appendChild(aSpan);
    const img = createElement("img");
    img.setAttribute("src","data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAAABHNCSVQICAgIfAhkiAAAAAlwSFlzAAABfAAAAXwBsrqMZwAAABl0RVh0U29mdHdhcmUAd3d3Lmlua3NjYXBlLm9yZ5vuPBoAAAE8SURBVEiJ7ZSxLkRBFIa/IxvJbrYQVEqVKPESCpViK0LhBUhcW5CrEHZXQaHbUHkAj7BRSLSiEI1tFEKiICHLHsU2946Z646djr/8ZnK+MyczA//5IWKlGxqjLHhVUo6py46JC47tI8C4l0AYtuEBryK/iOsEV1YqtFA6jrVbO7ZGhYgzYM7YvcKeNB1NWeMYkSjvLAJ3aS+HVHUigAA4kGeUCqRGUqLLKbEO9i8AqMslyrZBp3hjK4wAoEQN4TzFlIiqToYR3CMoRYM+0eExjGCIXWAmQT7pUmFfHvIIXO+gl0hngVWDNmhIK09xyDrBmo4CTZJvRbmgyGbe4tmCAkfAWIK8AsvE8uEjcP2mSygnBn0BbjJqtanJ/Pc+bVHWLbQMTGcIyjboGlGwX9Z1i67pjcQn7T57+av5AoyVRffOpHGeAAAAAElFTkSuQmCC");
    aSpan.appendChild(img);
    totalStatsButton.appendChild(searchA);
    const navblocks = document.getElementsByClassName("header__nav-block");
    const last = navblocks.item(navblocks.length - 1);
    last.insertAdjacentElement("afterend", totalStatsButton);
    totalStatsButton.addEventListener('click', clickTotalStatsButton);
}

function clickTotalStatsButton() {
    chrome.storage.local.set ( {prozenToken : token}, function () {
        window.open(browser.extension.getURL("totalstats.html"));
    });
}

function getUnproccedPublications() {
    return Array.from(publications.keys()).filter(function (key) {
        return !publications.get(key).processed;
    });
}

function setUnprocessedPublications() {
    Array.from(publications.keys()).forEach(function (key) {
        publications.get(key).processed = false;
    });
}

function loadPublicationsStat(publicationIds) {
    const url=URL_API_PUBLICATIONS + encodeURIComponent(publicationIds.join(","));
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}

function loadPublicationStat(publicationId) {
    const url=URL_API_PUBLICATION_VIEW_STAT + encodeURIComponent(publicationId);
    return fetch(url, {credentials: 'same-origin'}).then(response => response.json());
}

function loadArticle(publicationId) {
    const url=URL_API_GET_PUBLICATION + publicationId;
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}

function processCards(loadedIds) {
    const ids = loadedIds === undefined ? getUnproccedPublications() : loadedIds;
    if (ids.length === 0) {
        return;
    }
    const idsToLoad = [];
    ids.forEach (function (id) {
        if (!publications.get(id).processed) {
            idsToLoad.push(id);
        }
    });
    if (idsToLoad.length > 0) {
        loadPublicationsStat(idsToLoad).then (function (data) {
            const articles = [];
            for (let i in data.items) {
                const stat = data.items[i];
                const id = stat.publicationId;
                const card = publications.get(id);
                card.comments = stat.comments;
                card.feedShows = stat.feedShows;
                card.likes = stat.likes;
                card.shows = stat.shows;
                card.views = stat.views;
                card.sumViewTimeSec = stat.sumViewTimeSec;
                card.viewsTillEnd = stat.viewsTillEnd;
                card.readTime = card.sumViewTimeSec / card.viewsTillEnd;
                articles.push(loadArticle(id));
            }
            Promise.all(articles).then(function (articles) {
                for (let i in articles) {
                    const article = articles[i];
                    const id = article.publications[0].id;
                    const card = publications.get(id);
                    card.addTime = article.publications[0].addTime;
                    card.modTime = article.publications[0].content.modTime;
                    card.tags = article.publications[0].privateData.tags;
                    card.processed = true;
                }
            }).then(function () {
                processCardsViews(ids);
            });
        });
    } else {
        processCardsViews(ids);
    }
}


function processCardsViews (ids) {
    for (let i = 0; i< ids.length; i++) {
        const publicationId = ids[i];
        const value = publications.get(publicationId);
        if (value.card.hasChildNodes()) {
            setPublicationTime (value);
            modifyCardFooter (value);
        }
        value.processed = true;
    }
}

function setPublicationTime (pubData) {
    const dateDiv = pubData.card.getElementsByClassName("card-cover-publication__status")[0];
    if (dateDiv.innerText.match("(^Вчера)|(^Сегодня)|(^Три дня назад)|(^\\d{1,2}\\s([а-я]+)(\\s201\\d)?)")) {
        const dayMod = dateFormat(pubData.modTime);
        const dayCreate = pubData.addTime === undefined ? dayMod : dateFormat(pubData.addTime);
        dateDiv.innerText = dayCreate + (dayCreate === dayMod ? "" : " ("+dayMod+")");
    }
}

function createFooterLine(element1, element2, element3) {
    const div = document.createElement("div");
    div.setAttribute("class", "card-cover-footer-stats");
    div.setAttribute("style", "color: rgb(255, 255, 255);");

    div.appendChild(element1);
    if (element2 !== undefined) {
        div.appendChild(element2);
    }
    if (element3 !== undefined) {
        div.appendChild(element3);
    }
    return div;
}


function modifyCardFooter (pubData) {
    const cardFooters = pubData.card.getElementsByClassName("card-cover-publication__stats-container");
    if (cardFooters === undefined || cardFooters.length === 0) {
        return;
    }
    const cardFooter = cardFooters[0];
    removeChilds (cardFooter);
    const elementShows = createIcon (infiniteAndNanToStr (pubData.feedShows), "icon_shows_in_feed", "Показы");

    const erViews = firstNotZ (pubData.viewsTillEnd, pubData.views, pubData.feedShows);
    const likesEr = infiniteAndNan((pubData.likes / erViews)*100);
    const likesValue = pubData.likes === 0 ? "0 (0.00%)" : infiniteAndNanToStr(pubData.likes) + " (" + parseFloat (likesEr).toFixed(2) + "%)";
    const elementLikes = createIcon(likesValue, "icon_like", "Лайки");

    const line1 = createFooterLine (elementShows, elementLikes);
    cardFooter.appendChild(line1);

    const ctr = (parseFloat(infiniteAndNan(pubData.views / pubData.feedShows)*100)).toFixed(2);
    const elementViews = createIcon(infiniteAndNanToStr (pubData.views) + " ("+ctr+"%)", "icon_views", "Просмотры (CTR)");
    const readsPercent = ((pubData.viewsTillEnd / pubData.views)*100).toFixed(2);

    const commentsEr = infiniteAndNan((pubData.comments / erViews)*100);
    const commentsValue = pubData.comments === 0 ? "0 (0.00%)" : infiniteAndNanToStr (pubData.comments) + " (" + parseFloat (commentsEr).toFixed(2) + "%)";
    const elementComments = createIcon(commentsValue, "icon_comments", "Комментарии");
    const line2 = createFooterLine (elementViews, elementComments);
    cardFooter.appendChild(line2);

    const elementViewsTillEnd = createIcon (infiniteAndNanToStr(pubData.viewsTillEnd)+" (" +parseFloat(infiniteAndNan (readsPercent)).toFixed(2) +"%)",
        "icon_views_till_end", "Дочитывания");
    const erValue = infiniteAndNan((((pubData.comments + pubData.likes) / erViews))*100).toFixed(2) + "%";
    const elementEr = createIcon(erValue, "icon_er", "Коэффициент вовлеченности, ER");
    const line3 = createFooterLine (elementViewsTillEnd, elementEr);
    cardFooter.appendChild(line3);

    const readTimeCount = secToHHMMSS (pubData.readTime);
    const readTimeTitle = "Время дочитывания" +(pubData.readTime > 0 ? " - " + secToText(pubData.readTime) : "");
    const elementReadTime = createIcon(readTimeCount, "icon_clock", readTimeTitle);

    const elementTags = createIcon(null, "icon_tags", pubData.tags.length === 0 ? "Теги не указаны" : "Теги: " + joinByThree(pubData.tags));

    const line4 = createFooterLine (elementReadTime, elementTags);
    cardFooter.appendChild(line4);
}

function removeChilds(element) {
    while (element.firstChild) {
        element.removeChild(element.firstChild);
    }
}

function removeByClass (className) {
    const elements = document.getElementsByClassName(className);
    while(elements.length > 0){
        elements[0].parentNode.removeChild(elements[0]);
    }
}

function addDirectLinkButton(link) {
    const linkUrl = link.getAttribute("href").replace("?from=editor", "");
    const directLink = createElement("a", "action-menu__action-button");
    directLink.setAttribute("href",linkUrl);
    directLink.innerText = "Прямая ссылка";
    link.insertAdjacentElement("afterend", directLink);
}


function createIcon(value, icon, tip) {
    const a = document.createElement("a");
    a.setAttribute("class", "card-cover-footer-stats__item");
    if (tip.indexOf ("\n") !== -1) {
        a.setAttribute("title", tip);
    } else {
        a.setAttribute("data-tip", tip);
    }
    a.setAttribute("currentitem", "false");

    const iconSpan = document.createElement("span")
    iconSpan.setAttribute("class", "card-cover-footer-stats__icon "+ icon);
    a.appendChild(iconSpan);

    if (value !== null) {
        const valueDiv = document.createElement("div");
        valueDiv.setAttribute("class", "card-cover-footer-stats__value");
        valueDiv.innerText = value;
        a.appendChild(valueDiv);
    }
    return a;
}

//const message = JSON.parse('{ "title":"заголовок", "text":"Текст сообщения", link":"Текст ссылки", "ссыллка"}');
function showAnnouncement(message) {
    const notifications = document.getElementsByClassName("notifications");
    if (notifications.length > 0) {
        const last = notifications.item(notifications.length - 1);
        const notification = creatNotification(notifications.length, message);
        last.insertAdjacentElement("afterend", notification);
    }
}

function creatNotification(num, message) {
    const notification = createElement("div", "notifications notifications_num_"+num);
    const link = createElement("a","notification-item");
    link.setAttribute("href", message.href);
    link.setAttribute("target","_blank");
    link.setAttribute("style", "");
    const container = createElement("div", "notification-item__container");
    const icon = createElement("div", "notification-item__icon");
    container.appendChild(icon);
    const title = createElement("span", "notification-item__title");
    title.innerText = message.title;
    container.appendChild(title);
    const text = createElement("span", "notification-item__text");
    text.innerText = message.text+" ";
    container.appendChild(text);

    const linkStr = createElement("span","notification-item__link");
    linkStr.innerText = message.link;
    container.appendChild(linkStr);
    link.appendChild(container);
    notification.appendChild(link);
    return notification;
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

function secToHHMMSS(seconds) {
    let time = seconds;
    const hours = Math.floor(time / 3600);
    time = time % 3600;
    const min = ("0" + Math.floor(time / 60)).substr(-2);
    const sec = ("0" + Math.floor(time % 60)).substr(-2);

    if (isNaN(hours) || isNaN(min) || isNaN(sec)) return "0";
    return (hours > 0 ? hours +":" : "") + min +":"+sec;
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

function joinByThree(list) {
    let text = "";
    for (i = 0; i<list.length; i++) {
        if (i === 0) {
            text = list[i];
        } else if ( (i/3) === Math.floor(i/3)) {
            text = text + ",\n" + list[i];
        } else {
            text = text + ", " + list[i];
        }
    }
    return text;
}

function infiniteAndNan(number) {
    return isNaN (number) ? 0 : (isFinite(number) ? number : 0);
}

function firstNotZ(a, b, c) {
    if (a !== 0) {
        return a;
    }
    if (b !== 0) {
        return b;
    }
    return c;
}

function checkNoIndex(){
    const metas = document.getElementsByTagName('meta');
    for (let i = 0; i < metas.length; i++) {
        if (metas[i].getAttribute('name') === "robots") {
            return metas[i].getAttribute('content') === "noindex";
        }
    }
    return false;
}

function addRobotIconIfNoNoIndex(id) {
    const xhr = new XMLHttpRequest();
    xhr.onload = function() {
        const metas = xhr.responseXML.head.getElementsByTagName('meta');
        for (let i = 0; i < metas.length; i++) {
            if (metas[i].getAttribute('property') === "robots") {
                if (metas[i].getAttribute('content') === "none") {
                    addRobotIcon();
                }
                break;
            }
        }
    };
    xhr.open("GET", URL_ZEN_ID+id );
    xhr.responseType = "document";
    xhr.send();
}

function addRobotIcon() {
    const sadRobotIcon = createElement("span","header__readers-count");
    const img = createElement("img");
    img.setAttribute("src", "data:image/svg+xml,%3C%3Fxml version='1.0' encoding='UTF-8' standalone='no'%3F%3E%3Csvg width='20px' height='20px' viewBox='0 0 20 20' version='1.1' xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink'%3E%3Ctitle%3Esad%3C/title%3E%3Cdefs%3E%3C/defs%3E%3Cg id='Page-1' stroke='none' stroke-width='1' fill='none' fill-rule='evenodd'%3E%3Cg id='Dribbble-Light-Preview' transform='translate(-100.000000, -6239.000000)' fill='%23ea7272'%3E%3Cg id='icons' transform='translate(56.000000, 160.000000)'%3E%3Cpath d='M56,6086 C56,6085.448 56.448,6085 57,6085 L59,6085 C59.552,6085 60,6085.448 60,6086 L60,6088 C60,6088.55 59.55,6089 59,6089 L57,6089 C56.45,6089 56,6088.55 56,6088 L56,6086 Z M52,6088 C52,6088.552 51.552,6089 51,6089 L49,6089 C48.448,6089 48,6088.552 48,6088 L48,6086 C48,6085.448 48.448,6085 49,6085 L51,6085 C51.552,6085 52,6085.448 52,6086 L52,6088 Z M60,6092.689 L60,6093.585 C60,6094.137 59.552,6094.611 59,6094.611 L59,6094.637 C58.448,6094.637 58,6094.241 58,6093.689 L58,6093.792 C58,6093.24 57.552,6093 57,6093 L51,6093 C50.448,6093 50,6093.24 50,6093.792 L50,6093.585 C50,6094.137 49.552,6094.585 49,6094.585 C48.448,6094.585 48,6094.137 48,6093.585 L48,6092.689 C48,6091.584 48.896,6090.997 50,6090.997 L50,6091 L58,6091 C59,6091 60,6091.584 60,6092.689 L60,6092.689 Z M62,6096 C62,6096.552 61.552,6097 61,6097 L47,6097 C46.448,6097 46,6096.552 46,6096 L46,6082 C46,6081.448 46.448,6081 47,6081 L61,6081 C61.552,6081 62,6081.448 62,6082 L62,6096 Z M64,6081 C64,6079.895 63.105,6079 62,6079 L46,6079 C44.895,6079 44,6079.895 44,6081 L44,6097 C44,6098.105 44.895,6099 46,6099 L62,6099 C63.105,6099 64,6098.105 64,6097 L64,6081 Z' id='sad'%3E%3C/path%3E%3C/g%3E%3C/g%3E%3C/g%3E%3C/svg%3E");
    img.setAttribute("title", "Обнаружен мета-тег <meta property=\"robots\" content=\"none\" />\n" +
        "Канал не индексируется поисковиками.\n" +
        "Примечание: связь этого тега с показами,\n" +
        "пессимизацией и иными ограничениями канала\n" +
        "официально не подтверждена.");
    sadRobotIcon.appendChild(img);
    const navblocks = document.getElementsByClassName("header__readers-count");
    const last = navblocks.item(navblocks.length - 1);
    last.insertAdjacentElement("afterend", sadRobotIcon);
}

function createElement(elementType, elementClass, childElement) {
    const newElement = document.createElement(elementType);
    if (elementClass !== undefined) {
        newElement.setAttribute("class", elementClass);
    }
    if (childElement !== undefined) {
        newElement.appendChild(childElement);
    }
    return newElement;
}

function infiniteAndNanToStr(num, digits) {
    return infiniteAndNan(num).toLocaleString(undefined, {maximumFractionDigits: digits === undefined ? 0 : digits})
}

function addSearchInput() {
    const input = createElement("input", "zen-ui-input__control");
    input.setAttribute("type", "text");
    input.setAttribute("id", "search");
    input.setAttribute("placeholder", "строка поиска");
    const divInputContainer = createElement("div","zen-ui-input__control-container", input);
    const divUiBox = createElement("div","zen-ui-input__box");
    divInputContainer.appendChild(divUiBox);
    const divUiInputControl = createElement("div","zen-ui-input _size_m", divInputContainer);
    const divUiSelect = createElement("div", "zen-ui-select _size_m _type_input publications-groups-view__content-type-filter-control", divUiInputControl);
    const boxDiv = document.getElementsByClassName("publications-groups-view__content-type-filter")[0];
    const span = createElement("span");
    span.innerText = "|";
    span.setAttribute("style", "margin-left: 5px; margin-right: 5px; color:silver;");
    const button = createElement("button" );
    button.innerText = "🔎";
    button.setAttribute("class","prozen_button");
    boxDiv.insertAdjacentElement("afterend", span);
    span.insertAdjacentElement("afterend", divUiSelect);
    divUiSelect.insertAdjacentElement("afterend", button);
    button.setAttribute("data-tip",  "Поиск (откроется новое окно)");
    button.addEventListener('click', clickFind);
}

function clickFind() {
    clickSearchButton (document.getElementById("search").value);
}