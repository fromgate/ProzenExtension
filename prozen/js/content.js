let observer;
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
                hideComments();
                addStudioMenu();
                registerObserverWindowsLocation();
                registerObserverBalance();
                listenToRequests();
                addInformerBlock();
            }
            break;
        case "publications":
        case "money":
        case "karma":
        case "music":
        case "stats":
        case "campaigns":
            if (token != null && publisherId != null) {
                addStudioMenu();
                registerObserverWindowsLocation();
            }
            break;
        case "unknown":
            break;
    }
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
        if (path.endsWith("/campaigns")) {
            return "campaigns";
        }
        return "main";
    }
    return "unknown";
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

    for (const e of target.querySelectorAll("li.author-studio-info-block__stat-item")) {
        const name = e.querySelector("div.author-studio-info-item__stat-item-name").textContent;
        if (name === "баланс") {
            updateStudioBalance(e.childNodes[0]);
            return;
        }
    }

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

    const fullSize = document.documentElement.clientHeight > 626;
    const addition = fullSize ? "" : "\nДобавлено расширением продзен";

    if (oldStudioMenu == null) {
        const navbars = document.getElementsByClassName("navbar__nav-list");
        const prozenMenu = createElement("ul", "navbar__nav-list prozen_navbar");
        prozenMenu.id = "prozen-main-menu";
        prozenMenu.setAttribute("data-publisherId", publisherId);
        if (fullSize) {
            prozenMenu.appendChild(creatProzenMenuElement("Дополнительно", null, null, "Добавлено расширением ПРОДЗЕН", true));
        }
        prozenMenu.appendChild(creatProzenMenuElement("Полная статистика", "prozen_menu_stats", clickTotalStatsButton, "Сводная статистика" + addition));
        const metriksUrl = metriksId !== undefined && metriksId !== null ? "https://metrika.yandex.ru/dashboard?id=" + metriksId : "https://metrika.yandex.ru/list";
        prozenMenu.appendChild(creatProzenMenuElement("Метрика", "prozen_menu_metrika", metriksUrl, "Просмотр статистики в Яндекс.Метрике" + addition));
        prozenMenu.appendChild(creatProzenMenuElement("Поиск", "prozen_menu_search", clickSearchButton, "Альтернативная функция поиска" + addition));
        prozenMenu.appendChild(creatProzenMenuElement("Проверка noindex", "prozen_menu_robot", clickFindSadRobots, "Поиск публикаций с мета-тегом robots" + addition));
        if (fullSize) {
            prozenMenu.appendChild(creatProzenMenuElement("Служба поддержки", "prozen_support_mail", openUrlNewTab.bind(null, "https://yandex.ru/support/zen/troubleshooting/feedback.html"), "Обратиться в службу поддержки Яндекс.Дзена"));
        }
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

    const menuText = createElement("span", "Text Text_color_full Text_typography_text-14-18 navbar__text");
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
        const pageType = getPageType();
        if (pageType === "main") {
            processDashboardCards(request.pageSize);
        } else if (pageType === "publications") {
            processPublicationsCards(request);
        }
    }
}

async function processPublicationsCards(request) {
    const data = await getPublicationsByFilterAndSubscribers (request.pageSize, request.types, request.publicationIdAfter, request.query);
    if (isPublicationGrid()) {
        modifyPublicationGrid(data.publications);
    } else {
        modifyPublicationTable(data.publications);
    }
}

function getPublicationCellById(publicationId) {
    const table = document.querySelector("table.publications-list");
    const a = table.querySelector(`a.publication-preview[href*='${publicationId}'`);
    return a != null ? a.parentNode : null;
}

function getPublicationGridCellById(publicationId) {
    const a = document.querySelector(`a.publication-card__link-2q[href*='${publicationId}'`);
    return a != null ? a.parentNode : null;
}

function modifyPublicationsGridCell(cell, card) {
    if (cell.hasAttribute("data-prozen-publication-id")) {
        return;
    }
    cell.setAttribute("data-prozen-publication-id", card.id);
    const date = cell.querySelector("span.Text_typography_text-12-16");
    date.innerText = card.timeStr + " · ";
    modifyGridCellStats(cell, card);
}


function modifyPublicationsCell(cell, card) {
    if (cell.hasAttribute("data-prozen-publication-id")) {
        return;
    }
    cell.setAttribute("data-prozen-publication-id", card.id);
    const snippet = cell.querySelector("p.publication-preview__snippet");
    if (snippet != null && snippet.style != null && !["post", "gallery"].includes(card.type)) {
        snippet.style.setProperty("-webkit-line-clamp", "2");
    }

    const timeCell = cell.parentNode.cells[1];
    timeCell.querySelector("span").innerText = card.timeStr;

    const previewContainer = cell.querySelector("div.publication-preview__preview-container");
    //const publicationItemStats = createElement("div", "author-studio-publication-item__stats");
    const publicationItemStats = createElement("div", "prozen-card-container");

    previewContainer.appendChild(publicationItemStats);
    modifyPublicationsCard(publicationItemStats, card);
}

function modifyPublicationTable(requestData) {
    if (isPublicationGrid()) {
        return;
    }
    const waitList = []
    for (let i = 0; i < requestData.length; i++) {
        const publicationData = requestData[i];
        const cell = getPublicationCellById(publicationData.id);
        if (cell == null) {
            if (!["post", "narrative", "story"].includes(publicationData.content.type)) {
                waitList.push(publicationData);
            }
        } else {
            const card = jsonToCardData(publicationData, cell.querySelector("a.publication-preview").href);
            modifyPublicationsCell(cell, card);
            /*
            if (card.subscribersViews == null || card.subscribersViews === 0) {
                setTimeout(fillupPublicationsCell.bind(null, cell, card), 1);
            } */
        }
    }
    if (waitList.length > 0) {
        setTimeout(modifyPublicationTable.bind(null, waitList), 300);
    }
}


function modifyPublicationGrid(requestData) {
    if (!isPublicationGrid()) {
        return;
    }
    const waitList = []
    for (let i = 0; i < requestData.length; i++) {
        const publicationData = requestData[i];
        const cell = getPublicationGridCellById(publicationData.id);
        if (cell == null) {
            if (!["post", "narrative", "story"].includes(publicationData.content.type)) {
                waitList.push(publicationData);
            }
        } else {
            const card = jsonToCardData(publicationData, cell.querySelector("a.publication-card__link-2q").href);
            modifyPublicationsGridCell(cell, card);
        }
    }
    if (waitList.length > 0) {
        setTimeout(modifyPublicationGrid.bind(null, waitList), 300);
    }


}

async function processDashboardCards(pageSize) {
    const data = await getPublicationsByFilterAndSubscribers(pageSize);
    const studioPublicationsBlock = document.getElementsByClassName("author-studio-publications-block")[0];
    const publicationsBlocks = studioPublicationsBlock.getElementsByClassName("author-studio-publication-item");
    if (publicationsBlocks.length > 0) {
        for (let i = 0; i < publicationsBlocks.length; i++) {
            const publicationBlock = publicationsBlocks.item(i);
            const publicationId = getPublicationBlockId(publicationBlock);
            const publicationUrl = getPublicationBlockUrl(publicationBlock);
            if (publicationId != null) {
                const publicationData = getCardData(publicationId, data.publications);
                if (publicationData != null) {
                    const card = jsonToCardData(publicationData, publicationUrl);
                    modifyDashboardCard(publicationBlock, card);
                }
            }
        }
    }
}

/*
   Возвращает true, если странице публикаций включён режим отображения
   карточек в виде стеки. false — если в виде таблицы.
 */
function isPublicationGrid() {
    const div = document.querySelector("table.publications-list");
    return div == null;
}


function modifyGridCellStats(cell, card) {
    // Показы                |            Лайки
    // Просмотры (ctr)       |      Комментарии
    // Дочитывания           |    Вовлечённость
    // Просмотры подписчиков |    Время дочитывания /Ссылка / Теги

    const statsBlock = cell.querySelector("div.stats__block-gu");
    removeChilds(statsBlock);

    // Первый ряд
    const col1 = createElement("div", "stats__item-HO");
    statsBlock.appendChild(col1);

    // Показы
    const c1r1 = createElement("span", "Text Text_color_full Text_typography_text-12-16");
    c1r1.setAttribute("title", "Показы");
    const c1r1Icon = createElement("span", "prozen_studio_card_icon_shows");
    const c1r1Text = createElement("span");
    c1r1Text.innerText = card.feedShowStr;
    c1r1.appendChild(c1r1Icon);
    c1r1.appendChild(c1r1Text);
    col1.appendChild(c1r1);
    c1r1.style.zIndex = "14";
    c1r1.style.cursor = "default";

    // Лайки
    const c1r2 = createElement("span", "Text Text_color_full Text_typography_text-12-16");
    c1r2.setAttribute("title", "Лайки (%)");
    const c1r2Icon = createElement("span", "prozen_studio_card_icon_like");
    const c1r2Text = createElement("span");
    c1r2Text.innerText = card.likesStr;
    c1r2.appendChild(c1r2Icon);
    c1r2.appendChild(c1r2Text);
    col1.appendChild(c1r2);
    c1r2.style.zIndex = "14";
    c1r2.style.cursor = "default";

    // Второй ряд
    const col2 = createElement("div", "stats__item-HO");
    statsBlock.appendChild(col2);

    // Просмотры
    const c2r1 = createElement("span", "Text Text_color_full Text_typography_text-12-16");
    c2r1.setAttribute("title", "Просмотры (CTR)");
    const c2r1Icon = createElement("span", "prozen_studio_card_icon_views");
    const c2r1Text = createElement("span");
    c2r1Text.innerText = card.viewsStr;
    c2r1.appendChild(c2r1Icon);
    c2r1.appendChild(c2r1Text);
    col2.appendChild(c2r1);
    c2r1.style.zIndex = "14";
    c2r1.style.cursor = "default";

    // Комментарии
    const c2r2 = createElement("span", "Text Text_color_full Text_typography_text-12-16");
    c2r2.setAttribute("title", "Комментарии");
    const c2r2Icon = createElement("span", "prozen_studio_card_icon_comments");
    const c2r2Text = createElement("span");
    c2r2Text.innerText = card.commentsStr;
    c2r2.appendChild(c2r2Icon);
    c2r2.appendChild(c2r2Text);
    col2.appendChild(c2r2);
    c2r2.style.zIndex = "14";
    c2r2.style.cursor = "default";

    // Третий ряд
    const col3 = createElement("div", "stats__item-HO");
    statsBlock.appendChild(col3);

    // Дочитывания
    const c3r1 = createElement("span", "Text Text_color_full Text_typography_text-12-16");
    c3r1.setAttribute("title", "Дочитывания");
    const c3r1Icon = createElement("span", "prozen_studio_card_icon_full_views");
    const c3r1Text = createElement("span");
    c3r1Text.innerText = card.viewsTillEndStr;
    c3r1.appendChild(c3r1Icon);
    c3r1.appendChild(c3r1Text);
    col3.appendChild(c3r1);
    c3r1.style.zIndex = "14";
    c3r1.style.cursor = "default";

    // Вовлечённость
    const c3r2 = createElement("span", "Text Text_color_full Text_typography_text-12-16");
    c3r2.setAttribute("title", "Коэффициент вовлечённости");
    const c3r2Icon = createElement("span", "prozen_studio_card_icon_er");
    const c3r2Text = createElement("span");
    c3r2Text.innerText = card.erStr;
    c3r2.appendChild(c3r2Icon);
    c3r2.appendChild(c3r2Text);
    col3.appendChild(c3r2);
    c3r2.style.zIndex = "14";
    c3r2.style.cursor = "default";

    // Четвёртый ряд
    const col4 = createElement("div", "stats__item-HO");
    statsBlock.appendChild(col4);

    // Просмотры подписчиков
    const c4r1 = createElement("span", "Text Text_color_full Text_typography_text-12-16");
    const c4r1Icon = createElement("span", "prozen_studio_card_icon_subscribers");
    const c4r1Text = createElement("span", "prozen-subscribers-views");
    c4r1Text.innerText = card.getSubscribersViews();
    c4r1.setAttribute("title", card.getSubscribersViewsHint());
    c4r1.appendChild(c4r1Icon);
    c4r1.appendChild(c4r1Text);
    col4.appendChild(c4r1);
    c4r1.style.zIndex = "14";
    c4r1.style.cursor = "default";

    col4.appendChild(c4r1);
    // Ссылка / Теги
    const c4r2 = createElement("span", "Text Text_color_full Text_typography_text-12-16");

    const c4r2TimeIcon = createElement("span", "prozen_studio_card_icon_clock");
    c4r2TimeIcon.setAttribute("title", "Среднее время просмотра: " + card.readTimeStr);
    const c4r2TimeText = createElement("span");
    c4r2TimeText.setAttribute("title", "Среднее время просмотра: " + card.readTimeStr);
    c4r2TimeText.innerText = card.readTimeStrHMS;
    c4r2TimeText.style.marginRight = "7px";
    c4r2.appendChild(c4r2TimeIcon);
    c4r2.appendChild(c4r2TimeText);


    const c4r2Link = createElement("span", "prozen_studio_card_icon_link");
    c4r2Link.setAttribute("title", "Короткая ссылка.\nНажмите, чтобы скопировать в буфер обмена.");

    const shortUrl = mediaUrl != null ?
        (mediaUrl.startsWith("https://zen.yandex") ? `${mediaUrl}/${card.id}` : `https://zen.yandex.ru/${mediaUrl}/${card.id}`)
        : card.shortUrl;
    c4r2Link.addEventListener('click', event => {
        copyTextToClipboard(shortUrl);
        event.preventDefault();
    });
    c4r2.appendChild(c4r2Link);

    const c4r2Tags = createElement("span", "prozen_studio_card_icon_tags");
    c4r2Tags.style.marginRight = "0px";
    c4r2Tags.style.marginLeft = "5px";
    const tagsHint = card.tags.length === 0 ? "Теги не указаны" : `Теги: ${card.tagsStr}`;
    c4r2Tags.setAttribute("title", tagsHint);
    c4r2Tags.addEventListener('click', event => {
        copyTextToClipboard(card.tagsStr);
        event.preventDefault();
    });
    c4r2.appendChild(c4r2Tags);

    col4.appendChild(c4r2);
    c4r2.style.zIndex = "14";
    c4r2.style.cursor = "pointer";
}

function modifyPublicationsCard(publicationItemStats, card) {
    // Первая колонка
    //const col1 = createElement("div", "author-studio-publication-item__stat-item author-studio-publication-item__stat-item_type_views");
    const col1 = createElement("div", "prozen-card-container-item");
    publicationItemStats.appendChild(col1);

    // Показы
    const c1r1 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    c1r1.setAttribute("title", "Показы");
    col1.appendChild(c1r1);
    const c1r1Icon = createElement("span", "prozen_studio_card_icon_shows");
    const c1r1Text = createElement("span");
    c1r1Text.innerText = card.feedShowStr;
    c1r1.appendChild(c1r1Icon);
    c1r1.appendChild(c1r1Text);

    // Просмотры
    const c1r2 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    c1r2.setAttribute("title", "Просмотры (CTR, %)");
    const c1r2Icon = createElement("span", "prozen_studio_card_icon_views");
    const c1r2Text = createElement("span");
    c1r2Text.innerText = card.viewsStr;
    c1r2.appendChild(c1r2Icon);
    c1r2.appendChild(c1r2Text);
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
    //const col2 = createElement("div", "author-studio-publication-item__stat-item author-studio-publication-item__stat-item_type_shows");
    const col2 = createElement("div", "prozen-card-container-item");
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

    // Просмотры подписчиков //prozen-subscribers-views
    const c2r3 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    const c2r3Icon = createElement("span", "prozen_studio_card_icon_subscribers");
    const c2r3Text = createElement("span", "prozen-subscribers-views");
    c2r3Text.innerText = card.getSubscribersViews();
    c2r3.setAttribute("title", card.getSubscribersViewsHint());
    c2r3.appendChild(c2r3Icon);
    c2r3.appendChild(c2r3Text);
    col2.appendChild(c2r3);

    // Третья колонка
    //const col3 = createElement("div", "author-studio-publication-item__stat-item author-studio-publicauthor-studio-publication-itemation-item__stat-item_type_comments");
    const col3 = createElement("div", "prozen-card-container-item");
    publicationItemStats.appendChild(col3);

    // ER
    const c3r1 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    c3r1.style.textAlign = "right";
    c3r1.setAttribute("title", "Коэффициент вовлечённости, ER (%)");
    const c3r1Icon = createElement("span", "prozen_studio_card_icon_er");
    const c3r1Text = createElement("span");
    c3r1Text.innerText = card.erStr;
    c3r1.appendChild(c3r1Icon);
    c3r1.appendChild(c3r1Text);
    col3.appendChild(c3r1);

    // Среднее время просмотра
    const c3r2 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    c3r2.style.textAlign = "right";
    c3r2.setAttribute("title", "Среднее время просмотра: " + card.readTimeStr);
    const c3r2Icon = createElement("span", "prozen_studio_card_icon_clock");
    const c3r2Text = createElement("span");
    c3r2Text.innerText = card.readTimeStrHMS;
    c3r2.appendChild(c3r2Icon);
    c3r2.appendChild(c3r2Text);
    col3.appendChild(c3r2);

    // Теги и ссылка
    const c3r3 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 author-studio-publication-item__name");
    c3r3.style.textAlign = "right";
    c3r3.setAttribute("title", "Короткая ссылка.\nНажмите, чтобы скопировать в буфер обмена.");
    // Теги
    const c3r3IconTags = createElement("span", "prozen_studio_card_icon_tags");
    c3r3IconTags.addEventListener('click', event => {
        copyTextToClipboard(card.tagsStr);
        event.preventDefault();
    });
    c3r3.appendChild(c3r3IconTags);

    const tagList = card.tags.length === 0 ? "Теги не указаны" : `Теги: ${card.tagsStr}`;
    c3r3IconTags.setAttribute("title", tagList)


    // Ссылка
    const c3r3IconLink = createElement("span", "prozen_studio_card_icon_link");
    c3r3.appendChild(c3r3IconLink);
    const shortUrl = mediaUrl != null ?
        (mediaUrl.startsWith("https://zen.yandex") ? `${mediaUrl}/${card.id}` : `https://zen.yandex.ru/${mediaUrl}/${card.id}`)
        : card.shortUrl;
    c3r3IconLink.addEventListener('click', event => {
        copyTextToClipboard(shortUrl);
        event.preventDefault();
    });
    col3.appendChild(c3r3);


}

function modifyDashboardCard(publicationBlock, card) {
    /*
       Показы         Лайки                       ER
       Просмотры      Коменты                     Среднее время
       Дочитывания    Просм. подписчиков          Короткая ссылка/ Теги
     */

    const timeBlock = publicationBlock.getElementsByClassName("author-studio-publication-item__date")[0];
    timeBlock.innerText = card.timeStr;
    //timeBlock.style.opacity = 1;

    const publicationItemStats = publicationBlock.getElementsByClassName("author-studio-publication-item__stats")[0];
    removeChilds(publicationItemStats);
    modifyPublicationsCard(publicationItemStats, card);
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

    if (!await getOption(OPTIONS.informer)) {
        return;
    }

    if (document.getElementById("prozen-informer")) {
        return;
    }

    const column = document.getElementsByClassName("author-studio-main__right-column")[0];
    if (column == null) {
        return;
    }
    const informer = createElement("div", "author-studio-block");
    informer.id = "prozen-informer";
    column.appendChild(informer);


    const result = await Promise.all([
        checkHasNone(publisherId),
        getStatsActuality(), // getStatsInfo(), // getStatsInfoAndCounter()
        getStrikesInfo(),
        getBannedUsers()
    ]);

    const hasNone = result[0];
    const actuality = result [1]; // const statsInfo = result[1];
    const strikesInfo = result[2];
    const bannedUsers = result[3];

    const informerContent = createElement("div", "author-studio-useful-articles-block");
    informer.appendChild(informerContent);

    const informerH3 = createElement("h3", "Text Text_weight_bold Text_color_full Text_typography_text-16-20 author-studio-useful-articles-block__title");
    informerH3.innerText = "ПРОДЗЕН-инфо";
    informerH3.setAttribute("title", "Добавлено расширением ПРОДЗЕН");

    informerContent.appendChild(informerH3);

    if (strikesInfo.limitations != null) {
        const informerStrikes = createElement("span", "Text Text_color_full Text_typography_text-14-18 author-studio-article-card__title prozen-mb5-block");
        informerStrikes.innerText = `Предупреждения: ${strikesInfo.limitations}`
        informerStrikes.setAttribute("title", "Информация получена на основе данных раздела «Предупреждения»");
        informerContent.appendChild(informerStrikes);
    }

    if (strikesInfo.channelRestricted != null) {
        const informerPyos = createElement("span", "Text Text_color_full Text_typography_text-14-18 author-studio-article-card__title prozen-mb5-block");
        informerPyos.innerText = strikesInfo.channelRestricted ? "Канал ограничен" : "Канал не ограничен";
        informerPyos.setAttribute("title", "Информация получена на основе данных раздела «Предупреждения»");
        informerContent.appendChild(informerPyos);
    }

    if (hasNone != null) {
        const allNone = createElement("span", "Text Text_color_full Text_typography_text-14-18 author-studio-article-card__title prozen-mb5-block");
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

    if (bannedUsers) {
        const banCount = createElement("span", "Text Text_color_full Text_typography_text-14-18 author-studio-article-card__title prozen-mb5-block");
        banCount.innerText = `Заблокировано читателей: ${bannedUsers.bannedUsers.length}`;
        banCount.setAttribute("title", "Количество заблокированных комментаторов");
        informerContent.appendChild(banCount);
    }

    if (actuality) {
        const informerActuality = createElement("span", "Text Text_color_full Text_typography_text-14-18 author-studio-article-card__title prozen-mb5-block");
        informerActuality.innerText = `Статистика от ${actuality}`;
        informerActuality.setAttribute("title", "Время обновления статистики");
        informerContent.appendChild(informerActuality);
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
        this.subscribersViews = publicationData.subscribersViews;
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

        // Просмотры подписчиков
        this.subscribersViewsPercent = infiniteAndNan((this.subscribersViews / this.viewsTillEnd) * 100).toFixed(2);

        if (this.subscribersViews == null || this.subscribersViews === 0) {
            this.subscribersViewsStr = "0";
        } else {
            this.subscribersViewsStr = `${infiniteAndNanToStr(this.subscribersViews)} (${this.subscribersViewsPercent}%)`;
        }

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
            const publicationPath = this.url.split("/");
            this.shortUrl = publicationPath[4] === "id" ?
                `https://zen.yandex.ru/media/id/${publicationPath[5]}/${this.id}`
                : `https://zen.yandex.ru/media/${publicationPath[4]}/${this.id}`;
        } else {
            this.url = this.shortUrl;
        }
    }

    getSubscribersViewsHint() {
        if (this.subscribersViews < 0) {
            return "Не удалось получить данные\nо просмотрах подписчиках.";
        } else {
            return "Просмотры/дочитывания подписчиков, %";
        }
    }
    getSubscribersViews(updateValue) {
        if (updateValue != null) {
            this.subscribersViews = updateValue
        }
        this.subscribersViewsPercent = infiniteAndNan((this.subscribersViews / this.viewsTillEnd) * 100).toFixed(2);
        if (this.subscribersViews == null || this.subscribersViews === 0) {
            this.subscribersViewsStr = "0";
        } else if (this.subscribersViews < 0) {
            this.subscribersViewsStr = "–";
        } else {
            this.subscribersViewsStr = `${infiniteAndNanToStr(this.subscribersViews)} (${this.subscribersViewsPercent}%)`;
        }
        return this.subscribersViewsStr
    }
}