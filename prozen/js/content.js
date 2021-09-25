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
let moneyDate;

let oldHref = window.location.href;
let observerWindowLocationHref;
let observerInfoBlockStats;
let observerBalanceTooltip;

start();

///////////////////////////////////
// Functions
///////////////////////////////////

async function start() {
    if (await getOption(OPTIONS.prozen) === false) {
        return;
    }
    listenToRequests();
    injectCssAndScript();
}

function injectCssAndScript() {
    window.removeEventListener("message", ReceiveProzenData);
    if (!document.getElementById("prozen-css")) {
        const css = createElement("link");
        css.setAttribute("rel", "stylesheet");
        css.setAttribute("type", "text/css");
        css.id = "prozen-css";
        css.setAttribute("href", chrome.extension.getURL("css/prozen.css"));
        document.head.appendChild(css);
    }
    if (!document.getElementById("prozen-page-script")) {
        const script = createElement("script");
        script.setAttribute("type", "text/javascript");
        script.id = "prozen-page-script";
        script.setAttribute("src", chrome.extension.getURL("js/page.js"));
        document.body.appendChild(script);
    }
    window.addEventListener("message", ReceiveProzenData);
}


function main(updatedId = null) {
    const pageType = getPageType();
    publisherId = updatedId != null ? updatedId : getPublisherId();
    switch (pageType) {
        case "main":
            if (token != null && publisherId != null) {
                mediaUrl = window.location.href.replace("profile/editor", "media");
                if (isStudio()) {
                    hideComments();
                    addStudioMenu();
                    // updateStudioBalance();
                    registerObserverWindowsLocation();
                    registerObserverBalance();
                    listenToRequests();
                    addInformerBlock();
                } else {
                    // Старый редактор
                    registerTargetObserver();
                    registerContentObserver();
                    registerObserverWindowsLocation();

                }
            }
            break;
        case "publications":
            if (token != null && publisherId != null) {
                addStudioMenu();
                registerObserverWindowsLocation();
            }
            break;
        case "money":
            if (token != null && publisherId != null) {
                addStudioMenu();
                registerObserverWindowsLocation();
            }
            break;
        case "unknown":
            break;
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
    observer = new MutationObserver(mutations => {
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
    if (path.startsWith("/profile/editor/")) {
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
    const response = await fetch(url, {
        credentials: 'same-origin',
        headers: {'X-Csrf-Token': token}
    });
    const data = await response.json();
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
            Promise.all(articles).then(articles => {
                for (const article of articles) {
                    const id = article.publications[0].id;
                    const card = publications.get(id);
                    card.addTime = article.publications[0].addTime;
                    card.modTime = article.publications[0].content.modTime;
                    card.tags = article.publications[0].tags;
                    card.processed = true;
                }
            }).then(() => {
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
    if (pubData.card.classList.contains("card-cover-publication_type_brief")) {
        return;
    }

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
                sendProzenRequest();
                //start();
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
    if (!await getOption(OPTIONS.prozenMenu)) {
        return;
    }

    let oldStudioMenu = document.getElementById("prozen-main-menu");
    if (oldStudioMenu != null && oldStudioMenu.getAttribute("data-publisherId") !== publisherId) {
        oldStudioMenu.parentNode.removeChild(oldStudioMenu);
        oldStudioMenu = null;
    }

    if (oldStudioMenu == null) {
        const navbars = document.getElementsByClassName("navbar__nav-list");
        const prozenMenu = createElement("ul", "navbar__nav-list prozen_navbar");
        prozenMenu.id = "prozen-main-menu";
        prozenMenu.setAttribute("data-publisherId", publisherId);
        prozenMenu.appendChild(creatProzenMenuElement("\nДополнительно", null, null, "Добавлено расширением ПРОДЗЕН", true));
        prozenMenu.appendChild(creatProzenMenuElement("Полная статистика", "prozen_menu_stats", clickTotalStatsButton, "Сводная статистика"));
        const metriksUrl = metriksId !== undefined && metriksId !== null ? "https://metrika.yandex.ru/dashboard?id=" + metriksId : "https://metrika.yandex.ru/list";
        prozenMenu.appendChild(creatProzenMenuElement("Метрика", "prozen_menu_metrika", metriksUrl, "Просмотр статистики в Яндекс.Метрике"));
        prozenMenu.appendChild(creatProzenMenuElement("Поиск", "prozen_menu_search", clickSearchButton, "Альтернативная функция поиска"));
        prozenMenu.appendChild(creatProzenMenuElement("Проверка noindex", "prozen_menu_robot", clickFindSadRobots, "Поиск публикаций с мета-тегом robots"));
        prozenMenu.appendChild(creatProzenMenuElement("Служба поддержки", "prozen_support_mail", openUrlNewTab.bind(null, "https://yandex.ru/support/zen/troubleshooting/feedback.html"), "Обратиться в службу поддержки Яндекс.Дзена"));
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


function hideComments() {
    getOption(OPTIONS.dashboardComments).then(enable => {
        if (!enable) {
            const column = document.getElementsByClassName("author-studio-main__middle-column")[0];
            column.parentNode.removeChild(column);
            //column.style.display = "none";
        }
    });
}

function openUrl(url) {
    location.href = url;
}

function openUrlNewTab(url) {
    window.open(url, "_blank");
}

function sendProzenRequest() {
    const data = {
        type: "prozen-request"
    };
    window.postMessage(data, "*");
}

function ReceiveProzenData(event) {
    if (event.source !== window) {
        return;
    }
    if (event.data.type && (event.data.type === "prozen-data")) {
        token = event.data.text;
        data = event.data.jsonData;
        publisherId = event.data.jsonData.publisher.id;
        const pageType = getPageType();
        if (pageType === "main" || pageType === "publications") {
            getBalanceAndMetriksId().then(result => {
                metriksId = result.metriksId;
                moneyTotal = result.total;
                moneySaldo = result.money;
                moneyDate = result.balanceDate;
                main(publisherId);
            });
        } else {
            main(publisherId);
        }
    }
}

function listenToRequests() {
    if (chrome.runtime.onMessage.hasListener(backgroundListener)) {
        chrome.runtime.onMessage.removeListener(backgroundListener);
    }
    chrome.runtime.onMessage.addListener(backgroundListener);
}

function backgroundListener(request) {
    if (!isStudio()) {
        chrome.runtime.onMessage.removeListener(backgroundListener);
        return;
    }
    if (request.type === "prozen-webrequest") {
        publisherId = request.publisherId;
        token = request.token;
        processDashboardCards();
    }
}

async function processDashboardCards() {
    const data = await getPublicationsByFilter(5);
    const studioPublicationsBlock = document.getElementsByClassName("author-studio-publications-block")[0];
    const publicationsBlocks = studioPublicationsBlock.getElementsByClassName("author-studio-publication-item");
    if (publicationsBlocks.length > 0) {
        for (let i = 0; i < publicationsBlocks.length; i++) {
            const publicationBlock = publicationsBlocks.item(i);
            const publicationtionId = getPublicationBlockId(publicationBlock);
            const publicationUrl = getPublicationBlockUrl(publicationBlock);
            if (publicationtionId != null) {
                const publicationData = getCardData(publicationtionId, data.publications);
                if (publicationData != null) {
                    const card = jsonToCardData(publicationData, publicationUrl);
                    modifyDashboardCard(publicationBlock, card);
                }
            }

        }
    }
}

function modifyDashboardCard(publicationBlock, card) {
    /*
       Показы         Лайки     Среднее время
       Просмотры      Коменты   isBanned?  moderationStatus // ок? isPromotionAvailable snippetFrozen
       Дочитывания    ER        Короткая ссылка/ Теги
     */
    const timeBlock = publicationBlock.getElementsByClassName("author-studio-publication-item__date")[0];
    timeBlock.innerText = card.timeStr;
    //timeBlock.style.opacity = 1;

    const publicationItemStats = publicationBlock.getElementsByClassName("author-studio-publication-item__stats")[0];
    removeChilds(publicationItemStats);

    // Первая колонка
    const col1 = createElement("div", "author-studio-publication-item__stat-item author-studio-publication-item__stat-item_type_views");
    publicationItemStats.appendChild(col1);

    // Показы
    const с1r1 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    с1r1.setAttribute("title", "Показы");
    col1.appendChild(с1r1);
    const с1r1Icon = createElement("span", "prozen_studio_card_icon_shows");
    const с1r1Text = createElement("span");
    с1r1Text.innerText = card.feedShowStr;
    с1r1.appendChild(с1r1Icon);
    с1r1.appendChild(с1r1Text);

    // Просмотры
    const c1r2 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    c1r2.setAttribute("title", "Просмотры (CTR, %)");
    const с1r2Icon = createElement("span", "prozen_studio_card_icon_views");
    const с1r2Text = createElement("span");
    с1r2Text.innerText = card.viewsStr;
    c1r2.appendChild(с1r2Icon);
    c1r2.appendChild(с1r2Text);
    col1.appendChild(c1r2);

    // Дочитывания
    const c1r3 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    c1r3.setAttribute("title", "Дочитывания (%)");
    const c1r3Icon = createElement("span", "prozen_studio_card_icon_full_views");
    const c1r3Text = createElement("span");
    c1r3Text.innerText = card.viewsTillEndStr;
    c1r3.appendChild(c1r3Icon);
    c1r3.appendChild(c1r3Text);
    col1.appendChild(c1r3);

    // Вторая колонка
    const col2 = createElement("div", "author-studio-publication-item__stat-item author-studio-publication-item__stat-item_type_shows");
    publicationItemStats.appendChild(col2);

    // Лайки
    const c2r1 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    c2r1.setAttribute("title", "Лайки (%)");
    const c2r1Icon = createElement("span", "prozen_studio_card_icon_like");
    const c2r1Text = createElement("span");
    c2r1Text.innerText = card.likesStr;
    c2r1.appendChild(c2r1Icon);
    c2r1.appendChild(c2r1Text);
    col2.appendChild(c2r1);

    // Коменты
    const c2r2 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    c2r2.setAttribute("title", "Комментарии (%)");
    const c2r2Icon = createElement("span", "prozen_studio_card_icon_comments");
    const c2r2Text = createElement("span");
    c2r2Text.innerText = card.commentsStr;
    c2r2.appendChild(c2r2Icon);
    c2r2.appendChild(c2r2Text);
    col2.appendChild(c2r2);

    // ER
    const c2r3 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    c2r3.setAttribute("title", "Коэффициент вовлечённости, ER (%)");
    const c2r3Icon = createElement("span", "prozen_studio_card_icon_er");
    const c2r3Text = createElement("span");
    c2r3Text.innerText = card.erStr;
    c2r3.appendChild(c2r3Icon);
    c2r3.appendChild(c2r3Text);
    col2.appendChild(c2r3);

    // Третья колонка
    const col3 = createElement("div", "author-studio-publication-item__stat-item author-studio-publicauthor-studio-publication-itemation-item__stat-item_type_comments");
    publicationItemStats.appendChild(col3);

    // Среднее время просмотра
    const c3r1 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    c3r1.style.textAlign = "right";
    c3r1.setAttribute("title", "Среднее время просмотра: " + card.readTimeStr);
    const c3r1Icon = createElement("span", "prozen_studio_card_icon_clock");
    const c3r1Text = createElement("span");
    c3r1Text.innerText = card.readTimeStrHMS;
    c3r1.appendChild(c3r1Icon);
    c3r1.appendChild(c3r1Text);
    col3.appendChild(c3r1);

    // Теги
    const tagsHint = card.tags.length === 0 ? "Теги не указаны" : `Теги: ${card.tagsStr}`;
    const c3r2 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    c3r2.style.textAlign = "right";
    c3r2.setAttribute("title", tagsHint);
    const c3r2Icon = createElement("span", "prozen_studio_card_icon_tags");
    const c3r2Text = createElement("span");
    c3r2Text.innerText = paucal(card.tags.length, "тег", "тега", "тегов");
    c3r2.appendChild(c3r2Icon);
    c3r2.appendChild(c3r2Text);
    c3r2.addEventListener('click', event => {
        copyTextToClipboard(card.tagsStr);
        event.preventDefault();
    });
    col3.appendChild(c3r2);

    // Ссылка
    const c3r3 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    c3r3.style.textAlign = "right";
    c3r3.setAttribute("title", "Короткая ссылка.\nНажмите, чтобы скопировать в буфер обмена.");
    const c3r3Icon = createElement("span", "prozen_studio_card_icon_link");
    c3r3.appendChild(c3r3Icon);
    const shortUrl = mediaUrl != null ? `https://zen.yandex.ru/${mediaUrl}/${card.id}` : card.shortUrl;
    c3r3.addEventListener('click', event => {
        copyTextToClipboard(shortUrl);
        event.preventDefault();
    });
    col3.appendChild(c3r3);
}

function jsonToCardData(publicationData, publicationUrl) {
    return new Card(publicationData, publicationUrl);
}

function getPublicationBlockUrl(publicationBlock) {
    return publicationBlock.hasAttribute("href") ? publicationBlock.getAttribute("href") : null;
}

function getPublicationBlockId(publicationBlock) {
    if (publicationBlock.hasAttribute("href")) {
        const href = publicationBlock.getAttribute("href");
        const idArray = href.split("-");
        return idArray[idArray.length - 1];
    } else {
        return null;
    }
}

function getCardData(id, dataArray) {
    for (let i = 0; i < dataArray.length; i++) {
        const data = dataArray[i];
        if (data.id === id) {
            return data;
        }
    }
    return null;
}

function arraysJoin(array1, array2) {
    const a = array1.concat(array2);
    for (let i = 0; i < a.length; ++i) {
        for (let j = i + 1; j < a.length; ++j) {
            if (a[i] === a[j])
                a.splice(j--, 1);
        }
    }
    return a;
}


// Информер
function getData() {
    return this.data
}

async function addInformerBlock() {
    /*
   Предупреждения: 1
   Канал не ограничен / канал ограничен
   Канал индексируется / не индексируется
   Актуальность статистики: 01.01.21 01:01
   Публикации: A:234 V:100 G:100 P:25 L:10
 */
    if (!await getOption(OPTIONS.informer)) {
        return;
    }

    if (document.getElementById("prozen-informer")) {
        return;
    }

    const column = document.getElementsByClassName("author-studio-main__right-column")[0];
    const informer = createElement("div", "author-studio-block");
    informer.id = "prozen-informer";
    column.appendChild(informer);

    const karmaData = await getUserKarma();
    const hasNone = await checkHasNone(publisherId);
    const statsInfo = await getStatsInfo();
    const strikesInfo = await getStrikesInfo();

    const informerContent = createElement("div", "author-studio-useful-articles-block");
    informer.appendChild(informerContent);

    const informerH3 = createElement("h3", "Text Text_weight_bold Text_color_full Text_typography_text-16-20 author-studio-useful-articles-block__title");
    informerH3.innerText = "ПРОДЗЕН-инфо";
    informerH3.setAttribute("title", "Добавлено расширением ПРОДЗЕН");

    informerContent.appendChild(informerH3);

    if (karmaData != null) {
        const karma = createElement("span", "Text Text_color_full Text_typography_text-14-18 author-studio-article-card__title prozen-mb5");
        if (karmaData.karma && karmaData.karma.length > 0) {
            karma.innerText = `Карма: ${karmaData.karma[karmaData.karma.length - 1].values.finalScore}`;
        } else {
            karma.innerText = "Карма: 0";
        }
        if (karmaData.totalBonusShows) {
            karma.setAttribute("title", `Бонусные показы: ${numFormat(karmaData.totalBonusShows)}`);
        }
        informerContent.appendChild(karma);
    }

    if (strikesInfo.limitations != null) {
        const informerStrikes = createElement("span", "Text Text_color_full Text_typography_text-14-18 author-studio-article-card__title prozen-mb5");
        informerStrikes.innerText = `Предупреждения: ${strikesInfo.limitations}`
        informerStrikes.setAttribute("title", "Информация получена на основе данных раздела «Предупреждения»");
        informerContent.appendChild(informerStrikes);
    }

    if (strikesInfo.channelRestricted != null) {
        const informerPyos = createElement("span", "Text Text_color_full Text_typography_text-14-18 author-studio-article-card__title prozen-mb5");
        informerPyos.innerText = strikesInfo.channelRestricted ? "Канал ограничен" : "Канал не ограничен";
        informerPyos.setAttribute("title", "Информация получена на основе данных раздела «Предупреждения»");
        informerContent.appendChild(informerPyos);
    }

    if (hasNone != null) {
        const allNone = createElement("span", "Text Text_color_full Text_typography_text-14-18 author-studio-article-card__title prozen-mb5");
        if (hasNone) {
            allNone.innerText = "Канал не индексируется 🤖";
            allNone.setAttribute("title", "Обнаружен мета-тег <meta property=\"robots\" content=\"none\" />\n" +
                "Канал не индексируется поисковиками.\n" +
                "Это нормальная ситуация для новых каналов.");
        } else {
            allNone.innerText = "Канал индексируется";
        }
        informerContent.appendChild(allNone);
    }

    if (statsInfo.actuality != null) {
        const informerActuality = createElement("span", "Text Text_color_full Text_typography_text-14-18 author-studio-article-card__title prozen-mb5");
        informerActuality.innerText = `Статистика от ${statsInfo.actuality}`;
        informerActuality.setAttribute("title", "Время обновления статистики");
        informerContent.appendChild(informerActuality);
    }

    if (statsInfo.counters != null) {
        const publicationNames = {
            article: "статей",
            gif: "видео",
            gallery: "галерей",
            brief: "постов",
            live: "трансляций"
        };
        const informerCounters = createElement("span", "Text Text_color_full Text_typography_text-14-18 prozen-mb5 prozen-va");
        informerContent.appendChild(informerCounters);
        for (const [type, count] of Object.entries(statsInfo.counters)) {
            if (count != null) {
                const title = `Количество ${publicationNames[type]} на канале`;
                const icon = createElement("span", `prozen-publication-icon-${type}`);
                icon.setAttribute("title", title);
                informerCounters.appendChild(icon);
                const text = createElement("span", "prozen-ml5r8");
                text.setAttribute("title", title);
                text.innerText = count;
                informerCounters.appendChild(text);
            }
        }
    }
}

class Card {
    constructor(publicationData, publicationUrl) {
        this.title = publicationData.content.title;
        this.id = publicationData.id;
        this.publisherId = publicationData.publisherId;
        this.addTime = publicationData.addTime;
        this.modTime = publicationData.modTime;
        this.publishTime = publicationData.publishTime;
        this.feedShows = publicationData.privateData.statistics.feedShows;
        this.shows = publicationData.privateData.statistics.shows;
        this.views = publicationData.privateData.statistics.views;
        this.viewsTillEnd = publicationData.privateData.statistics.viewsTillEnd;
        this.sumViewTimeSec = publicationData.privateData.statistics.sumViewTimeSec;
        this.likes = publicationData.privateData.statistics.likes;
        this.comments = publicationData.privateData.statistics.comments;
        this.type = publicationData.content.type;
        this.tags = arraysJoin(publicationData.privateData.tags, publicationData.privateData.embeddedTags)

        // Текстовые представления данных
        // Время модификации
        this.dayMod = dateTimeFormat(this.modTime);
        this.dayCreate = this.addTime === undefined ? this.dayMod : dateTimeFormat(this.addTime);
        this.showTime = this.dayMod !== this.dayCreate ? this.dayCreate + " (" + this.dayMod + ")" : this.dayCreate;
        this.timeStr = this.showTime;

        // Показы
        this.feedShowStr = infiniteAndNanToStr(this.feedShows);

        // Просмотры (CTR%)
        this.ctr = (infiniteAndNan(this.shows / this.feedShows) * 100).toFixed(2);
        if (this.type === "brief") {
            this.ctr = (infiniteAndNan(this.views / this.feedShows) * 100).toFixed(2);
        }
        this.viewsStr = `${infiniteAndNanToStr(this.views)} (${this.ctr}%)`;

        // Дочитывания
        this.readsPercent = infiniteAndNan((this.viewsTillEnd / this.views) * 100).toFixed(2);
        this.viewsTillEndStr = `${infiniteAndNanToStr(this.viewsTillEnd)} (${this.readsPercent}%)`;

        // Среднее время дочитывания
        this.readTime = this.sumViewTimeSec / this.viewsTillEnd;
        this.readTimeStrHMS = secToHHMMSS(this.readTime);
        this.readTimeStr = this.readTime > 0 ? secToText(this.readTime) : "-";


        // Лайки (проценты)
        this.erViews = firstNotZ(this.viewsTillEnd, this.views, this.feedShows);
        this.likesEr = infiniteAndNan((this.likes / this.erViews) * 100);
        this.likesStr = this.likes === 0 ? "0 (0.00%)" : `${infiniteAndNanToStr(this.likes)} (${this.likesEr.toFixed(2)}%)`;

        // Комментарии (проценты)
        this.commentsEr = infiniteAndNan((this.comments / this.erViews) * 100);
        this.commentsStr = this.comments === 0 ? "0 (0.00%)" : `${infiniteAndNanToStr(this.comments)} (${this.commentsEr.toFixed(2)}%)`;

        // Коэффициент вовлечённости
        this.erStr = `${infiniteAndNan((((this.comments + this.likes) / this.erViews)) * 100).toFixed(2)}%`;

        // Теги
        this.tagsStr = joinByThree(this.tags);

        // Ссылка на статью (сокращённая)
        this.shortUrl = `https://zen.yandex.ru/media/id/${this.publisherId}/${this.id}`
        if (publicationUrl != null) {
            this.url = publicationUrl.startsWith("https://zen.yandex") ? publicationUrl : `https://zen.yandex.ru${publicationUrl}`;
            const publicationPath = publicationUrl.split("/");
            if (publicationPath[2] !== "id") {
                this.shortUrl = `https://zen.yandex.ru/media/${publicationPath[2]}/${this.id}`
            }
        } else {
            this.url = this.shortUrl;
        }
    }
}