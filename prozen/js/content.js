start();

const URL_API_PUBLICATIONS = "https://zen.yandex.ru/media-api/publisher-publications-stat?publicationsIds=";
const URL_ZEN_ID = "https://zen.yandex.ru/id/";
const URL_API_MEDIA = "https://zen.yandex.ru/media-api/id/";
const URL_API_GET_PUBLICATION = "https://zen.yandex.ru/media-api/get-publication?publicationId=";
const URL_API_PUBLICATION_VIEW_STAT = "https://zen.yandex.ru/media-api/publication-view-stat?publicationId=";

const publications = new Map();
let token;
let data;
let publisherId;

function main() {
    const pageType = getPageType();
    if (pageType === "unknown") {
        return;
    }
    if (pageType === "article") {
        articleShowStats();
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
            data = event.data.jsonData;
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

            const wrapper2 = document.createElement("div");
            wrapper2.setAttribute("class", "article-stat__counts-wrapper");

            const spanIcon2 = document.createElement("span");
            spanIcon2.setAttribute("class","article-stat__icon article-stat__icon_type_perusal-black");
            const spanCount2 = document.createElement("span");
            spanCount2.setAttribute("class","article-stat__count");
            wrapper2.appendChild(spanIcon2);
            wrapper2.appendChild(spanCount2);
            wrapper1.insertAdjacentElement("afterend", wrapper2);

            const wrapper3 = document.createElement("div");
            wrapper3.setAttribute("class", "article-stat__counts-wrapper");
            const spanIcon3 = document.createElement("span");
            spanIcon3.setAttribute("class","article-stat__icon article-stat__icon_type_time-black");
            const spanCount3 = document.createElement("span");
            spanCount3.setAttribute("class","article-stat__count");
            wrapper3.appendChild(spanIcon3);
            wrapper3.appendChild(spanCount3);
            wrapper2.insertAdjacentElement("afterend", wrapper3);
            counters = document.getElementsByClassName("article-stat__count");
        }
        counters[1].innerText = viewsTillEnd.toLocaleString(undefined, {maximumFractionDigits: 0}) + " ("+infiniteAndNan(viewsTillEnd/views*100).toFixed(2)+"%)";
        counters[2].innerText = secToText(infiniteAndNan(sumViewTimeSec / viewsTillEnd));
        removeByClass("article-stat-tip");
        if (checkNoIndex()) {
            const wrapper4 = document.createElement("div");
            wrapper4.setAttribute("class", "article-stat__counts-wrapper");
            const spanIcon4 = document.createElement("span");
            spanIcon4.setAttribute("class","article-stat__icon icon_sad_robot");
            wrapper4.appendChild(spanIcon4);
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
        const cardLinks = card.getElementsByClassName("card__card-link");
        if (cardLinks === undefined || cardLinks.length === 0) {
            continue;
        }
        const postLink = cardLinks[0].getAttribute("href");
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

function loadPublicationStat(publicationId) {
    const url=URL_API_PUBLICATION_VIEW_STAT + encodeURIComponent(publicationId);
    return fetch(url, {credentials: 'same-origin'}).then(response => response.json());
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
    const linkUrl = link.getAttribute("href").replace("?from=editor", "");
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
    readTime.title = "Время дочитывания" +(pubData.readTime > 0 ? " - " + secToText(pubData.readTime) : "");

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
                console.log(metas[i].getAttribute('property') + " : " + metas[i].getAttribute('content'));
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
    const sadRobotIcon = document.createElement("span");
    sadRobotIcon.setAttribute("class","header__readers-count");
    const img = document.createElement("img");
    img.setAttribute("src", "data:image/svg+xml,%3C%3Fxml version='1.0' encoding='UTF-8' standalone='no'%3F%3E%3Csvg width='20px' height='20px' viewBox='0 0 20 20' version='1.1' xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink'%3E%3Ctitle%3Esad%3C/title%3E%3Cdefs%3E%3C/defs%3E%3Cg id='Page-1' stroke='none' stroke-width='1' fill='none' fill-rule='evenodd'%3E%3Cg id='Dribbble-Light-Preview' transform='translate(-100.000000, -6239.000000)' fill='%23ea7272'%3E%3Cg id='icons' transform='translate(56.000000, 160.000000)'%3E%3Cpath d='M56,6086 C56,6085.448 56.448,6085 57,6085 L59,6085 C59.552,6085 60,6085.448 60,6086 L60,6088 C60,6088.55 59.55,6089 59,6089 L57,6089 C56.45,6089 56,6088.55 56,6088 L56,6086 Z M52,6088 C52,6088.552 51.552,6089 51,6089 L49,6089 C48.448,6089 48,6088.552 48,6088 L48,6086 C48,6085.448 48.448,6085 49,6085 L51,6085 C51.552,6085 52,6085.448 52,6086 L52,6088 Z M60,6092.689 L60,6093.585 C60,6094.137 59.552,6094.611 59,6094.611 L59,6094.637 C58.448,6094.637 58,6094.241 58,6093.689 L58,6093.792 C58,6093.24 57.552,6093 57,6093 L51,6093 C50.448,6093 50,6093.24 50,6093.792 L50,6093.585 C50,6094.137 49.552,6094.585 49,6094.585 C48.448,6094.585 48,6094.137 48,6093.585 L48,6092.689 C48,6091.584 48.896,6090.997 50,6090.997 L50,6091 L58,6091 C59,6091 60,6091.584 60,6092.689 L60,6092.689 Z M62,6096 C62,6096.552 61.552,6097 61,6097 L47,6097 C46.448,6097 46,6096.552 46,6096 L46,6082 C46,6081.448 46.448,6081 47,6081 L61,6081 C61.552,6081 62,6081.448 62,6082 L62,6096 Z M64,6081 C64,6079.895 63.105,6079 62,6079 L46,6079 C44.895,6079 44,6079.895 44,6081 L44,6097 C44,6098.105 44.895,6099 46,6099 L62,6099 C63.105,6099 64,6098.105 64,6097 L64,6081 Z' id='sad'%3E%3C/path%3E%3C/g%3E%3C/g%3E%3C/g%3E%3C/svg%3E");
    img.setAttribute("title", "Обнаружен мета-тег <meta property=\"robots\" content=\"all\" />\n" +
        "Канал не индексируется поисковиками.\n" +
        "Примечание: связь этого тега с показами,\n" +
        "пессимизацией и иными ограничениями канала\n" +
        "официально не подтверждена.");
    sadRobotIcon.appendChild(img);
    const navblocks = document.getElementsByClassName("header__readers-count");
    const last = navblocks.item(navblocks.length - 1);
    last.insertAdjacentElement("afterend", sadRobotIcon);
}