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
let metriksId;
let moneySaldo;
let moneyTotal;

let oldHref = window.location.href;
let observerWindowLocationHref;
let observerInfoBlockStats;
let observerBalanceTooltip;

start();

///////////////////////////////////
// Functions
///////////////////////////////////


async function start() {
    if (await getOption("prozen-switch") === false) {
        return;
    }
    const css = createElement("link");
    css.setAttribute("rel", "stylesheet");
    css.setAttribute("type", "text/css");
    css.setAttribute("href", chrome.extension.getURL("css/prozen.css"));
    document.head.appendChild(css);

    const script = createElement("script");
    script.setAttribute("type", "text/javascript");
    script.setAttribute("src", chrome.extension.getURL("js/page.js"));
    document.body.appendChild(script);

    window.addEventListener("message", function (event) {
        if (event.source !== window)
            return;
        if (event.data.type && (event.data.type === "prozen-data")) {
            token = event.data.text;
            data = event.data.jsonData;
            publisherId = event.data.jsonData.userPublisher.id;
            getBalanceAndMetriksId().then(result => {
                metriksId = result.metriksId;
                moneyTotal = result.total;
                moneySaldo = result.money;
                moneyDate = result.balanceDate;

                main();
            });

        }
    });
}


function getOption(optionId) {
    const optionsIds = ["prozen-switch", "prozen-article-link-switch", "prozen-studio-comments-switch"];
    return new Promise(resolve => {
        chrome.storage.local.get(optionsIds, option => {
            if (option.hasOwnProperty(optionId)) {
                resolve(option[optionId]);
            } else {
                resolve(true);
            }
        });
    });
}


function main() {
    const pageType = getPageType();
    if (pageType === "unknown") {
        return;
    }
    if (pageType === "article") {
        setTimeout(showStatsArticle, 300);
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

    if (pageType === "gallery") {
        setTimeout(articleShowStatsGallery, 300);
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
        if (isStudio()) {
            hideComments();
            addStudioMenu();
            // updateStudioBalance();
            registerObserverWindowsLocation();
            registerObserverBalance();

        } else {
            // Старый редактор
            registerTargetObserver();
            registerContentObserver();
        }
    }
    if (pageType === "publications") {
        addStudioMenu();
        registerObserverWindowsLocation();
    }
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
        setTimeout(showBalanceAndMetrics, 100);
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
                        setTimeout(showBalanceAndMetrics, 150);
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
    for (let i = 0; i < cards.length; i++) {
        const card = cards[i];
        const cardLinks = card.getElementsByTagName("a");
        if (cardLinks === undefined || cardLinks.length === 0) {
            continue;
        }
        const postLink = cardLinks[0].getAttribute("href");
        if (postLink == null || postLink.startsWith("/profile/editor/id/")) {
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
    const dayMod = dateTimeFormat(data.publication.content.modTime);
    const dayCreate = data.publication.addTime === undefined ? dayMod : dateTimeFormat(data.publication.addTime);
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

async function articleShowStatsGallery() {
    if (data === null) {
        return;
    }
    const postId = getPostIdFromUrl(window.location.pathname);
    const dayMod = dateTimeFormat(data.publication.content.modTime);
    const dayCreate = data.publication.addTime === undefined ? dayMod : dateTimeFormat(data.publication.addTime);
    const showTime = dayMod !== dayCreate ? dayCreate + " (" + dayMod + ")" : dayCreate;
    const articleData = await loadPublicationStat(postId);

    const sumViewTimeSec = articleData.sumViewTimeSec;
    const views = articleData.views;
    const shows = articleData.shows;
    const viewsTillEnd = articleData.viewsTillEnd;

    const divStat = createElement("div", "card-gallery-text");
    divStat.style.paddingLeft = "15px";
    divStat.style.paddingRight = "15px";
    divStat.style.paddingBottom = "10px";


    {
        const spanDate = createElement("span");
        // Время создания / Модификации
        spanDate.innerText = "◻" + dayCreate;
        spanDate.setAttribute("title", "Время создания (модификации)");
        divStat.appendChild(spanDate);
    }

    {
        // Среднее время досмотров: ⌚
        const spanTime = createElement("span");
        spanTime.innerText = " ⌚ " + secToHHMMSS(infiniteAndNan(sumViewTimeSec / viewsTillEnd));
        spanTime.setAttribute("title", "Среднее время\nпросмотра: " + secToText(infiniteAndNan(sumViewTimeSec / viewsTillEnd)));
        divStat.appendChild(spanTime);
    }

    {
        const br1 = createElement("br");
        divStat.appendChild(br1)
    }

    {
        const spanViews = createElement("span");
        // Просмотры 👀
        spanViews.innerText = "👀 " + views.toLocaleString(undefined, {maximumFractionDigits: 0});
        spanViews.setAttribute("title", "Просмотры");
        divStat.appendChild(spanViews);
    }

    {
        // Досмотры 🖼️
        const spanViewsTillEnd = createElement("span");
        spanViewsTillEnd.innerText = " 🖼️ " + viewsTillEnd.toLocaleString(undefined, {maximumFractionDigits: 0}) + " (" + infiniteAndNan(viewsTillEnd / views * 100).toFixed(2) + "%)";
        spanViewsTillEnd.setAttribute("title", "Досмотры");
        divStat.appendChild(spanViewsTillEnd);
    }

    {
        const spanLink = createElement("span");
        spanLink.innerText = " 🔗";
        spanLink.setAttribute("title", "Сокращённая ссылка на статью.\nКликните, чтобы скопировать её в буфер обмена.");
        spanLink.addEventListener('click', copyTextToClipboard.bind(null, shortUrl()));
        spanLink.style.cursor = "pointer";
        divStat.appendChild(spanLink);
    }

    {
        if (checkNoIndex()) {
            const spanRobot = createElement("span");
            spanRobot.innerText = " 🤖";
            spanRobot.setAttribute("title", "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
                "Публикация не индексируется поисковиками.\n" +
                "Примечание: связь этого тега с показами,\n" +
                "пессимизацией и иными ограничениями канала\n" +
                "официально не подтверждена.");
            divStat.appendChild(spanRobot);
        }
    }

    const divSeparator = document.getElementsByClassName("ui-lib-desktop-gallery-page__separator")[0];
    divSeparator.insertAdjacentElement("afterend", divStat);
}

async function articleShowStatsVideo() {
    if (data === null) {
        return;
    }
    const postId = getPostIdFromUrl(window.location.pathname);
    const dayMod = dateTimeFormat(data.publication.content.modTime);
    const dayCreate = data.publication.addTime === undefined ? dayMod : dateTimeFormat(data.publication.addTime);
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
        const spanCount1 = createElement("span", "article__date-video");
        spanCount1.innerText = "📺 " + views.toLocaleString(undefined, {maximumFractionDigits: 0});
        spanCount1.setAttribute("title", "Просмотры");
        container.appendChild(spanCount1);
    }
    {
        // Среднее время просмотра
        const spanCount3 = createElement("span", "article__date-video");
        spanCount3.innerText = "⌚ " + secToText(infiniteAndNan(sumViewTimeSec / viewsTillEnd));
        spanCount3.setAttribute("title", "Среднее время просмотра");
        container.appendChild(spanCount3);
    }
    {
        const spanIcon4 = createElement("span", "article__date-video");
        spanIcon4.innerText = "🔗";
        spanIcon4.setAttribute("title", "Сокращённая ссылка на статью.\nКликните, чтобы скопировать её в буфер обмена.");
        spanIcon4.addEventListener('click', copyTextToClipboard.bind(null, shortUrl()));
        spanIcon4.style.cursor = "pointer";
        container.appendChild(spanIcon4);
    }

    if (checkNoIndex()) {
        const spanIcon5 = createElement("span", "article__date-video");
        spanIcon5.innerText = "🤖";
        spanIcon5.setAttribute("title", "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
            "Публикация не индексируется поисковиками.\n" +
            "Примечание: связь этого тега с показами,\n" +
            "пессимизацией и иными ограничениями канала\n" +
            "официально не подтверждена.");
        container.appendChild(spanIcon5);
    }
}

function addHeaderClicks() {
    getOption("prozen-article-link-switch").then(option => {
        if (option) {
            const headers = document.querySelectorAll("h2, h3");
            if (headers.length > 0) {
                for (let i = 0; i < headers.length; i++) {
                    const header = headers [i];
                    const ancorId = header.getAttribute("id");
                    if (ancorId !== undefined && ancorId !== null) {
                        const clickIcon = createElement("span", "publication_header_icon_url");
                        clickIcon.setAttribute("title", "Ссылка на заголовок.\n" +
                            "Кликните, чтобы скопировать её в буфер обмена");
                        clickIcon.addEventListener('click', copyTextToClipboard.bind(null, shortUrl() + "#" + ancorId));
                        header.insertBefore(clickIcon, header.firstChild);
                    }
                }
            }
        }
    });
}


async function showStatsArticle() {
    if (data === null) {
        return;
    }
    const postId = getPostIdFromUrl(window.location.pathname);
    const dayMod = dateTimeFormat(data.publication.content.modTime);
    const dayCreate = data.publication.addTime === undefined ? dayMod : dateTimeFormat(data.publication.addTime);
    const showTime = dayMod !== dayCreate ? dayCreate + " (" + dayMod + ")" : dayCreate;
    const articleData = await loadPublicationStat(postId);
    const sumViewTimeSec = articleData.sumViewTimeSec;
    const views = articleData.views;
    const shows = articleData.shows;
    const viewsTillEnd = articleData.viewsTillEnd;

    const hasAdv = document.getElementsByClassName("article-stats-view__block-item").length; // 1 - рекламная статья, 0 - обычная

    let articleStatsViewRedesignItems = document.getElementsByClassName("article-stats-view__item");
    const elArticleDate = articleStatsViewRedesignItems[hasAdv];
    elArticleDate.innerText = showTime;
    elArticleDate.setAttribute("title", "Время создания (редактирования)");

    if (articleStatsViewRedesignItems.length == 1 + hasAdv) {
        document.getElementsByClassName("article-stats-view article-stats-view_theme_none")[0].appendChild(createElement("div", "article-stats-view__item"));
        articleStatsViewRedesignItems = document.getElementsByClassName("article-stats-view__item");
    }

    const elArticleStats = articleStatsViewRedesignItems[articleStatsViewRedesignItems.length - 1]
    elArticleStats.classList.remove("article-stats-view__item_no-opacity");
    removeChilds(elArticleStats);

    const container = createElement("div", "article-stats-view__info-container article-stats-view__info-container_loaded");
    elArticleStats.appendChild(container);

    const containerInner = createElement("div", "article-stats-view__info-inner");
    container.appendChild(containerInner);

    // Просмотры
    const viewsContainer = createElement("div", "article-stats-view__stats-item");
    viewsContainer.setAttribute("title", "Просмотры");
    const viewsIcon = createElement("span", "article-stats-view__stats-item-icon publication_icon_views_2");
    viewsContainer.appendChild(viewsIcon);
    const viewsText = createElement("span", "article-stats-view__stats-item-count")
    viewsText.innerText = numFormat(views, 0);
    viewsContainer.appendChild(viewsText);

    containerInner.appendChild(viewsContainer);

    // Дочитывания
    const fullViewsContainer = createElement("div", "article-stats-view__stats-item");
    fullViewsContainer.setAttribute("title", "Дочитывания");
    const fullViewsIcon = createElement("span", "article-stats-view__stats-item-icon publication_icon_full_views");
    fullViewsContainer.appendChild(fullViewsIcon);
    const fullViewsText = createElement("span", "article-stats-view__stats-item-count")
    fullViewsText.innerText = numFormat(viewsTillEnd, 0) + " (" + infiniteAndNan(viewsTillEnd / views * 100).toFixed(2) + "%)"
    fullViewsContainer.appendChild(fullViewsText);

    containerInner.appendChild(fullViewsContainer);

    // Среднее время
    const avgTimeContainer = createElement("div", "article-stats-view__stats-item");
    avgTimeContainer.setAttribute("title", "Среднее время дочитывания");
    const avgTimeIcon = createElement("span", "article-stats-view__stats-item-icon publication_icon_read_time");
    avgTimeContainer.appendChild(avgTimeIcon);
    const avgTimeText = createElement("span", "article-stats-view__stats-item-count");
    avgTimeText.innerText = secToText(infiniteAndNan(sumViewTimeSec / viewsTillEnd));
    avgTimeContainer.appendChild(avgTimeText);

    containerInner.appendChild(avgTimeContainer);

    // Короткая ссылка
    const shortLinkContainer = createElement("div", "article-stats-view__stats-item");
    shortLinkContainer.setAttribute("title", "Сокращённая ссылка на статью.\nКликните, чтобы скопировать её в буфер обмена.");
    const shortLinkIcon = createElement("span", "publication_icon_short_url");
    shortLinkIcon.addEventListener('click', copyTextToClipboard.bind(null, shortUrl()));
    shortLinkIcon.style.cursor = "pointer";
    shortLinkContainer.appendChild(shortLinkIcon);

    elArticleStats.appendChild(shortLinkContainer)

    // Грустный робот
    if (checkNoIndex()) {
        const sadRobotContainer = createElement("div", "article-stats-view__stats-item");
        sadRobotContainer.setAttribute("title", "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
            "Публикация не индексируется поисковиками.\n" +
            "Примечание: связь этого тега с показами,\n" +
            "пессимизацией и иными ограничениями канала\n" +
            "официально не подтверждена.");
        const sadRobotIcon = createElement("span", "article-stats-view__stats-item-icon publication_icon_sad_robot");
        sadRobotContainer.appendChild(sadRobotIcon);

        elArticleStats.appendChild(sadRobotContainer);
    }

    // Ссылка на подзаголовок
    addHeaderClicks();
}

function getPostIdFromUrl(url) {
    const ln = url.replace("?from=editor", "").split(url.includes("-") ? "-" : "/");
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
            if (data.isGallery === true) {
                return "gallery";
            }
        }
    } else if (path.startsWith("/profile/editor/")) {
        if (path.endsWith("/money/simple")) {
            return "money";
        }
        if (path.endsWith("/publications")) {
            return "publications";
        }
        if (path.endsWith("/music")) {
            return "music";
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

async function showBalance() {
    const url = URL_API_MEDIA + publisherId + "/money";
    const responce = await fetch(url, {
        credentials: 'same-origin',
        headers: {'X-Csrf-Token': token}
    });
    const data = await responce.json();
    if (data.money.isMonetizationAvailable && data.money.simple !== undefined && data.money.simple.balance !== undefined) {
        const simpleBalance = data.money.simple.balance;
        const personalDataBalance = data.money.simple.personalData.balance;
        const money = parseFloat((simpleBalance > personalDataBalance ? simpleBalance : personalDataBalance).toFixed(2));
        let total = money;
        for (let i = 0, len = data.money.simple.paymentHistory.length; i < len; i++) {
            if (data.money.simple.paymentHistory[i]["status"] === "completed") {
                total += parseFloat(data.money.simple.paymentHistory[i]["amount"]);
            }
        }
        setBalance(money, total);
    }
    return data.publisher.privateData.metrikaCounterId
}

function showBalanceAndMetrics() {
    showBalance().then(metricsId =>
        setTimeout(addProzenMenu.bind(null, metricsId), 1000)
    );
}

// OLD EDITOR
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
        const metricsUrl = metricsId !== undefined && metricsId !== null ? "https://metrika.yandex.ru/dashboard?id=" + metricsId : "https://metrika.yandex.ru/list";
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
        checkHasNone(publisherId).then(isNone => {
            if (isNone) {
                aSadRobot.innerText = "Канал не индексируется 🤖";
                aSadRobot.setAttribute("title", "Обнаружен мета-тег <meta property=\"robots\" content=\"none\" />\n" +
                    "Канал не индексируется поисковиками.\n" +
                    "Это нормальная ситуация для новых каналов."); //\n" +
                //"Нажмите здесь, чтобы узнать подробнее.");
                aSadRobot.removeAttribute("data-tip");
                aSadRobot.removeEventListener('click', clickFindSadRobots);
            }
        })

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

//OLD EDITOR
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
    chrome.storage.local.set({
        prozenId: id,
        prozenSearch: textToFind,
        prozenToken: token,
        prozenPublisherId: publisherId
    }, function () {
        window.open(chrome.extension.getURL("search.html"));
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
        window.open(chrome.extension.getURL("totalstats.html"));
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
        if (value.addTime !== undefined && value.card.hasChildNodes()) {
            setPublicationTime(value);
            modifyCardFooter(value, publicationId);
        }
        value.processed = true;
    }
}

function setPublicationTime(pubData) {
    const dateDiv = pubData.card.getElementsByClassName("card-cover-publication__status")[0];
    if (dateDiv.innerText.match("(^Вчера)|(^Сегодня)|(^Три дня назад)|(^\\d{1,2}\\s([а-я]+)(\\s201\\d)?)")) {
        const dayMod = dateTimeFormat(pubData.modTime);
        const dayCreate = pubData.addTime === undefined ? dayMod : dateTimeFormat(pubData.addTime);
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
        for (let i = 0; i < tagObjects.length; i++) {
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
    const tagTip = textTags.length === 0 ? "Теги не указаны" : "Теги: " + joinByThree(textTags);
    if (tagTip.indexOf("\n") !== -1) {
        iconSpan1.setAttribute("title", tagTip);
    } else {
        iconSpan1.setAttribute("data-tip", tagTip);
    }
    if (textTags.length !== 0) {
        iconSpan1.addEventListener('click', copyTextToClipboard.bind(null, textTags));
    }
    iconSpan1.style.cursor = "pointer";

    const iconSpan2 = document.createElement("span");
    iconSpan2.setAttribute("class", "icon_short_url");
    iconSpan2.setAttribute("data-tip", "Скопировать короткую ссылку");
    iconSpan2.style.cursor = "pointer";
    iconSpan2.addEventListener('click', copyTextToClipboard.bind(null, url));
    a.appendChild(iconSpan2);
    if (textTags.length !== 0) {
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

function dateTimeFormat(unixTime) {
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

function checkHasNone(id) {
    return new Promise(resolve => {
        const xhr = new XMLHttpRequest();
        xhr.onload = function () {
            const metas = xhr.responseXML.head.getElementsByTagName('meta');
            for (let i = 0; i < metas.length; i++) {
                if (metas[i].getAttribute('property') === "robots") {
                    if (metas[i].getAttribute('content') === "none") {
                        resolve(true);
                    }
                    break;
                }
            }
            resolve(false);
        };
        xhr.open("GET", URL_ZEN_ID + id);
        xhr.responseType = "document";
        xhr.send();
    });
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

function numFormat(num, digits) {
    return num.toLocaleString(undefined, {maximumFractionDigits: digits === undefined ? 0 : digits});
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

function shortUrl() {
    const url = window.location.href.split("\?")[0].split("#")[0];
    return url.substr(0, url.lastIndexOf("/")) + "/" + url.substr(url.lastIndexOf("-") + 1, url.length - 1);
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
/************************************************/
/*                 СТУДИЯ!                      */
/************************************************/
// Определяем изменение адреса
function registerObserverWindowsLocation() {
    const bodyList = document.querySelector("body")
    if (observerWindowLocationHref !== undefined) {
        observerWindowLocationHref.disconnect();
    }
    observerWindowLocationHref = new MutationObserver(mutations => {
        mutations.forEach(() => {
            if (oldHref !== document.location.href) {
                oldHref = document.location.href;
                main();
            }
        });
    });
    const config = {
        childList: true,
        subtree: true
    };
    observerWindowLocationHref.observe(bodyList, config);
}
// Вывод подсказки для баланса
function registerObserverBalanceTooltip(ariaDescribedBy) {
    const target = document.querySelector("body");
    if (observerBalanceTooltip !== undefined) {
        observerBalanceTooltip.disconnect();
    }
    observerBalanceTooltip = new MutationObserver(mutations => {
        mutations.forEach(mutation => {
            if (mutation.addedNodes && mutation.addedNodes.length > 0) {
                mutation.addedNodes.forEach(e => {
                    if (e.tagName === "DIV" && e.classList.contains("author-studio-info-item-desktop")) {
                        console.log(e.tagName + " " + e.className);
                        if (e.childNodes.length > 0 && e.childNodes[0].id === ariaDescribedBy) {
                            setBalanceTooltip(e.childNodes[0]);
                            observerBalanceTooltip.disconnect();
                        }
                    }
                });
            }
        });
    });
    observerBalanceTooltip.observe(target, {childList: true});
}
// Отображение баланса
function registerObserverBalance() {
    const target = document.querySelector("ul.author-studio-info-block__stats");
    if (target == null) {
        setTimeout(registerObserverBalance, 500);
        return;
    }
    target.querySelectorAll("li.author-studio-info-block__stat-item").forEach(e => {
        if (e.tagName === "LI") {
            const name = e.querySelector("div.author-studio-info-item__stat-item-name").textContent;
            if (name === "баланс") {
                updateStudioBalance(e.childNodes[0]);
                return;
            }
        }
    });

    if (observerInfoBlockStats !== undefined) {
        observerInfoBlockStats.disconnect();
    }
    observerInfoBlockStats = new MutationObserver(mutations => {
        mutations.forEach(mutation => {
            if (mutation.addedNodes && mutation.addedNodes.length > 0) {
                mutation.addedNodes.forEach(e => {
                    if (e.tagName === "LI") {
                        const name = e.querySelector("div.author-studio-info-item__stat-item-name").textContent;
                        if (name === "баланс") {
                            updateStudioBalance(e.childNodes[0]);
                            observerInfoBlockStats.disconnect();
                        }
                    }
                });
            }
        });
    });
    observerInfoBlockStats.observe(target, {childList: true});
}


function setBalanceTooltip(tooltip) {
    if (document.getElementById("prozen-money-date") != null || document.getElementById("prozen-money-total")) {
        return;
    }
    const messageDiv = tooltip.getElementsByClassName("author-studio-info-item-hint")[0];
    if (moneyDate != null) {
        const p = createElement("p", "Text Text_typography_text-14-18 author-studio-info-item-hint__text");
        p.id = "prozen-money-date";
        p.innerText = `Начислено за ${moneyDate}`;
        messageDiv.appendChild(p);
    }
    if (moneyTotal != null) {
        const p = createElement("p", "Text Text_typography_text-14-18 author-studio-info-item-hint__text");
        p.id = "prozen-money-total";
        p.innerText = `Всего: ${moneyTotal} ₽`;
        messageDiv.appendChild(p);
    }
}



function updateStudioBalance(balanceElement) {
    if (!balanceElement.hasAttribute("aria-describedby")) {
        return;
    }
    balanceElement.addEventListener('click', openUrl.bind(null, `https://zen.yandex.ru/profile/editor/id/${publisherId}/money/`));

    if (moneySaldo != null) {
        balanceElement.getElementsByClassName("author-studio-info-item__stat-item-value")[0].innerText = moneySaldo;
    }
    const ariaDescribedBy = balanceElement.getAttribute("aria-describedby");
    registerObserverBalanceTooltip(ariaDescribedBy);
}



// Поддержка Студии
function isStudio() {
    return document.getElementsByClassName("author-studio-layout__content").length > 0;
}

async function addStudioMenu() {
    if (document.getElementById("prozen-main-menu") == null) {
        const navbars = document.getElementsByClassName("navbar__nav-list");
        const prozenMenu = createElement("ul", "navbar__nav-list prozen_navbar");
        prozenMenu.id = "prozen-main-menu";
        prozenMenu.appendChild(creatProzenMenuElement("\nДополнительно", null, null, "Добавлено расширением ПРОДЗЕН", true));
        prozenMenu.appendChild(creatProzenMenuElement("Полная статистика", "prozen_menu_stats", clickTotalStatsButton, "Сводная статистика"));
        const metriksUrl = metriksId !== undefined && metriksId !== null ? "https://metrika.yandex.ru/dashboard?id=" + metriksId : "https://metrika.yandex.ru/list";
        prozenMenu.appendChild(creatProzenMenuElement("Метрика", "prozen_menu_metrika", metriksUrl, "Просмотр статистики в Яндекс.Метрике"));
        prozenMenu.appendChild(creatProzenMenuElement("Поиск", "prozen_menu_search", clickSearchButton, "Альтернативная функция поиска"));
        prozenMenu.appendChild(creatProzenMenuElement("Проверка noindex", "prozen_menu_robot", clickFindSadRobots, "Поиск публикаций с мета-тегом robots"));
        prozenMenu.appendChild(creatProzenMenuElement("Поддержка", "prozen_support_mail", openUrlNewTab.bind(null,"https://yandex.ru/support/zen/troubleshooting/feedback.html"), "Написать в службу поддержки Яндекс.Дзен"));
        navbars[0].insertAdjacentElement("afterend", prozenMenu);
    }
}

function creatProzenMenuElement(title, iconClass, url = null, hint = null, bold = false) {
    const navItem = createElement("li", "nav-item")
    if (hint !== null) {
        navItem.setAttribute("title", hint);
    }
    const menuLine = createElement("div", "navbar__nav-item-content");
    if (url == null) {
        const a = createElement("div", "navbar__nav-link")
        navItem.appendChild(a);
        a.appendChild(menuLine);
        bold = true;
    } else if (typeof url === "string") {
        const a = createElement("a", "navbar__nav-link")
        a.setAttribute("target", "_blank")
        a.setAttribute("href", url)
        navItem.appendChild(a);
        a.appendChild(menuLine);
    } else {
        const a = createElement("a", "navbar__nav-link")
        a.addEventListener('click', url);
        a.cursor = "pointer";
        navItem.appendChild(a);
        a.appendChild(menuLine);
    }

    const menuIcon = createElement("span", "navbar__icon");
    if (iconClass != null) {
        const icon = createElement("span", "ui-lib-generic-svg");
        icon.classList.add(iconClass);
        menuIcon.appendChild(icon);
    }
    menuLine.appendChild(menuIcon);

    const menuText = createElement("span", "navbar__text");
    menuText.innerText = title;
    menuLine.appendChild(menuText);
    if (bold) {
        menuText.style.fontWeight = "bold";
    }
    return navItem;
}

async function getBalanceAndMetriksId() {
    const result = {money: null, total: null, balanceDate: null, metriksId: null}
    const url = URL_API_MEDIA + publisherId + "/money";
    const responce = await fetch(url, {
        credentials: 'same-origin',
        headers: {'X-Csrf-Token': token}
    });
    const data = await responce.json();

    if (data.money.isMonetizationAvailable && data.money.simple !== undefined && data.money.simple.balance !== undefined) {
        const simpleBalance = data.money.simple.balance;
        const options = {year: 'numeric', month: 'long', day: 'numeric'};
        result.balanceDate = new Date(data.money.simple.balanceDate).toLocaleString("ru-RU", options);
        const personalDataBalance = data.money.simple.personalData.balance;
        const money = parseFloat((simpleBalance > personalDataBalance ? simpleBalance : personalDataBalance));

        let total = money;
        for (let i = 0, len = data.money.simple.paymentHistory.length; i < len; i++) {
            if (data.money.simple.paymentHistory[i]["status"] === "completed") {
                total += parseFloat(data.money.simple.paymentHistory[i]["amount"]);
            }
        }
        result.money = money.toLocaleString("ru-RU", {maximumFractionDigits: 2});
        result.total = total.toLocaleString("ru-RU", {maximumFractionDigits: 2})
    }
    result.metriksId = data.publisher.privateData.metrikaCounterId;
    return result;
}

function hideComments() {
    getOption("prozen-studio-comments-switch").then(enable => {
        if (!enable) {
            document.getElementsByClassName("author-studio-main__middle-column")[0].style.display = "none";
        }
    });
}

function openUrl(url) {
    location.href = url;
}

function openUrlNewTab(url) {
    window.open(url, "_blank");
}