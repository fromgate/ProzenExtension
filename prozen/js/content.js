/*
TODO
+ Добавить кнопку метрики
+ Добавить кнопку «Прямая ссылка» (без ?from=editor)
+ Отслеживать подгрузку компонентов и добавлять им всё что нужно
+ Выводить сумму с копейками
+ Вывод полного заработка в виде подсказки
+ Выводить всплывающее сообщение (по кнопке)


- Выводить статистику под каждой записью (как у Алексея)
- Выводить 1-2 записи с ПРОДЗЕНа во всплыващем сообщении
 */

const token = window._csrfToken;
const publisherId = getPublisherId();
const publications = new Map();
showBalanceAndMetrics();
loadCards();
processCards();
registerObserver();

/*****************************************
 * Functions
 *****************************************/

function getUnproccedPublications() {
    return Array.from(publications.keys()).filter(function (key) {
        return !publications.get(key).processed;
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
            card.viewsTillEnd = stat.viewsTillEnd;
            articles.push(loadArticle(id));
        }
        Promise.all(articles).then(function (articles) {
            for (let i in articles) {
                const article = articles[i];
                const id = article.publications[0].id;
                const card = publications.get(id);
                card.addTime = article.publications[0].addTime;
                card.modTime = article.publications[0].content.modTime;
            }
        }).then(function () {
            // TODO вот тут уже работа по отображению статистки записи




            for (let i = 0; i< ids.length; i++) {

                const publicationId = ids[i];
                const value = publications.get(publicationId);

                if (value.processed) {
                    continue;
                }

                if (value.card.hasChildNodes()) {
                    //publication-card-item__footer
                    //publication-card-item-statistic
                    //publication-card-item-statistic__main
                    const cardStat = value.card.getElementsByClassName("publication-card-item-statistic__main")[0];
                    removeChilds (cardStat);

                    const likes = value.card.getElementsByClassName("publication-card-item-statistic__likes")[0];
                    // removeChilds (likes);
                    addStats (cardStat, likes, value);


                    let actions = value.card.getElementsByClassName("publication-card-item__actions");
                    if (actions.length >0) {
                        addDirectLinkButton(actions[0]);
                    }
                }
                value.processed = true;
            }


        });
    });
}

/*
<div class="publication-card-item__footer">
    <div class="publication-card-item-statistic publication-card-item-statistic_loaded">
        <div class="publication-card-item-statistic__main">
            <div class="publication-card-item-statistic__main-item"><span
                    class="publication-card-item-statistic__main-count">55,9 тыс.</span><span
                    class="publication-card-item-statistic__main-text">показов в ленте</span>
            </div>
            <div class="publication-card-item-statistic__main-item"><span
                    class="publication-card-item-statistic__main-count">1,89 тыс.</span><span
                    class="publication-card-item-statistic__main-text">просмотров</span>
            </div>
            <div class="publication-card-item-statistic__main-item"><span
                    class="publication-card-item-statistic__main-count">1,45 тыс.</span><span
                    class="publication-card-item-statistic__main-text">дочитываний</span>
            </div>
        </div>
        <div class="publication-card-item-statistic__likes">
            <div class="publication-card-item-statistic__wrapper-item"><span
                    class="publication-card-item-statistic__icon publication-card-item-statistic__icon_type_like"></span><span
                    class="publication-card-item-statistic__count">35</span></div>
            <div class="publication-card-item-statistic__wrapper-item"><span
                    class="publication-card-item-statistic__icon publication-card-item-statistic__icon_type_time"></span>
                    <span
                    class="publication-card-item-statistic__count">1 мин 30 секунд</span></div>
        </div>
        <div class="article-stat-tip ">
            <div class="article-stat-tip__item"><span class="article-stat-tip__value">1,89 тыс. просмотров. </span>Уникальные
                посетители страницы.
            </div>
            <div class="article-stat-tip__item"><span
                    class="article-stat-tip__value">1,45 тыс. дочитываний, 77%. </span>Пользователи, дочитавшие до
                конца.
            </div>
            <div class="article-stat-tip__item"><span class="article-stat-tip__value">1 мин 30 секунд</span>. Среднее
                время дочитывания публикации.
            </div>
        </div>
    </div>
</div>

 */




function createItemLeft(count, text) {
    const item = document.createElement("div");
    item.setAttribute("class", "publication-card-item-statistic__main-item");
    const itemCount = document.createElement("span");
    itemCount.setAttribute("class", "publication-card-item-statistic__main-count");
    itemCount.innerText = isNaN (count) ? count : count.toLocaleString(undefined, { maximumFractionDigits: 0 });
    //.toLocaleString(undefined, { maximumFractionDigits: 2 })
    const itemText = document.createElement("span");
    itemText.setAttribute("class", "publication-card-item-statistic__main-text");
    itemText.innerText = text;
    item.appendChild(itemCount);
    item.appendChild(itemText);
    return item;
}


/*

+ addTime: 1538289724097
# card: div.publication-card-item.publication-card-item_type_image
comments: 11
+ feedShows: 61152
likes: 132
+ modTime: 1542466595690
# processed: true
+ shows: 2447
views: 2700
+ viewsTillEnd: 2207

*/
function addStats(leftSide, rightSide, pubData) {
    const dayCreate = dateFormat(pubData.addTime);
    const dayMod = dateFormat(pubData.modTime);
    const date = createItemLeft (dayCreate, dayCreate === dayMod ? "" : "("+dayMod+")");
    leftSide.appendChild(date);
    const shows = createItemLeft(pubData.feedShows, "показов");
    leftSide.appendChild(shows);
    const views = createItemLeft(pubData.views, "просмотров");
    leftSide.appendChild(views);
    const viewsTillEnd = createItemLeft(pubData.viewsTillEnd, "дочитываний");
    leftSide.appendChild (viewsTillEnd);
/*
    const likes = createItemRight(pubData.likes, "likes");
    rightSide.appendChild (likes); */
}


function createItemRight(count, text) {
    const item = document.createElement("div");
    item.setAttribute("class","publication-card-item-statistic__wrapper-item");
    const itemIcon = document.createElement("span");

    if (text === "likes") {
        itemIcon.setAttribute("class", "publication-card-item-statistic__icon publication-card-item-statistic__icon_type_like");
        itemIcon.innerText = "";
    } else if (text === "readTime") {
        itemIcon.setAttribute("class", "publication-card-item-statistic__icon publication-card-item-statistic__icon_type_time");
        itemIcon.innerText = "";
    } else {
        itemIcon.setAttribute("class", "publication-card-item-statistic__icon");
        itemIcon.innerText = text;
    }

    const itemCount = document.createElement("span");
    itemCount.setAttribute("class", "publication-card-item-statistic__count");
    itemCount.innerText = count;

    item.appendChild(itemIcon);
    item.appendChild(itemCount);
    return item;
}






function removeChilds(el) {
    while (el.firstChild) {
        el.removeChild(el.firstChild);
    }
}

/*
function addPostDate(snippet) {
    let dateSnippet = document.createElement("div");
    dateSnippet.setAttribute("class","card__snippet clamped-text-1");
    dateSnippet.innerHTML = "<small>01.01.2019 (01.01.2019)</small>"; ///////////////////////////////TODO
    snippet.insertAdjacentElement("afterend", dateSnippet);
}*/

function addDirectLinkButton(element) {
    const link = element.children[0];
    const linkUrl = "https://zen.yandex.ru" + link.getAttribute("href").replace("?from=editor", "");
    const directLink = document.createElement("a");
    directLink.setAttribute("href",linkUrl);
    directLink.setAttribute("class", "publication-card-item__action-button");
    directLink.innerText = "Прямая ссылка";
    link.insertAdjacentElement("afterend", directLink);
}

function getPostIdFromUrl(url) {
    ln = url.replace ("?from=editor","").split("-");
    return ln[ln.length-1];
}

function getPublisherId() {
    //https://forms.yandex.ru/surveys/6674/?znchnlid=<ID>
    return document.getElementsByClassName("help-button__link")[0].getAttribute("href").split("=")[1];
}

function showBalanceAndMetrics() {
    const url = "https://zen.yandex.ru/media-api/id/" + publisherId + "/money";
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
    if (money !== total) {
        const navBlock = document.getElementsByClassName("header__nav-money")[0];
        navBlock.setAttribute("data-tip", "Всего: " + total.toLocaleString(undefined, { maximumFractionDigits: 2 }) + " ₽");
    }
    const moneySpan = document.getElementsByClassName("radio-button__text")[1];
    moneySpan.innerText = money.toLocaleString(undefined, { maximumFractionDigits: 2 }) + " ₽";
}

function addMetricsButton(metricsId) {
    const metricsUrl = metricsId !== undefined ? "https://metrika.yandex.ru/dashboard?id=" + metricsId : "https://metrika.yandex.ru/list";
    const metricsButton = document.createElement("div");
    metricsButton.setAttribute("class","header__nav-block");
    metricsButton.setAttribute("data-multiline","true");
    metricsButton.setAttribute("data-tip","Яндекс.Метрика");
    metricsButton.setAttribute("currentitem","false");
    metricsButton.innerHTML = "<a aria-pressed=\"false\" tabindex=\"0\" aria-disabled=\"false\" target=\"_blank\" href=\""+metricsUrl+"\" class=\"control button2 button2_view_classic button2_size_m button2_theme_zen-header-tab button2_type_link\"><span class=\"button2__text\"><svg width=\"18\" height=\"16\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:svg=\"http://www.w3.org/2000/svg\"><g class=\"layer\"><title>Layer 1</title><g fill=\"none\" fill-rule=\"evenodd\" id=\"svg_1\"><g fill=\"#0077FF\" id=\"stats\" stroke=\"#0077FF\" transform=\"translate(1.000000, 1.000000)\"><polygon fill=\"#ff0000\" stroke=\"#ff0000\" id=\"svg_2\" points=\"0 7 4 7 4 14 0 14\"/><polygon fill=\"#4277ca\" stroke=\"#4277ca\" id=\"svg_3\" points=\"6 4 10 4 10 14 6 14\"/><polygon fill=\"#ffcc00\" stroke=\"#ffcc00\" id=\"svg_4\" points=\"12 0 16 0 16 14 12 14\"/></g></g></g></svg></span></a>";
    const navblocks = document.getElementsByClassName("header__nav-block");
    const last = navblocks.item(navblocks.length - 1);
    last.insertAdjacentElement("afterend", metricsButton);
}


function loadPublicationsStat(publicationIds) {
    const url="https://zen.yandex.ru/media-api/publisher-publications-stat?publicationsIds=" + encodeURIComponent(publicationIds.join(","));
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
}

function loadArticle(publicationId) {
    const url="https://zen.yandex.ru/media-api/get-publication?publicationId=" + publicationId;
    return fetch(url, {credentials: 'same-origin', headers: {'X-Csrf-Token': token}}).then(response => response.json());
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
    const month = "0" + date.getMonth();
    const year = "" +date.getFullYear();
    const hours = "0" + date.getHours();
    const minutes = "0" + date.getMinutes();
    return day.substr(-2) + "." + month.substr(-2) + "."
        +year.substr(-2) + " "+hours.substr(-2)+":"+minutes.substr(-2) ;

}