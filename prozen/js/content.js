start();

const URL_ZEN = "https://zen.yandex.ru";
const URL_API_PUBLICATIONS = "https://zen.yandex.ru/media-api/publisher-publications-stat?publicationsIds=";
const URL_API_MEDIA = "https://zen.yandex.ru/media-api/id/";
const URL_API_GET_PUBLICATION = "https://zen.yandex.ru/media-api/get-publication?publicationId=";

const publications = new Map();
var token;
var publisherId;

function main() {
    publisherId = getPublisherId();
    if (token === undefined || publisherId === undefined) {
        return;
    }

    const pageType = getPageType();

    if (pageType === "unknown") {
        return;
    }

    if (pageType !== "edit") {
        showBalanceAndMetrics ();
    }

    if (pageType === "main") {
        loadCards();
        processCards();
        registerObserver();
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

    const css = document.createElement("link");
    css.setAttribute("rel", "stylesheet");
    css.setAttribute("type", "text/css");
    css.setAttribute("href", browser.extension.getURL("css/icons.css"));
    document.head.appendChild(css);

    const script = document.createElement("script");
    script.setAttribute("type","text/javascript");
    script.setAttribute("src", browser.extension.getURL("js/page.js"));
    document.body.appendChild(script);

    window.addEventListener("message", function(event) {
        if (event.source !== window)
            return;
        if (event.data.type && (event.data.type === "prozen-data")) {
            token = event.data.text;
            main ();
        }
    });
}

function registerObserver() {
    const target = document.getElementsByClassName('publications-cards-view')[0];
    const observer = new MutationObserver(function(mutations) {
        mutations.forEach(function(mutation) {
            if (mutation.type === 'childList') {
                loadCards();
                processCards();
            }
        });
    });
    const config = {
        attributes: false,
        childList: true,
        characterData: false
    };
    observer.observe(target, config);
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
    });
}

function setBalance(money, total) {
    const moneyA = document.getElementsByClassName("header-menu__link")[1];
    if (money !== total) {
        moneyA.setAttribute("data-tip", moneyA.getAttribute("data-tip") + " / Всего: " + total.toLocaleString(undefined, { maximumFractionDigits: 2 }) + " ₽");
    }
    moneyA.innerText = money.toLocaleString(undefined, { maximumFractionDigits: 2 }) + " ₽";
}

function addMetricsButton(metricsId) {
    const metricsUrl = metricsId !== undefined ? "https://metrika.yandex.ru/dashboard?id=" + metricsId : "https://metrika.yandex.ru/list";
    const metricsButton = document.createElement("div");
    metricsButton.setAttribute("class","header__nav-block");
    metricsButton.setAttribute("data-multiline","true");
    metricsButton.setAttribute("data-tip","Яндекс.Метрика");
    metricsButton.setAttribute("currentitem","false");
    const metricsA = document.createElement("a");
    metricsA.setAttribute("aria-pressed","false");
    metricsA.setAttribute("tabindex","0");
    metricsA.setAttribute("aria-disabled","false");
    metricsA.setAttribute("target","_blank");
    metricsA.setAttribute("class","control button2 button2_view_classic button2_size_m button2_theme_zen-header-tab button2_type_link");
    metricsA.setAttribute("href",metricsUrl);
    const aSpan = document.createElement("span");
    aSpan.setAttribute("class","button2__text");
    metricsA.appendChild(aSpan);
    const img = document.createElement("img");
    img.setAttribute("src","data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTgiIGhlaWdodD0iMTYiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgeG1sbnM6c3ZnPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgICA8ZyBjbGFzcz0ibGF5ZXIiPgogICAgICAgIDx0aXRsZT5MYXllciAxPC90aXRsZT4KICAgICAgICA8ZyBmaWxsPSJub25lIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiIGlkPSJzdmdfMSI+CiAgICAgICAgICAgIDxnIGZpbGw9IiMwMDc3RkYiIGlkPSJzdGF0cyIgc3Ryb2tlPSIjMDA3N0ZGIgogICAgICAgICAgICAgICB0cmFuc2Zvcm09InRyYW5zbGF0ZSgxLjAwMDAwMCwgMS4wMDAwMDApIj4KICAgICAgICAgICAgICAgIDxwb2x5Z29uIGZpbGw9IiNmZjAwMDAiCiAgICAgICAgICAgICAgICAgICAgICAgICBzdHJva2U9IiNmZjAwMDAiCiAgICAgICAgICAgICAgICAgICAgICAgICBpZD0ic3ZnXzIiCiAgICAgICAgICAgICAgICAgICAgICAgICBwb2ludHM9IjAgNyA0IDcgNCAxNCAwIDE0Ii8+CiAgICAgICAgICAgICAgICA8cG9seWdvbgogICAgICAgICAgICAgICAgICAgICAgICBmaWxsPSIjNDI3N2NhIiBzdHJva2U9IiM0Mjc3Y2EiIGlkPSJzdmdfMyIgcG9pbnRzPSI2IDQgMTAgNCAxMCAxNCA2IDE0Ii8+CiAgICAgICAgICAgICAgICA8cG9seWdvbiBmaWxsPSIjZmZjYzAwIgogICAgICAgICAgICAgICAgICAgICAgICAgc3Ryb2tlPSIjZmZjYzAwIgogICAgICAgICAgICAgICAgICAgICAgICAgaWQ9InN2Z180IgogICAgICAgICA\n" +
        "gICAgICAgICAgICAgICAgcG9pbnRzPSIxMiAwIDE2IDAgMTYgMTQgMTIgMTQiLz4KICAgICAgICAgICAgPC9nPgogICAgICAgIDwvZz4KICAgIDwvZz4KPC9zdmc+");
    aSpan.appendChild(img);
    metricsButton.appendChild(metricsA);
    const navblocks = document.getElementsByClassName("header__nav-block");
    const last = navblocks.item(navblocks.length - 1);
    last.insertAdjacentElement("afterend", metricsButton);
}


function loadCards() {
    const cards = document.getElementsByClassName("publication-card-item");
    for (i = 0; i < cards.length; i++) {
        const card = cards[i];
        const postLink = card.getElementsByClassName("card__card-link")[0].getAttribute("href");
        if (postLink.startsWith("/profile/editor/id/")) {
            continue;
        }
        const publicationId = getPostIdFromUrl(postLink);
        if (publications.has(publicationId)) {
            continue;
        }
        publications.set(publicationId, {});
        publications.get(publicationId).card = card;
        publications.get(publicationId).processed = false;
    }
}

function getUnproccedPublications() {
    return Array.from(publications.keys()).filter(function (key) {
        return !publications.get(key).processed;
    });
}

function loadPublicationsStat(publicationIds) {
    const url=URL_API_PUBLICATIONS + encodeURIComponent(publicationIds.join(","));
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}

function loadArticle(publicationId) {
    const url=URL_API_GET_PUBLICATION + publicationId;
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}

function processCards() {
    const ids = getUnproccedPublications();
    if (ids.length === 0) {
        return;
    }
    loadPublicationsStat(ids).then (function (data) {
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
            }
        }).then(function () {
            for (let i = 0; i< ids.length; i++) {
                const publicationId = ids[i];
                const value = publications.get(publicationId);
                if (value.processed) {
                    continue;
                }
                if (value.card.hasChildNodes()) {
                    const cartLeft = value.card.getElementsByClassName("publication-card-item-statistic__main")[0];
                    removeChilds (cartLeft);
                    const cardRight= value.card.getElementsByClassName("publication-card-item-statistic__likes")[0];
                    removeChilds (cardRight);
                    removeByClass  ("article-stat-tip");
                    addStats (cartLeft, cardRight, value);
                    let actions = value.card.getElementsByClassName("action-menu__action-button");
                    if (actions.length >0) {
                        addDirectLinkButton(actions[0]);
                    }
                }
                value.processed = true;
            }
        });
    });
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
    const linkUrl = URL_ZEN + link.getAttribute("href").replace("?from=editor", "");
    const directLink = document.createElement("a");
    directLink.setAttribute("href",linkUrl);
    directLink.setAttribute("class", "action-menu__action-button");
    directLink.innerText = "Прямая ссылка";
    link.insertAdjacentElement("afterend", directLink);
}

function addStats(leftSide, rightSide, pubData) {
    const shows = createLeftItem(pubData.feedShows, "icon_shows_in_feed", "Показы");
    leftSide.appendChild(shows);
    const ctr = (parseFloat(infiniteAndNan(pubData.views / pubData.feedShows)*100)).toFixed(2);
    const views = createLeftItem(pubData.views, "icon_views", "Просмотры (CTR)", " ("+ctr +"%)");
    leftSide.appendChild(views);
    const readsPercent = ((pubData.viewsTillEnd / pubData.views)*100).toFixed(2);
    const viewsTillEnd = createLeftItem(pubData.viewsTillEnd, "icon_views_till_end", "Дочитывания", " (" +parseFloat(infiniteAndNan (readsPercent)).toFixed(2) +"%)");
    leftSide.appendChild (viewsTillEnd);
    const dayMod = dateFormat(pubData.modTime);
    const dayCreate = pubData.addTime === undefined ? dayMod : dateFormat(pubData.addTime);
    const date = createLeftItem (dayCreate, "icon_calendar","Дата создания"+ (dayCreate === dayMod ? "" : " (и редактрования)"), dayCreate === dayMod ? "" : " ("+dayMod+")");
    leftSide.appendChild(date);

    const erViews = firstNotZ (pubData.viewsTillEnd, pubData.views, pubData.feedShows);
    const likesEr = infiniteAndNan((pubData.likes / erViews)*100);
    const likesValue = pubData.likes === 0 ? "0 (0.00%)" : pubData.likes + " (" + parseFloat (likesEr).toFixed(2) + "%)";
    const likes = createRightItem(likesValue, "icon_like", "Лайки (в процентах)");
    rightSide.appendChild (likes);

    const commentsEr = infiniteAndNan((pubData.comments / erViews)*100);
    const commentsValue = pubData.comments === 0 ? "0 (0.00%)" : pubData.comments + " (" + parseFloat (commentsEr).toFixed(2) + "%)";
    const comments = createRightItem(commentsValue, "icon_comments", pubData.comments === 0 ? "Комментарии. Полковнику никто не пишет?" : "Комментарии (в процентах)");
    rightSide.appendChild (comments);

    const erValue = infiniteAndNan((((pubData.comments + pubData.likes) / erViews))*100).toFixed(2) + "%";
    const er = createRightItem(erValue, "icon_er", "Коэффициент вовлеченности, ER");
    rightSide.appendChild (er);

    const readTime = {};
    readTime.count = secToHHMMSS (pubData.readTime);
    readTime.text = "icon_clock";
    readTime.title = "Время дочитывания" +(pubData.readTime > 0 ? " - " + secToText(pubData.readTime) : "") + "&nbsp;";

    const tag = {};
    tag.count = "";
    tag.text = "icon_tags";
    tag.title = pubData.tags.length === 0 ? "Теги не указаны" : "Теги: " + joinByThree(pubData.tags);
    const timeAndTag = createRightItems ([readTime, tag]);
    rightSide.appendChild (timeAndTag);
}

function createLeftItem(count, text, title, postText) {
    const item = document.createElement("div");
    item.setAttribute("class", "publication-card-item-statistic__main-item");

    const itemCount = document.createElement("span");
    itemCount.setAttribute("class", "publication-card-item-statistic__main-count");
    itemCount.innerText = isNaN(count) ? count : count.toLocaleString(undefined, {maximumFractionDigits: 0});

    const itemText = document.createElement("span");

    switch (text) {
        case "icon_calendar":
        case "icon_shows_in_feed":
        case "icon_views":
        case "icon_views_till_end":
            itemText.setAttribute("class", "publication-card-item-statistic__icon " + text);
            item.appendChild(itemText);
            item.appendChild(itemCount);
            if (postText !== undefined && postText !== "") {
                const itemPost = document.createElement("span");
                itemPost.setAttribute("class", "publication-card-item-statistic__main-text");
                itemPost.innerText = postText;
                item.appendChild(itemPost);
            }
            break;
        default:
            itemText.setAttribute("class", "publication-card-item-statistic__main-text");
            itemText.innerText = text;
            item.appendChild(itemCount);
            item.appendChild(itemText);
            break;
    }
    if (title !== undefined && title !== "") {
        item.setAttribute("title", title);
    }
    return item;
}

function createRightItemElement(count, text) {
    const itemCount = document.createElement("span");
    itemCount.setAttribute("class", "publication-card-item-statistic__count");
    itemCount.innerText = count;
    const itemIcon = document.createElement("span");
    let items = [];
    switch (text) {
        case "icon_like":
        case "icon_clock":
        case "icon_comments":
        case "icon_er":
        case "icon_tags":
            itemIcon.setAttribute("class", "publication-card-item-statistic__icon " + text);
            itemIcon.innerText = "";
            items[0] = itemIcon;
            items[1] = itemCount;
            break;
        default:
            itemIcon.setAttribute("class", "publication-card-item-statistic__icon");
            itemIcon.innerText = text;
            items[0] = itemCount;
            items[1] = itemIcon;
            break;
    }
    return items;
}

function createRightItems(items) {
    const item = document.createElement("div");
    item.setAttribute("class","publication-card-item-statistic__wrapper-item");
    for (let i = 0; i<items.length;i ++) {
        const title = items[i].title;
        const elements = createRightItemElement(items[i].count, items[i].text);
        for (let j = 0; j<elements.length;j ++) {
            item.appendChild(elements[j]);
            if (title !== undefined && title !== "") {
                elements[j].setAttribute("title", title);
            }
        }
    }
    return item;
}

function createRightItem(count, text, title) {
    const item = document.createElement("div");
    item.setAttribute("class","publication-card-item-statistic__wrapper-item");
    const items = createRightItemElement (count, text);
    for (let i = 0; i<items.length;i ++) {
        item.appendChild(items[i]);
    }
    if (title !== undefined && title !== "") {
        item.setAttribute("title", title);
    }
    return item;
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
    const notification = document.createElement("div");
    notification.setAttribute("class", "notifications notifications_num_"+num);
    const link = document.createElement("a");
    link.setAttribute("href", message.href);
    link.setAttribute("target","_blank");
    link.setAttribute("class", "notification-item");
    link.setAttribute("style", "");

    const container = document.createElement("div");
    container.setAttribute("class","notification-item__container");
    const icon = document.createElement("div");
    icon.setAttribute("class", "notification-item__icon");
    container.appendChild(icon);
    const title = document.createElement("span");
    title.setAttribute("class","notification-item__title");
    title.innerText = message.title;
    container.appendChild(title);

    const text = document.createElement("span");
    text.setAttribute("class","notification-item__text");
    text.innerText = message.text+" ";
    container.appendChild(text);

    const linkStr = document.createElement("span");
    linkStr.setAttribute("class","notification-item__link");
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
        +year.substr(-2) + " "+hours.substr(-2)+":"+minutes.substr(-2) ;
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
    return (hours > 0 ? hours +" час. " : "") + min +" мин. "+sec+ " сек.";
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