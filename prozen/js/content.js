start();
const URL_API_PUBLICATIONS = "https://zen.yandex.ru/media-api/publisher-publications-stat?publicationsIds=";
const URL_API_PUBLICATIONS_PUBLISHED = "https://zen.yandex.ru/media-api/get-publications-by-state?state=published&pageSize=%pageSize%&publisherId=%publisherId%";
const URL_API_COUNT_PUBLISHED = "https://zen.yandex.ru/media-api/count-publications-by-state?state=published&publisherId=";
const URL_ZEN_ID = "https://zen.yandex.ru/id/";
const URL_API_MEDIA = "https://zen.yandex.ru/media-api/id/";
const URL_API_EDITOR = "https://zen.yandex.ru/editor-api/v2/publisher/";
const URL_API_GET_PUBLICATION = "https://zen.yandex.ru/media-api/get-publication?publicationId=";
const URL_API_PUBLICATION_VIEW_STAT = "https://zen.yandex.ru/media-api/publication-view-stat?publicationId=";
const COUNT_PUBLICATIONS_API_URL = "https://zen.yandex.ru/media-api/count-publications-by-state?state=published&type=";
const GET_PUBLICATIONS_API_URL = "https://zen.yandex.ru/media-api/get-publications-by-state?state=published&pageSize=";

const publications = new Map();
let observer;
const observers = [];
let token;
let data;
let publisherId;
let mediaUrl;


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
    script.setAttribute("type", "text/javascript");
    script.setAttribute("src", browser.extension.getURL("js/page.js"));
    document.body.appendChild(script);

    window.addEventListener("message", function (event) {
        if (event.source !== window)
            return;
        if (event.data.type && (event.data.type === "prozen-data")) {
            token = event.data.text;
            data = event.data.jsonData;
            publisherId = event.data.jsonData.userPublisher.id;
            main();
        }
    });
}

function main() {
    const pageType = getPageType();
    if (pageType === "unknown") {
        return;
    }
    if (pageType === "article") {
        setTimeout(articleShowStats, 300);
        return;
    }

    if (pageType === "narrative") {
        setTimeout(articleShowStatsNarrative, 300);
        return;
    }

    if (pageType === "video") {
        setTimeout(articleShowStatsVideo, 300);
        return;
    }

    publisherId = getPublisherId();
    if (token === undefined || publisherId === undefined) {
        return;
    }
    if (pageType !== "edit") {
        setTimeout(addNotificationCloseButton, 50);
    }
    if (pageType === "main") {
        mediaUrl = window.location.href.replace("profile/editor", "media");
        registerTargetObserver();
        registerContentObserver();
    }
}

function registerFeedObservers() {

}

function registerContentObserver() {
    const target = document.getElementsByClassName("content")[0];
    if (!target) {
        setTimeout(registerContentObserver, 50);
        return;
    }

    if (document.getElementsByClassName("publications-root")) {
        setUnprocessedPublications();
        loadCardsAll();
        processCards();
        registerCardObservers();

        addSearchInput();
        setTimeout (showBalanceAndMetrics, 100);
        addRobotIconIfNoNoIndex(publisherId);
    }
    const contentObserver = new MutationObserver(function (mutations) {
        mutations.forEach(mutation => {
            if (mutation.addedNodes && mutation.addedNodes.length > 0) {
                mutation.addedNodes.forEach(e => {
                    if (e.hasAttribute("class") && e.getAttribute("class") === "publications-root") {
                        setUnprocessedPublications();
                        loadCardsAll();
                        processCards();
                        registerCardObservers();
                        addSearchInput();
                        setTimeout (showBalanceAndMetrics, 150);
                    }
                });
            }
        });
    });
    contentObserver.observe(target, {childList: true});
}

function registerTargetObserver() {
    const target = document.getElementsByClassName("publications-groups-view")[0];
    if (!target) {
        setTimeout(registerTargetObserver, 50);
        return;
    }
    if (observer !== undefined) {
        observer.disconnect();
    }
    observer = new MutationObserver(function (mutations) {
        mutations.forEach(function (mutation) {
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
        const observer = new MutationObserver(function (mutations) {
            mutations.forEach(function (mutation) {
                if (mutation.type === 'childList') {
                    if (mutation.addedNodes !== undefined && mutation.addedNodes.length > 0) {
                        mutation.addedNodes.forEach(function (node) {
                            const ids = loadCards(node);
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

async function articleShowStatsNarrative() {
    if (data == null) {
        return;
    }
    const postId = getPostIdFromUrl(window.location.pathname);
    const dayMod = dateFormat(data.publication.content.modTime);
    const dayCreate = data.publication.addTime === undefined ? dayMod : dateFormat(data.publication.addTime);
    const showTime = dayMod !== dayCreate ? dayCreate + " (" + dayMod + ")" : dayCreate;
    const articleData = await loadPublicationStat(postId);
    const sumViewTimeSec = articleData.sumViewTimeSec;
    const views = articleData.views;
    const viewsTillEnd = articleData.viewsTillEnd;
    const timeRead = "Время дочитывания: " + secToText(infiniteAndNan(sumViewTimeSec / viewsTillEnd));


    const elArticleDates = document.getElementsByClassName("article-stat__date");
    for (let i = 0; i < elArticleDates.length; i++) {
        const elArticleDate = document.getElementsByClassName("article-stat__date")[i];
        elArticleDate.innerText = showTime;
    }

    const divsStat = document.getElementsByClassName("article-stat__info");
    const divList = [];
    for (let i = 0; i < divsStat.length; i++) {
        divList.push(divsStat[i]);
    }

    for (let i = 0; i < divList.length; i++) {
        const divStat = divList.pop();
        let spanViewsTillEnd = divStat.querySelector(".article-stat__counts-wrapper > .article-stat__count");
        if (spanViewsTillEnd === undefined || spanViewsTillEnd === null) {
            spanViewsTillEnd = createElement("span", "article-stat__count")
            const divArticleStatCountsWrapper = createElement("div", "article-stat__counts-wrapper", spanViewsTillEnd);
            const divArticleStatDateContainer = document.getElementsByClassName("article-stat__date-container")[i];
            divArticleStatDateContainer.insertAdjacentElement("afterend", divArticleStatCountsWrapper);
        }
        spanViewsTillEnd.innerText = paucal(viewsTillEnd, "дочитывание", "дочитывания", "дочитываний") + " (" + infiniteAndNan(viewsTillEnd / views * 100).toFixed(2) + "%)";
        const spanViews = createElement("span", "article-stat__count");
        spanViews.innerText = paucal(views, "просмотр", "просмотра", "просмотров");
        const divViewsWrapper = createElement("div", "article-stat__counts-wrapper", spanViews);
        const divViews = createElement("div", "article-stat__info article-stat__info_loaded", divViewsWrapper);
        divStat.insertAdjacentElement("beforebegin", divViews);
        const spanTimeReads = createElement("span", "article-stat__count");
        spanTimeReads.innerText = timeRead;
        const divTimeReadsWrapper = createElement("div", "article-stat__counts-wrapper", spanTimeReads);
        const divReads = createElement("div", "article-stat__info article-stat__info_loaded", divTimeReadsWrapper);
        divStat.insertAdjacentElement("afterend", divReads);
        if (checkNoIndex()) {
            const spanIcon5 = createElement("span", "article-stat__icon icon_sad_robot");
            spanIcon5.setAttribute("style", "background-color: #FFFFFF80;");
            const wrapper5 = createElement("div", "article-stat__counts-wrapper", spanIcon5);
            wrapper5.setAttribute("title", "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
                "Публикация не индексируется поисковиками.\n" +
                "Примечание: связь этого тега с показами,\n" +
                "пессимизацией и иными ограничениями канала\n" +
                "официально не подтверждена.");
            divReads.insertAdjacentElement("afterend", wrapper5);
        }
    }
}

async function articleShowStatsVideo() {
    if (data === null) {
        return;
    }
    const postId = getPostIdFromUrl(window.location.pathname);
    const dayMod = dateFormat(data.publication.content.modTime);
    const dayCreate = data.publication.addTime === undefined ? dayMod : dateFormat(data.publication.addTime);
    const showTime = dayMod !== dayCreate ? dayCreate + " (" + dayMod + ")" : dayCreate;
    const articleData = await loadPublicationStat(postId);

    const sumViewTimeSec = articleData.sumViewTimeSec;
    const views = articleData.views;
    const shows = articleData.shows;
    const viewsTillEnd = articleData.viewsTillEnd;

    const elArticleDate = document.getElementsByClassName("article__date-video")[0];
    elArticleDate.innerText = showTime;

    const container = document.getElementsByClassName("article__about")[0];
    {
        // Просмотры
        const spanIcon1 = createElement("span", "article__date-video article-stat__icon article-stat__icon_type_book-black");
        /* container.appendChild(spanIcon1);
        spanIcon1.setAttribute("style", "background-color: #FFFFFF80;"); */
        const spanCount1 = createElement("span", "article__date-video");
        spanCount1.innerText = "📺 " + views.toLocaleString(undefined, {maximumFractionDigits: 0});
        spanCount1.setAttribute("title", "Просмотры");
        container.appendChild(spanCount1);
    }
    /*
    {
        // Просмотры (досмотры)?
        const spanIcon2 = createElement("span", "article-stat__icon article-stat__icon_type_perusal-black");
        container.appendChild(spanIcon2);
        const spanCount2 = createElement("span", "article__date-video");
        spanCount2.innerText = viewsTillEnd.toLocaleString(undefined, {maximumFractionDigits: 0}) + " (" + infiniteAndNan(viewsTillEnd / views * 100).toFixed(2) + "%)";
        container.appendChild(spanCount2);
    }
    */
    {
        // Среднее время просмотра
        /* const spanIcon3 = createElement("span", "article-stat__icon article-stat__icon_type_time-black");
        container.appendChild(spanIcon3);*/
        const spanCount3 = createElement("span", "article__date-video");
        spanCount3.innerText = "⌚ " + secToText(infiniteAndNan(sumViewTimeSec / viewsTillEnd));
        spanCount3.setAttribute("title", "Среднее время просмотра");
        container.appendChild(spanCount3);
    }

    {
        const url = window.location.href.split("\?")[0];
        const shortUrl = url.substr(0, url.lastIndexOf("/")) + "/" + url.substr(url.lastIndexOf("-") + 1, url.length - 1);
        const spanIcon4 = createElement("span", "article__date-video");
        spanIcon4.innerText = "🔗";
        spanIcon4.setAttribute("title", "Сокращённая ссылка на статью.\nКликните, чтобы скопировать её в буфер обмена.");
        spanIcon4.addEventListener('click', copyTextToClipboard.bind(null, shortUrl));
        spanIcon4.style.cursor = "pointer";
        container.appendChild(spanIcon4);
    }

    {
        if (checkNoIndex()) {
            const spanIcon5 = createElement("span", "article__date-video");
            spanIcon5.innerText = "🤖";
            spanIcon5.setAttribute("title", "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
                "Публикация не индексируется поисковиками.\n" +
                "Примечание: связь этого тега с показами,\n" +
                "пессимизацией и иными ограничениями канала\n" +
                "официально не подтверждена.");
        }
    }
}

async function articleShowStats() {
    if (data === null) {
        return;
    }
    const postId = getPostIdFromUrl(window.location.pathname);
    const dayMod = dateFormat(data.publication.content.modTime);
    const dayCreate = data.publication.addTime === undefined ? dayMod : dateFormat(data.publication.addTime);
    const showTime = dayMod !== dayCreate ? dayCreate + " (" + dayMod + ")" : dayCreate;
    const articleData = await loadPublicationStat(postId);
    const sumViewTimeSec = articleData.sumViewTimeSec;
    const views = articleData.views;
    const shows = articleData.shows;
    const viewsTillEnd = articleData.viewsTillEnd;

    const elArticleDate = document.getElementsByClassName("article-stat__date")[0];
    elArticleDate.innerText = showTime;
    let counters = document.getElementsByClassName("article-stat__count");
    if (counters.length === 0) {
        const container = document.getElementsByClassName("article-stat__date-container")[0];

        const wrapper1 = createElement("div", "article-stat__counts-wrapper");
        const spanIcon1 = createElement("span", "article-stat__icon article-stat__icon_type_book-black");
        wrapper1.appendChild(spanIcon1);
        const spanCount1 = createElement("span", "article-stat__count");
        wrapper1.appendChild(spanCount1);
        container.appendChild(wrapper1);
        counters = document.getElementsByClassName("article-stat__count");
    }
    counters[0].innerText = views.toLocaleString(undefined, {maximumFractionDigits: 0});

    if (counters.length === 1) {
        const wrapper1 = document.getElementsByClassName("article-stat__counts-wrapper")[0];
        const wrapper2 = createElement("div", "article-stat__counts-wrapper");
        const spanIcon2 = createElement("span", "article-stat__icon article-stat__icon_type_perusal-black");
        const spanCount2 = createElement("span", "article-stat__count");
        wrapper2.appendChild(spanIcon2);
        wrapper2.appendChild(spanCount2);
        wrapper1.insertAdjacentElement("afterend", wrapper2);
        counters = document.getElementsByClassName("article-stat__count");
    }
    if (counters.length === 2) {
        const wrapper2 = document.getElementsByClassName("article-stat__counts-wrapper")[1];
        const wrapper3 = createElement("div", "article-stat__counts-wrapper");
        const spanIcon3 = createElement("span", "article-stat__icon article-stat__icon_type_time-black");
        const spanCount3 = createElement("span", "article-stat__count");
        wrapper3.appendChild(spanIcon3);
        wrapper3.appendChild(spanCount3);
        wrapper2.insertAdjacentElement("afterend", wrapper3);
        counters = document.getElementsByClassName("article-stat__count");
    }
    counters[1].innerText = viewsTillEnd.toLocaleString(undefined, {maximumFractionDigits: 0}) + " (" + infiniteAndNan(viewsTillEnd / views * 100).toFixed(2) + "%)";
    counters[2].innerText = secToText(infiniteAndNan(sumViewTimeSec / viewsTillEnd));

    const url = window.location.href.split("\?")[0];
    const shortUrl = url.substr(0, url.lastIndexOf("/")) + "/" + url.substr(url.lastIndexOf("-") + 1, url.length - 1);

    const spanIcon4 = createElement("span", "article-stat__icon icon_url");

    const wrapper4 = createElement("div", "article-stat__counts-wrapper", spanIcon4);
    wrapper4.setAttribute("title", "Сокращённая ссылка на статью.\nКликните, чтобы скопировать её в буфер обмена.");
    wrapper4.addEventListener('click', copyTextToClipboard.bind(null, shortUrl));
    wrapper4.style.cursor = "pointer";

    const wrapper3 = document.getElementsByClassName("article-stat__counts-wrapper")[2];
    wrapper3.insertAdjacentElement("afterend", wrapper4);


    removeByClass("article-stat-tip");
    if (checkNoIndex()) {
        const spanIcon5 = createElement("span", "article-stat__icon icon_sad_robot");
        spanIcon5.setAttribute("style", "background-color: #FFFFFF80;");
        const wrapper5 = createElement("div", "article-stat__counts-wrapper", spanIcon5);
        wrapper5.setAttribute("title", "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
            "Публикация не индексируется поисковиками.\n" +
            "Примечание: связь этого тега с показами,\n" +
            "пессимизацией и иными ограничениями канала\n" +
            "официально не подтверждена.");
        const wrapper4 = document.getElementsByClassName("article-stat__counts-wrapper")[3];
        wrapper4.insertAdjacentElement("afterend", wrapper5);
    }

}

function getPostIdFromUrl(url) {
    const ln = url.replace("?from=editor", "").split("-");
    return ln[ln.length - 1];
}

function getPublisherId() {
    const path = window.location.pathname;
    switch (getPageType()) {
        case "main":
            return data.userPublisher.id;
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
        if (data != null) {
            if (data.isArticle === true) {
                return "article";
            }

            if (data.isNarrative === true) {
                return "narrative";
            }

            if (data.isGif === true) {
                return "video";
            }
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
        return "main";
    }
    return "unknown";
}

function showBalanceAndMetrics() {
    const url = URL_API_MEDIA + publisherId + "/money";
    const data = fetch(url, {
        credentials: 'same-origin',
        headers: {'X-Csrf-Token': token}
    }).then(response => response.json());
    data.then(response => {
        if (response.money.isMonetezationAvaliable) {
            const simpleBalance = response.money.simple.balance;
            const personalDataBalance = response.money.simple.personalData.balance;
            const money = parseFloat((simpleBalance > personalDataBalance ? simpleBalance : personalDataBalance).toFixed(2));
            let total = money;
            for (let i = 0, len = response.money.simple.paymentHistory.length; i < len; i++) {
                if (response.money.simple.paymentHistory[i]["status"] === "completed") {
                    total += parseFloat(response.money.simple.paymentHistory[i]["amount"]);
                }
            }
            setBalance(money, total);
        }
        setTimeout(addProzenMenu.bind(null, response.publisher.privateData.metrikaCounterId), 1000);
    });
}

function addProzenMenu(metricsId) {
    if (!document.getElementById("prozen-menu")) {
        const divProzenMenu = createElement("div", "monetization-block");
        divProzenMenu.setAttribute("id", "prozen-menu");

        const aProzenMenuTitle = createElement("a", "monetization-block__title");
        aProzenMenuTitle.innerText = "Дополнительные возможности";
        aProzenMenuTitle.setAttribute("data-tip", "Добавлены расширением ПРОДЗЕН");
        divProzenMenu.appendChild(aProzenMenuTitle);

        const spanEmpty = createElement("span", "karma-block__karma-stats-label");
        spanEmpty.innerText = " ";
        divProzenMenu.appendChild(spanEmpty);

        const aTotalStats = createElement("a", "karma-block__link"); //ui-lib-header-item
        aTotalStats.innerText = "Полная статистика";
        aTotalStats.addEventListener('click', clickTotalStatsButton);
        aTotalStats.style.cursor = "pointer";
        divProzenMenu.appendChild(aTotalStats);

        const aMetrics = createElement("a", "karma-block__link");
        const metricsUrl = metricsId !== undefined ? "https://metrika.yandex.ru/dashboard?id=" + metricsId : "https://metrika.yandex.ru/list";
        aMetrics.innerText = "Метрика";
        aMetrics.setAttribute("href", metricsUrl);
        aMetrics.setAttribute("target", "_blank");
        divProzenMenu.appendChild(aMetrics);

        const aSearch = createElement("a", "karma-block__link");
        aSearch.innerText = "Поиск";
        aSearch.addEventListener('click', clickSearchButton);
        aSearch.style.cursor = "pointer";
        divProzenMenu.appendChild(aSearch);

        const aSadRobot = createElement("a", "karma-block__link");
        aSadRobot.innerText = "Неиндексируемые";
        aSadRobot.setAttribute("data-tip", "Поиск публикаций с мета-тегом robots");
        aSadRobot.addEventListener('click', clickFindSadRobots);
        aSadRobot.style.cursor = "pointer";
        divProzenMenu.appendChild(aSadRobot);
        const spanEmpty2 = createElement("span", "karma-block__karma-stats-label");
        spanEmpty2.innerText = " ";
        divProzenMenu.appendChild(spanEmpty2);

        const spanProzen = createElement("span", "karma-block__karma-stats-label");
        spanProzen.innerText = "Добавлено расширением ПРОДЗЕН";
        divProzenMenu.appendChild(spanProzen);

        const divProfileSidebar = document.getElementsByClassName("profile-sidebar")[0];
        divProfileSidebar.appendChild(divProzenMenu);
    }
}

function setBalance(money, total) {
    const moneySpan = document.getElementsByClassName("monetization-block__money-balance")[0];
    if (!moneySpan) {
        setTimeout(setBalance.bind(null, money, total), 50);
        return;
    }
    if (money !== total) {
        const totalStr = "Всего: " + total.toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }) + " ₽";
        const moneyDate = moneySpan.getAttribute("data-tip");
        if (moneyDate !== undefined && moneyDate !== null) {
            moneySpan.setAttribute("data-tip", moneyDate + " / " + totalStr);
        } else {
            moneySpan.setAttribute("data-tip", totalStr);
        }
    }
    moneySpan.innerText = money.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2}) + " ₽";
}


function clickSearchButton(searchString) {
    let id;
    const textToFind = searchString === undefined ? "" : searchString;
    if (data.publisher.nickname === undefined) {
        id = "channel_id=" + publisherId;
    } else {
        id = "channel_name=" + data.publisher.nickname.raw;
    }
    chrome.storage.local.set({prozenId: id, prozenSearch: textToFind}, function () {
        window.open(browser.extension.getURL("search.html"));
    });
}

function clickFindSadRobots() {
    let id;
    if (data.publisher.nickname === undefined) {
        id = "channel_id=" + publisherId;
    } else {
        id = "channel_name=" + data.publisher.nickname.raw;
    }
    chrome.storage.local.set({prozenId: id}, function () {
        window.open(chrome.extension.getURL("sadrobot.html"));
    });
}

function clickTotalStatsButton() {
    chrome.storage.local.set({prozenToken: token, prozenPublisherId: publisherId}, function () {
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
    const url = URL_API_PUBLICATIONS + encodeURIComponent(publicationIds.join(",")) + "&publisherId=" + publisherId;
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}

function loadPublicationsPublisher(publisherId) {
    const countUrl = URL_API_COUNT_PUBLISHED + publisherId;
    return fetch(countUrl, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}})
        .then(response => response.json())
        .then(data => {
            const pageSize = data.count;
            const url = URL_API_PUBLICATIONS_PUBLISHED.replace("%pageSize%", pageSize).replace("%publisherId%", publisherId);
            return fetch(url, {
                credentials: 'same-origin',
                headers: {'X-Csrf-Token': token}
            }).then(response => response.json());
        });
}

function loadPublicationStat(publicationId) {
    const url = URL_API_PUBLICATION_VIEW_STAT + encodeURIComponent(publicationId);
    return fetch(url, {credentials: 'same-origin'}).then(response => response.json());
}

function loadArticle(publicationId) {
    const url = URL_API_GET_PUBLICATION + publicationId;
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}

function processCards(loadedIds) {
    const ids = loadedIds === undefined ? getUnproccedPublications() : loadedIds;
    if (ids.length === 0) {
        return;
    }
    const idsToLoad = [];
    ids.forEach(function (id) {
        if (!publications.get(id).processed) {
            idsToLoad.push(id);
        }
    });
    if (idsToLoad.length > 0) {
        loadPublicationsStat(idsToLoad).then(function (data) {
            const articles = [];
            for (let i in data.items) {
                const stat = data.items[i];
                const id = stat.publicationId;
                const card = publications.get(id);
                card.comments = stat.comments;
                card.feedShows = stat.feedShows;
                card.likes = stat.likes;
                card.views = stat.views;
                card.shows = stat.shows;
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
                    card.tags = article.publications[0].tags;
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


function processCardsViews(ids) {
    for (let i = 0; i < ids.length; i++) {
        const publicationId = ids[i];
        const value = publications.get(publicationId);
        if (value.card.hasChildNodes()) {
            setPublicationTime(value);
            modifyCardFooter(value, publicationId);
        }
        value.processed = true;
    }
}

function setPublicationTime(pubData) {
    const dateDiv = pubData.card.getElementsByClassName("card-cover-publication__status")[0];
    if (dateDiv.innerText.match("(^Вчера)|(^Сегодня)|(^Три дня назад)|(^\\d{1,2}\\s([а-я]+)(\\s201\\d)?)")) {
        const dayMod = dateFormat(pubData.modTime);
        const dayCreate = pubData.addTime === undefined ? dayMod : dateFormat(pubData.addTime);
        dateDiv.innerText = dayCreate + (dayCreate === dayMod ? "" : " (" + dayMod + ")");
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


function modifyCardFooter(pubData, publicationId) {
    const cardFooters = pubData.card.getElementsByClassName("card-cover-publication__stats-container");
    if (cardFooters === undefined || cardFooters.length === 0) {
        return;
    }
    const cardFooter = cardFooters[0];
    removeChilds(cardFooter);
    const elementShows = createIcon(infiniteAndNanToStr(pubData.feedShows), "icon_shows_in_feed", "Показы");

    const erViews = firstNotZ(pubData.viewsTillEnd, pubData.views, pubData.feedShows);
    const likesEr = infiniteAndNan((pubData.likes / erViews) * 100);
    const likesValue = pubData.likes === 0 ? "0 (0.00%)" : infiniteAndNanToStr(pubData.likes) + " (" + parseFloat(likesEr).toFixed(2) + "%)";
    const elementLikes = createIcon(likesValue, "icon_like", "Лайки");

    const line1 = createFooterLine(elementShows, elementLikes);
    cardFooter.appendChild(line1);

    const ctr = (parseFloat(infiniteAndNan(pubData.shows / pubData.feedShows) * 100)).toFixed(2);

    const elementViews = createIcon(infiniteAndNanToStr(pubData.views) + " (" + ctr + "%)", "icon_views", "Просмотры (CTR)");
    const readsPercent = ((pubData.viewsTillEnd / pubData.views) * 100).toFixed(2);

    const commentsEr = infiniteAndNan((pubData.comments / erViews) * 100);
    const commentsValue = pubData.comments === 0 ? "0 (0.00%)" : infiniteAndNanToStr(pubData.comments) + " (" + parseFloat(commentsEr).toFixed(2) + "%)";
    const elementComments = createIcon(commentsValue, "icon_comments", "Комментарии");
    const line2 = createFooterLine(elementViews, elementComments);
    cardFooter.appendChild(line2);

    const elementViewsTillEnd = createIcon(infiniteAndNanToStr(pubData.viewsTillEnd) + " (" + parseFloat(infiniteAndNan(readsPercent)).toFixed(2) + "%)",
        "icon_views_till_end", "Дочитывания");
    const erValue = infiniteAndNan((((pubData.comments + pubData.likes) / erViews)) * 100).toFixed(2) + "%";
    const elementEr = createIcon(erValue, "icon_er", "Коэффициент вовлеченности, ER");
    const line3 = createFooterLine(elementViewsTillEnd, elementEr);
    cardFooter.appendChild(line3);

    const readTimeCount = secToHHMMSS(pubData.readTime);
    const readTimeTitle = "Время дочитывания" + (pubData.readTime > 0 ? " - " + secToText(pubData.readTime) : "");
    const elementReadTime = createIcon(readTimeCount, "icon_clock", readTimeTitle);

    const elementTags = createIconsTagLink(pubData.tags, mediaUrl + "/" + publicationId);

    const line4 = createFooterLine(elementReadTime, elementTags);
    cardFooter.appendChild(line4);
}

function removeChilds(element) {
    while (element.firstChild) {
        element.removeChild(element.firstChild);
    }
}

function removeByClass(className) {
    const elements = document.getElementsByClassName(className);
    while (elements.length > 0) {
        elements[0].parentNode.removeChild(elements[0]);
    }
}

function addDirectLinkButton(link) {
    const linkUrl = link.getAttribute("href").replace("?from=editor", "");
    const directLink = createElement("a", "action-menu__action-button");
    directLink.setAttribute("href", linkUrl);
    directLink.innerText = "Прямая ссылка";
    link.insertAdjacentElement("afterend", directLink);
}

function createIcon(value, icon, tip) {
    const a = document.createElement("a");
    a.setAttribute("class", "card-cover-footer-stats__item");
    if (tip.indexOf("\n") !== -1) {
        a.setAttribute("title", tip);
    } else {
        a.setAttribute("data-tip", tip);
    }
    a.setAttribute("currentitem", "false");

    const iconSpan = document.createElement("span");
    iconSpan.setAttribute("class", "card-cover-footer-stats__icon " + icon);
    a.appendChild(iconSpan);

    if (value !== null) {
        const valueDiv = document.createElement("div");
        valueDiv.setAttribute("class", "card-cover-footer-stats__value");
        valueDiv.innerText = value;
        a.appendChild(valueDiv);
    }
    return a;
}

function getTagsTitles(tagObjects) {
    const tagTitles = [];
    if (tagObjects !== undefined && tagObjects.length > 0) {
        for (i = 0; i < tagObjects.length; i++) {
            tagTitles.push(tagObjects[i].title);
        }
    }
    return tagTitles;
}

function createIconsTagLink(tags, url) {
    const a = document.createElement("a");
    a.setAttribute("class", "card-cover-footer-stats__item");
    const iconSpan1 = document.createElement("span");
    iconSpan1.setAttribute("class", "card-cover-footer-stats__icon icon_tags");

    const textTags = getTagsTitles(tags);

    const tagTip = tags.length === 0 ? "Теги не указаны" : "Теги: " + joinByThree(textTags);
    if (tagTip.indexOf("\n") !== -1) {
        iconSpan1.setAttribute("title", tagTip);
    } else {
        iconSpan1.setAttribute("data-tip", tagTip);
    }
    if (tags.length !== 0) {
        iconSpan1.addEventListener('click', copyTextToClipboard.bind(null, textTags));
    }
    iconSpan1.style.cursor = "pointer";

    const iconSpan2 = document.createElement("span");
    iconSpan2.setAttribute("class", "icon_short_url");
    iconSpan2.setAttribute("data-tip", "Скопировать короткую ссылку");
    iconSpan2.style.cursor = "pointer";
    iconSpan2.addEventListener('click', copyTextToClipboard.bind(null, url));
    a.appendChild(iconSpan2);
    if (tags.length !== 0) {
        a.appendChild(iconSpan1);
    }
    return a;
}


function creatNotification(num, message) {
    const notification = createElement("div", "notifications notifications_num_" + num);
    const link = createElement("a", "notification-item");
    link.setAttribute("href", message.href);
    link.setAttribute("target", "_blank");
    link.setAttribute("style", "");
    const container = createElement("div", "notification-item__container");
    const icon = createElement("div", "notification-item__icon");
    container.appendChild(icon);
    const title = createElement("span", "notification-item__title");
    title.innerText = message.title;
    container.appendChild(title);
    const text = createElement("span", "notification-item__text");
    text.innerText = message.text + " ";
    container.appendChild(text);

    const linkStr = createElement("span", "notification-item__link");
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
    const year = "" + date.getFullYear();
    const hours = "0" + date.getHours();
    const minutes = "0" + date.getMinutes();
    return day.substr(-2) + "." + month.substr(-2) + "."
        + year.substr(-2) + "\u00A0" + hours.substr(-2) + ":" + minutes.substr(-2);
}

function secToHHMMSS(seconds) {
    let time = seconds;
    const hours = Math.floor(time / 3600);
    time = time % 3600;
    const min = ("0" + Math.floor(time / 60)).substr(-2);
    const sec = ("0" + Math.floor(time % 60)).substr(-2);

    if (isNaN(hours) || isNaN(min) || isNaN(sec)) return "0";
    return (hours > 0 ? hours + ":" : "") + min + ":" + sec;
}

function secToText(seconds) {
    let time = seconds;
    const hours = Math.floor(time / 3600);
    time = time % 3600;
    const min = Math.floor(time / 60);
    const sec = Math.floor(time % 60);
    if (isNaN(hours) || isNaN(min) || isNaN(sec)) return "не определено";
    return (hours > 0 ? hours + " час " : "") + (min > 0 ? min + " мин " : "") + sec + " сек";
}

function joinByThree(list) {
    let text = "";
    for (i = 0; i < list.length; i++) {
        if (i === 0) {
            text = list[i];
        } else if ((i / 3) === Math.floor(i / 3)) {
            text = text + ",\n" + list[i];
        } else {
            text = text + ", " + list[i];
        }
    }
    return text;
}

function infiniteAndNan(number) {
    return isNaN(number) ? 0 : (isFinite(number) ? number : 0);
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

function checkNoIndex() {
    const metas = document.getElementsByTagName('meta');
    for (let i = 0; i < metas.length; i++) {
        if (metas[i].getAttribute('name') === "robots") {
            return metas[i].getAttribute('content') === "noindex";
        }
    }
    return false;
}

function addRobotIconIfNoNoIndex(id) {
    if (!document.getElementById("prozen-robot-icon")) {
        const xhr = new XMLHttpRequest();
        xhr.onload = function () {
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
        xhr.open("GET", URL_ZEN_ID + id);
        xhr.responseType = "document";
        xhr.send();
    }
}

function addRobotIcon() {
    const sadRobotIcon = createElement("span", "header__readers-count");
    sadRobotIcon.setAttribute("id", "prozen-robot-icon");
    const img = createElement("img");
    img.setAttribute("src", "data:image/svg+xml,%3C%3Fxml version='1.0' encoding='UTF-8' standalone='no'%3F%3E%3Csvg width='20px' height='20px' viewBox='0 0 20 20' version='1.1' xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink'%3E%3Ctitle%3Esad%3C/title%3E%3Cdefs%3E%3C/defs%3E%3Cg id='Page-1' stroke='none' stroke-width='1' fill='none' fill-rule='evenodd'%3E%3Cg id='Dribbble-Light-Preview' transform='translate(-100.000000, -6239.000000)' fill='%23ea7272'%3E%3Cg id='icons' transform='translate(56.000000, 160.000000)'%3E%3Cpath d='M56,6086 C56,6085.448 56.448,6085 57,6085 L59,6085 C59.552,6085 60,6085.448 60,6086 L60,6088 C60,6088.55 59.55,6089 59,6089 L57,6089 C56.45,6089 56,6088.55 56,6088 L56,6086 Z M52,6088 C52,6088.552 51.552,6089 51,6089 L49,6089 C48.448,6089 48,6088.552 48,6088 L48,6086 C48,6085.448 48.448,6085 49,6085 L51,6085 C51.552,6085 52,6085.448 52,6086 L52,6088 Z M60,6092.689 L60,6093.585 C60,6094.137 59.552,6094.611 59,6094.611 L59,6094.637 C58.448,6094.637 58,6094.241 58,6093.689 L58,6093.792 C58,6093.24 57.552,6093 57,6093 L51,6093 C50.448,6093 50,6093.24 50,6093.792 L50,6093.585 C50,6094.137 49.552,6094.585 49,6094.585 C48.448,6094.585 48,6094.137 48,6093.585 L48,6092.689 C48,6091.584 48.896,6090.997 50,6090.997 L50,6091 L58,6091 C59,6091 60,6091.584 60,6092.689 L60,6092.689 Z M62,6096 C62,6096.552 61.552,6097 61,6097 L47,6097 C46.448,6097 46,6096.552 46,6096 L46,6082 C46,6081.448 46.448,6081 47,6081 L61,6081 C61.552,6081 62,6081.448 62,6082 L62,6096 Z M64,6081 C64,6079.895 63.105,6079 62,6079 L46,6079 C44.895,6079 44,6079.895 44,6081 L44,6097 C44,6098.105 44.895,6099 46,6099 L62,6099 C63.105,6099 64,6098.105 64,6097 L64,6081 Z' id='sad'%3E%3C/path%3E%3C/g%3E%3C/g%3E%3C/g%3E%3C/svg%3E");
    img.setAttribute("title", "Обнаружен мета-тег <meta property=\"robots\" content=\"none\" />\n" +
        "Канал не индексируется поисковиками.\n" +
        "Примечание: связь этого тега с показами,\n" +
        "пессимизацией и иными ограничениями канала\n" +
        "официально не подтверждена.");
    sadRobotIcon.appendChild(img);
    const navblocks = document.getElementsByClassName("ui-lib-header-item _type_left");
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
    if (document.getElementById("prozen-search")) {
        return;
    }

    const boxDiv = document.getElementsByClassName("publications-groups-view__content-type-filter")[0];
    if (!boxDiv) {
        setTimeout(addSearchInput, 50);
        return;
    }

    const input = createElement("input", "ui-lib-input__control");
    input.setAttribute("type", "text");
    input.setAttribute("id", "prozen-search");
    input.setAttribute("placeholder", "строка поиска");
    const divInputContainer = createElement("div", "ui-lib-input__control-container", input);
    const divUiBox = createElement("div", "ui-lib-input__box");
    divInputContainer.appendChild(divUiBox);
    const divUiInputControl = createElement("div", "ui-lib-input _size_m", divInputContainer);
    const divUiSelect = createElement("div", "ui-lib-select _size_m _type_input publications-groups-view__content-type-filter-control", divUiInputControl);
    divUiSelect.style.width = "165px";
    const span = createElement("span");
    span.innerText = "|";
    span.setAttribute("style", "margin-left: 5px; margin-right: 5px; color:silver;");
    const button = createElement("button");
    button.innerText = "🔎";
    button.setAttribute("class", "prozen_button");
    boxDiv.insertAdjacentElement("afterend", span);
    span.insertAdjacentElement("afterend", divUiSelect);
    divUiSelect.insertAdjacentElement("afterend", button);
    button.setAttribute("data-tip", "Поиск (откроется новое окно)");
    button.addEventListener('click', clickFind);
    input.addEventListener("keyup", event => {
        event.preventDefault();
        if (event.keyCode === 13) {
            button.click();
        }
    });
}

function clickFind() {
    clickSearchButton(document.getElementById("prozen-search").value);
    return false;
}

function showAnnouncement(message) {
    const notifications = document.getElementsByClassName("notifications");
    if (notifications.length > 0) {
        const last = notifications.item(notifications.length - 1);
        const notification = creatNotification(notifications.length, message);
        last.insertAdjacentElement("afterend", notification);
    }
}

function closeNotification(event) {
    const cross = event.target;
    const container = cross.parentElement;
    const notificationId = getNotificationId(container);
    container.parentElement.removeChild(container);
    setNotifictionHidden(notificationId);
    event.stopPropagation();
    event.preventDefault();
    return false;
}

function getNotificationId(notification) {
    const idParts = [];
    const titles = notification.querySelector(".notifications__item > .notifications__item-container > .notifications__item-link");
    if (titles !== undefined && titles !== null && titles.innerText) {
        idParts.push(titles.innerText);
    }
    const links = notification.querySelector(".notifications__item > .notifications__item-container > .notifications__item-title");
    if (links !== undefined && links !== null && links.innerText) {
        idParts.push(links.innerText);
    }
    const texts = notification.querySelector(".notifications__item > .notifications__item-container > .notifications__item-text");
    if (texts !== undefined && texts !== null && texts.innerText) {
        idParts.push(texts.innerText);
    }
    if (idParts.length === 0) {
        return "";
    }
    return idParts.join("_");
}

async function addNotificationCloseButton() {
    const notifications = document.getElementsByClassName("notifications");
    if (notifications && notifications.length > 0) {
        for (let i = 0; i < notifications.length; i++) {
            const notification = notifications[i];
            const notificationId = getNotificationId(notification);
            if (notificationId.length === 0) {
                continue;
            }
            const hidden = await isNotificationHidden(notificationId);
            if (hidden) {
                notification.parentElement.removeChild(notification);
            } else {
                const cross = createElement("span");
                cross.setAttribute("class", "notifications__item-cross");
                cross.innerText = "❌";
                cross.style.cursor = "pointer";
                cross.setAttribute("title", "Закрыть уведомление\nОно будет скрыто, пока не появится новое");
                cross.setAttribute("closeClass", notification.getAttribute("class"));
                cross.addEventListener('click', closeNotification);
                const container = notification.querySelector(".notifications__item > .notifications__item-container");
                container.appendChild(cross);
            }
        }
    }
}

function isNotificationHidden(notificationId) {
    return new Promise((resolve) => {
        chrome.storage.local.get("prozenHideNotification", function (result) {
            resolve(result !== undefined && result !== null && result.prozenHideNotification === notificationId);
        });
    });
}

async function addZenjournalCloseButton() {
    const zenjournalDiv0 = document.getElementsByClassName("publications-news-block")[0];
    const state = await getZenjournalState();
    /*const zenjournalDiv = document.getElementsByClassName("publications-news-block")[0];
    const zenjournalLink = zenjournalDiv.querySelector("a.publications-news-block__channel-link"); */
    const zenjournalDiv = document.querySelector("body > div.content > div.publications-root > div.publications-root__publications-list > div.publications-root__right-block > div.publications-news-block");
    const zenjournalLink = document.querySelector("body > div.content > div.publications-root > div.publications-root__publications-list > div.publications-root__right-block > div.publications-news-block > a.publications-news-block__channel-link");
    //body > div.content > div.publications-root > div.publications-root__publications-list > div.publications-root__right-block > div.publications-news-block > a.publications-news-block__channel-link
    //publications-news-block__channel-link
    //<a class="publications-news-block__channel-link" href="/id/59706d883c50f7cc7f69b291" target="_blank">Все статьи</a>
    const space = createElement("span");
    space.innerText = " • ";
    const hideLink = createElement("a", "publications-news-block__channel-link")
    if (state === "show") {
        hideLink.innerText = "Скрыть";
    } else {
        hideLink.innerText = "Вернуть";
    }
    zenjournalLink.insertAdjacentElement("afterend", space);
    space.insertAdjacentElement("afterend", hideLink);
}

// show, hide, prozen
function getZenjournalState() {
    return new Promise((resolve) => {
        chrome.storage.local.get("prozenHideZenjournal", function (result) {
            if (result === undefined || result === null ||
                result.prozenHideZenjournal === undefined ||
                result.prozenHideZenjournal === null) {
                resolve("show");
            } else {
                resolve(result.prozenHideZenjournal);
            }
        });
    });
}

function setNotifictionHidden(notificationId) {
    chrome.storage.local.set({prozenHideNotification: notificationId});
}

function copyTextToClipboard(text) {
    const copyFrom = document.createElement("textarea");
    copyFrom.setAttribute('readonly', '');
    copyFrom.style.position = 'absolute';
    copyFrom.style.left = '-9999px';
    copyFrom.style.too = '0px';
    copyFrom.textContent = text;
    document.body.appendChild(copyFrom);
    copyFrom.select();
    document.execCommand('copy');
    copyFrom.blur();
    document.body.removeChild(copyFrom);
}

function loadPublicationsCount(publicationType) {
    const url = COUNT_PUBLICATIONS_API_URL + encodeURIComponent(publicationType) + "&publisherId=" + publisherId;
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}

function loadPublications(publicationType, count) {
    const url = GET_PUBLICATIONS_API_URL + encodeURIComponent(count) + "&type=" + encodeURIComponent(publicationType) + "&publisherId=" + publisherId;
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}

async function loadAllPublications() {
    const publications = [];
    for (let i = 0; i < TYPES.length; i++) {
        const publicationType = TYPES[i];
        const response = await loadPublicationsCount(publicationType).then(response => {
            return response;
        });
        const count = response.count;
        const result = await loadPublications(publicationType, count).then(response => {
            const cards = [];
            for (let i = 0, len = response.publications.length; i < len; i++) {
                const pubData = {};
                const publication = response.publications[i];
                pubData.id = publication.id;
                pubData.feedShows = publication.privateData.statistics.feedShows;
                pubData.shows = publication.privateData.statistics.shows;
                pubData.views = publication.privateData.statistics.views;
                pubData.viewsTillEnd = publication.privateData.statistics.viewsTillEnd;
                pubData.comments = publication.privateData.statistics.comments;
                pubData.likes = publication.privateData.statistics.likes;
                pubData.sumViewTimeSec = publication.privateData.statistics.sumViewTimeSec;
                pubData.addTime = publication.addTime !== undefined ? publication.addTime : 0;
                pubData.type = publication.content.type;
                cards.push(pubData);
            }
            return cards;
        });
        publications.push(...result);
    }
    return publications;
}

function paucal(num, p1, p234, p) {
    const x = num % 100;
    if (x >= 10 && x < 20) {
        return num + " " + p;
    }
    const numStr = infiniteAndNanToStr(num, 0);
    switch (num % 10) {
        case 1:
            return numStr + " " + p1;
        case 2:
        case 3:
        case 4:
            return numStr + " " + p234;
        default:
            return numStr + " " + p;
    }
}

function debug(message, message2) {
    if (DEBUG) {
        let str = "[ПРОДЗЕН]: " + message;
        if (message2 !== undefined) {
            str += " " + message2;
        }
        console.log(str);
    }
}

function log(message) {
    if (DEBUG) {
        console.log(message);
    }
}


//////
function registerEvents() {

}


// Event handlers
