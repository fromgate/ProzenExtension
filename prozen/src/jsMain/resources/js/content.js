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

start();

///////////////////////////////////
// Functions
///////////////////////////////////

async function start() {
    if (await getOption(OPTIONS.prozen) === false) {
        return;
    }
    keepServiceWorkerAlive();
    setInterval(keepServiceWorkerAlive, 25000);
    listenToRequests();
    injectCssAndScript();
}

function keepServiceWorkerAlive() {
    chrome.runtime.sendMessage({ type: "keepAlive" }, () => {});
}

function injectCssAndScript() {
    window.removeEventListener("message", ReceiveProzenData);
    if (!document.getElementById("prozen-css")) {
        const css = createElement("link");
        css.setAttribute("rel", "stylesheet");
        css.setAttribute("type", "text/css");
        css.id = "prozen-css";
        css.setAttribute("href", chrome.runtime.getURL("css/prozen.css"));
        document.head.appendChild(css);
    }
    if (!document.getElementById("prozen-page-script")) {
        const script = createElement("script");
        script.setAttribute("type", "text/javascript");
        script.id = "prozen-page-script";
        script.setAttribute("src", chrome.runtime.getURL("js/page.js"));
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
                modifyStudioStyles();
                addStudioMenu();
                registerObserverWindowsLocation();
                // updateBalanceBlock();
                listenToRequests();
                setTimeout(addInformerBlock, 600);
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
        id = `channel_id=${publisherId}`;
    } else {
        id = `channel_name=${data.publisher.nickname.raw}`;
    }
    chrome.storage.local.set({
        prozenId: id,
        prozenSearch: textToFind,
        prozenToken: token,
        prozenPublisherId: publisherId
    }, () => {
        window.open(chrome.runtime.getURL("search.html"));
    });
}

function clickFindSadRobots() {
    let id;
    if (data.publisher.nickname === undefined) {
        id = `channel_id=${publisherId}`;
    } else {
        id = `channel_name=${data.publisher.nickname.raw}`;
    }
    chrome.storage.local.set({
        prozenId: id,
        prozenToken: token,
        prozenPublisherId: publisherId
    }, () => {
        window.open(chrome.runtime.getURL("sadrobot.html"));
    });
}

function clickTotalStatsButton() {
    chrome.storage.local.set({prozenToken: token, prozenPublisherId: publisherId}, function () {
        window.open(chrome.runtime.getURL("totalstats.html"));
    });
}

/************************************************/
/*                 СТУДИЯ!                      */
/************************************************/

// Определяем изменение адреса
function registerObserverWindowsLocation() {
    const bodyList = document.querySelector("body");
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

// @Deprecated

function updateBalanceBlock(count = 0) {
    const target = document.querySelector("div[class^=dashboard-stats__statsContainer-]");
    if (target != null && count < 5) {
        const nameBlock = target.querySelector("a[class^=item__statItemCompact-] > div[class^=item__accent-] > div.Text.Text_typography_text-13-16");
        const balanceElement = target.querySelector("a[class^=item__statItemCompact-] > div[class^=item__accent-] > div.Text.Text_typography_headline-20-24");
        if (nameBlock != null && balanceElement != null && nameBlock.textContent.startsWith("баланс")) {
            if (moneySaldo != null) {
                balanceElement.innerText = `${moneySaldo} ₽`;
                balanceElement.setAttribute("title", `Всего: ${moneyTotal} ₽`);
            }
            return;
        }
    }
    setTimeout(updateBalanceBlock.bind(null, count + 1), 1000);
}


// Поддержка Студии
function isStudio() {
    return document.getElementsByClassName("editor--author-studio-layout-new__block-1I").length > 0
        || document.getElementsByClassName("editor--author-studio-layout__content-3n").length > 0;
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
        const metriksUrl = metriksId !== undefined && metriksId !== null
            ? `https://metrika.yandex.ru/dashboard?id=${metriksId}`
            : "https://metrika.yandex.ru/list";

        const navBarContent = document.querySelector("div[class^=editor--navbar__content]");
        if (document.documentElement.clientHeight > 777 && navBarContent != null) {
            // const navBarContent = document.querySelector("div[class^=navbar__content]");
            const navbarLabelItem = createElement("div", "editor--navbar__labelItem-32");
            const navbarLabelLine = createElement("span", "editor--navbar__labelLine-28");
            const navbarLabelText = createElement("span", "editor--navbar__labelText-3D");
            navbarLabelText.innerText = "Продзен";
            navbarLabelItem.setAttribute("title", "Добавлено расширением „Продзен“");
            navbarLabelItem.appendChild(navbarLabelLine);
            navbarLabelItem.appendChild(navbarLabelText);
            const navBarSpace = navBarContent.querySelector("div[class^=editor--navbar__space]");
            // const separator = createElement("div", "navbar__div-YS navbar__div-fx"); // navbar__div-fx - старая версия
            navBarContent.insertBefore(navbarLabelItem, navBarSpace);
            const prozenMenu = createElement("ul", "editor--navbar__ul-1l editor--navbar__ul-3_ prozen_navbar"); // navbar__ul-3_ - старая версия
            prozenMenu.id = "prozen-main-menu";
            prozenMenu.setAttribute("data-publisherId", publisherId);
            prozenMenu.appendChild(createProzenMenuElement("Полная статистика", "prozen_menu_stats", clickTotalStatsButton, "Сводная статистика"));
            prozenMenu.appendChild(createProzenMenuElement("Метрика", "prozen_menu_metrika", metriksUrl, "Просмотр статистики в Яндекс.Метрике"));
            prozenMenu.appendChild(createProzenMenuElement("Поиск", "prozen_menu_search", clickSearchButton, "Альтернативная функция поиска"));
            prozenMenu.appendChild(createProzenMenuElement("Проверка публикаций", "prozen_menu_robot", clickFindSadRobots, "Поиск проблемных публикаций"));
            navBarContent.insertBefore(prozenMenu, navBarSpace);
        } else {
            let column = document.querySelector("div[class^=editor--author-studio-dashboard__stickyWrapper-]"); //"div[class^=author-studio-dashboard__rightContent-]"
            if (column == null) column = document.querySelector("div[class^=editor--author-studio-dashboard__rightContent-]");
            if (column == null) {
                return;
            }
            const menu = createElement("div", "editor--pager__container-Hn");
            menu.id = "prozen-main-menu";
            menu.setAttribute("data-publisherId", publisherId);
            column.appendChild(menu);
            menu.style.marginTop = "16px";

            const menuContent = createElement("div", "editor--loading-boundary-stacked-layout__content-15"); //author-studio-useful-articles-block
            menu.appendChild(menuContent);

            const menuH3 = createElement("div", "editor--author-studio-section-title__title-uh Text Text_weight_medium Text_color_full Text_typography_headline-18-22 editor--author-studio-section-title__text-2P");
            menuH3.innerText = "Дополнительное меню";
            menuH3.setAttribute("title", "Добавлено расширением ПРОДЗЕН");
            menuContent.appendChild(menuH3);

            const menuStats = createElement("div", "Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block");
            menuStats.innerText = "Полная статистика";
            menuStats.addEventListener("click", clickTotalStatsButton);
            menuStats.style.cursor = "pointer";
            menuContent.appendChild(menuStats);

            const menuMetriks = createElement("div", "Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block");
            menuMetriks.innerText = "Метрика";
            menuMetriks.style.cursor = "pointer";
            menuMetriks.addEventListener("click", window.open.bind(null, metriksUrl));
            menuContent.appendChild(menuMetriks);

            const menuSearch = createElement("div", "Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block");
            menuSearch.innerText = "Поиск";
            menuSearch.addEventListener("click", clickSearchButton);
            menuSearch.style.cursor = "pointer";
            menuContent.appendChild(menuSearch);

            const menuNoindex = createElement("div", "Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block");
            menuNoindex.innerText = "Проверка публикаций";
            menuNoindex.addEventListener("click", clickFindSadRobots);
            menuNoindex.style.cursor = "pointer";
            menuContent.appendChild(menuNoindex);
        }
    }
}

function createProzenMenuElement(title, iconClass, url = null, hint = null, bold = false) {
    const navItem = createElement("li");
    navItem.style.cursor = "pointer";
    if (hint !== null) {
        navItem.setAttribute("title", hint);
    }
    let menuLine;
    if (url == null) {
        menuLine = createElement("div", "editor--navbar__item-17"); //editor--navbar__item-17 editor--navbar__item-2e
        bold = true;
    } else if (typeof url === "string") {
        menuLine = createElement("a", "editor--navbar__item-17"); // editor--navbar__item-17 editor--navbar__item-2e
        menuLine.setAttribute("target", "_blank");
        menuLine.setAttribute("href", url);
    } else {
        menuLine = createElement("a", "editor--navbar__item-17"); //editor--navbar__item-17 editor--navbar__item-2e
        menuLine.addEventListener("click", url);
        menuLine.cursor = "pointer";
    }
    navItem.appendChild(menuLine);

    const menuIcon = createElement("div", "editor--navbar__icon-1d"); //"editor--navbar__icon-1d editor--navbar__icon-1R"
    if (iconClass != null) {
        const icon = createElement("div", "editor--navbar__svg-2_"); //editor--navbar__svg-2_ editor--navbar__svg-3j
        icon.classList.add(iconClass);
        menuIcon.appendChild(icon);
    }
    menuLine.appendChild(menuIcon);

    const menuText = createElement("span", "Text Text_typography_text-15-20 editor--navbar__text-pc"); //Text Text_color_full Text_typography_text-14-18 navbar__text
    menuText.innerText = title;
    menuLine.appendChild(menuText);
    if (bold) {
        menuText.style.fontWeight = "bold";
    }
    return navItem;
}


async function modifyStudioStyles() {
    // const showComments = await getOption(OPTIONS.commentsWidget);
    const hideRealtimeStatsList = await getOption(OPTIONS.shortDashboardRealtime);
    const hidePromoteBanner = await getOption(OPTIONS.promoteShow);
    let sheetStr = "";
    // if (!showComments) sheetStr +=  ".author-studio-comments-block__authorStudioCommentsBlock-13{display:none;}";
    if (hideRealtimeStatsList) sheetStr += ".editor--realtime-publications__list-3o{display:none;}";
    if (hidePromoteBanner) {
        sheetStr += ".editor--author-studio-dashboard__promoBanner-1U{display:none;}";
        sheetStr += ".editor--youtube-entrency-panel__root-2D{display:none;}";
    }

    if (sheetStr.length > 0) {
        const sheet = new CSSStyleSheet();
        sheet.replaceSync(sheetStr);
        document.adoptedStyleSheets = [sheet];
    }
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
    } else if (request.type === "prozen-mainpage-request") {

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


function publicationsDataToCards(requestData) {
    const cards = [];
    const publications = requestData.publications;
    const publicationCounters = requestData.publicationCounters;
    const socialCounters = requestData.socialCounters;
    if (publications.length > 0) {
        for (let i = 0; i < publications.length; i++) {
            const merged = {
                ...publications[i],
                ...(publicationCounters.find((itmInner) => itmInner.publicationId === publications[i].id)),
                ...(socialCounters.find((itmInner) => itmInner.publicationId === publications[i].id))
            };
            const card = Card.createCardFromPublicationData2(merged);
            cards.push(card);
        }
    }
    return cards;
}

// Публикации
async function processPublicationsCards(request) {
    const data = await getPublicationsByFilterAndSubscribers(request.pageSize, request.types, request.publicationIdAfter, request.view, request.query);
    const cards = publicationsDataToCards(data);
    if (isPublicationGrid()) {
        modifyPublicationGrid(cards);
    } else {
        modifyPublicationTable(cards);
    }
}

function getPublicationCellById(publicationId) {
    const table = document.querySelector("table[class^=editor--publications-list]");
    const a = table.querySelector(`a[class^=editor--publication-preview][href*='${publicationId}'`);
    if (a != null) return a.parentNode;
    const div = table.querySelector(`div.editor--publication-cover__image-gr[style*='${publicationId}'`);
    return div.parentNode.parentNode.parentNode.parentNode;
}

function getPublicationGridCellById(publicationId) {
    const a = document.querySelector(`a.editor--publication-card__link-3k[href*='${publicationId}'`);
    if (a != null) return a.parentNode;
    const div = document.querySelector(`div.editor--publication-cover__image-gr[style*='${publicationId}'`);
    return div.parentNode.parentNode.querySelector("div.editor--publication-card__stats-1k");
}

function modifyPublicationsGridCell(cell, card) {
    if (cell.hasAttribute("data-prozen-publication-id")) {
        return;
    }
    cell.setAttribute("data-prozen-publication-id", card.id);
    const date = cell.querySelector("span.Text_typography_text-12-16");  //Text Text_color_low Text_typography_text-12-16
    date.innerText = card.timeStr + " · ";
    modifyGridCellStats(cell, card);
}


function modifyPublicationsCell(cell, card) {
    if (cell.hasAttribute("data-prozen-publication-id")) {
        return;
    }
    cell.setAttribute("data-prozen-publication-id", card.id);

    const snippet = cell.querySelector("p.editor--publication-preview__snippet-IX");
    if (snippet != null && snippet.style != null && !["post", "gallery", "brief"].includes(card.type)) {
        snippet.style.setProperty("-webkit-line-clamp", "2");
    }

    const timeCell = cell.parentNode.cells[1];
    timeCell.querySelector("span").innerText = card.timeStr;

    const previewContainer = cell.querySelector("div.editor--publication-preview__previewContainer-1j");  //publication-preview__preview-container
    const publicationItemStats = createElement("div", "prozen-card-container");

    previewContainer.appendChild(publicationItemStats);
    modifyPublicationsCard(publicationItemStats, card);
}

function modifyPublicationTable(cards) {
    if (isPublicationGrid()) {
        return;
    }
    const waitList = [];
    for (let i = 0; i < cards.length; i++) {
        const card = cards[i];
        const cell = getPublicationCellById(card.id);
        if (cell == null) {
            if (!["post", "narrative", "story"].includes(card.type)) {
                waitList.push(card);
            }
        } else {
            modifyPublicationsCell(cell, card);
        }
    }
    if (waitList.length > 0) {
        setTimeout(modifyPublicationTable.bind(null, waitList), 300);
    }
}

function modifyPublicationGrid(cards) {
    if (!isPublicationGrid()) {
        return;
    }
    const waitList = [];
    for (let i = 0; i < cards.length; i++) {
        const card = cards[i];
        const cell = getPublicationGridCellById(card.id);
        if (cell == null) {
            if (!["post", "narrative", "story"].includes(card.type)) {
                waitList.push(card);
            }
        } else {
            modifyPublicationsGridCell(cell, card);
        }
    }
    if (waitList.length > 0) {
        setTimeout(modifyPublicationGrid.bind(null, waitList), 300);
    }
}

async function processDashboardCards(pageSize) {
    const data = await getPublicationsByFilterAndSubscribers(pageSize);
    const cards = publicationsDataToCards(data);
    const studioPublicationsBlock = document.querySelector("div[class^=editor--last-publications__lastPublications-] > div > div");//document.getElementsByClassName("author-studio-publications-block")[0];


    const publicationsBlocks = studioPublicationsBlock.querySelectorAll("a");

    if (publicationsBlocks.length > 0) {
        for (let i = 0; i < publicationsBlocks.length; i++) {
            const publicationBlock = publicationsBlocks.item(i);
            const publicationId = getPublicationBlockId(publicationBlock);
            const card = cards.find((itmInner) => itmInner.id === publicationId);
            if (publicationId != null && card != null) {
                modifyDashboardCard(publicationBlock, card);
            }
        }
    }
}

/*
   Возвращает true, если странице публикаций включён режим отображения
   карточек в виде стеки. false — если в виде таблицы.
 */
function isPublicationGrid() {
    const div = document.querySelector("table[class^=editor--publications-list]"); //publications-list__publicationsList-3U
    return div == null;
}


function modifyGridCellStats(cell, card) {
    // Показы                |            Лайки
    // Просмотры (ctr)       |      Комментарии
    // Дочитывания           |    Вовлечённость
    // Просмотры подписчиков |    Время дочитывания /Ссылка / Теги

    const statsBlock = cell.querySelector("div[class^=editor--stats__block]");  //stats__block-39
    removeChilds(statsBlock);


    // Первый ряд
    const col1 = createElement("div", "editor--stats__item-3m");
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

    // Лайки / Репосты
    const c1r2 = createElement("span", "Text Text_color_full Text_typography_text-12-16");
    c1r2.setAttribute("title", `Лайки: ${card.likesStr}\nРепосты: ${card.sharesStr}`);
    const c1r2LikesIcon = createElement("span", "prozen_studio_card_icon_like");
    const c1r2LikesText = createElement("span");
    c1r2LikesText.innerText = card.likes;
    const c1r2RepostsIcon = createElement("span", "prozen_studio_cards_reposts");
    const c1r2RepostsText = createElement("span");
    c1r2RepostsText.innerText = card.shares;
    c1r2.appendChild(c1r2LikesIcon);
    c1r2.appendChild(c1r2LikesText);
    c1r2.appendChild(c1r2RepostsIcon);
    c1r2.appendChild(c1r2RepostsText);
    col1.appendChild(c1r2);
    c1r2.style.zIndex = "14";
    c1r2.style.cursor = "default";

    // Второй ряд
    const col2 = createElement("div", "editor--stats__item-3m");
    statsBlock.appendChild(col2);

    // Просмотры
    const c2r1 = createElement("span", "Text Text_color_full Text_typography_text-12-16");
    c2r1.setAttribute("title", card.viewsTitle);
    const c2r1Icon = createElement("span", "prozen_studio_card_icon_views");
    const c2r1Text = createElement("span");
    c2r1Text.innerText = card.viewsStr;
    c2r1.appendChild(c2r1Icon);
    c2r1.appendChild(c2r1Text);
    col2.appendChild(c2r1);
    c2r1.style.zIndex = "14";
    c2r1.style.cursor = "default";

    // Подписки / Комментарии
    const c2r2 = createElement("span", "Text Text_color_full Text_typography_text-12-16");
    c2r2.setAttribute("title", `Подписки: ${card.subscriptionsStr}\nКомментарии: ${card.commentsStr}`);
    const c2r2SubscriptionsIcon = createElement("span", "prozen_studio_cards_subscribers");
    const c2r2SubscriptionsText = createElement("span");
    c2r2SubscriptionsText.innerText = card.subscriptions;
    const c2r2CommentsIcon = createElement("span", "prozen_studio_card_icon_comments");
    const c2r2CommentsText = createElement("span");
    c2r2CommentsText.innerText = card.comments;
    c2r2.appendChild(c2r2SubscriptionsIcon);
    c2r2.appendChild(c2r2SubscriptionsText);
    c2r2.appendChild(c2r2CommentsIcon);
    c2r2.appendChild(c2r2CommentsText);
    col2.appendChild(c2r2);
    c2r2.style.zIndex = "14";
    c2r2.style.cursor = "default";

    // Третий ряд
    const col3 = createElement("div", "editor--stats__item-3m");
    statsBlock.appendChild(col3);

    // Дочитывания
    const c3r1 = createElement("span", "Text Text_color_full Text_typography_text-12-16");
    c3r1.setAttribute("title", card.viewsTillEndTitle);
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
    const col4 = createElement("div", "editor--stats__item-3m");
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
    c4r2TimeIcon.setAttribute("title", `Среднее время просмотра: ${card.readTimeStr}`);
    const c4r2TimeText = createElement("span");
    c4r2TimeText.setAttribute("title", `Среднее время просмотра: ${card.readTimeStr}`);
    c4r2TimeText.innerText = card.readTimeStrHMS;
    c4r2TimeText.style.marginRight = "7px";
    c4r2.appendChild(c4r2TimeIcon);
    c4r2.appendChild(c4r2TimeText);


    const c4r2Link = createElement("span", "prozen_studio_card_icon_link");
    c4r2Link.setAttribute("title", "Короткая ссылка.\nНажмите, чтобы скопировать в буфер обмена.");

    const shortUrl = `https://dzen.ru/media/id/${publisherId}/${card.id}`;

    c4r2Link.addEventListener("click", event => {
        copyTextToClipboard(shortUrl);
        event.preventDefault();
    });
    c4r2.appendChild(c4r2Link);

    const c4r2Repost = createElement("span", "prozen_studio_card_icon_repost");
    c4r2Repost.style.marginRight = "0px";
    c4r2Repost.style.marginLeft = "5px";
    c4r2Repost.setAttribute("title", "Сделать репост публикации");
    c4r2Repost.addEventListener("click", event => {
        openUrl(`https://dzen.ru/media/zen/login?briefEditorPublicationId=draft&repostId=${card.id}`);
        event.preventDefault();
    });
    c4r2.appendChild(c4r2Repost);


    col4.appendChild(c4r2);
    c4r2.style.zIndex = "14";
    c4r2.style.cursor = "pointer";
}

/*
   Показы                     Лайки       Репосты
   Просмотры / дочитывания    Коменты     ER
   Просм. подписчиков         Подписки    Ссылка / Репост
 */
function modifyPublicationsCard(publicationItemStats, card) {
    // Первая колонка
    //const col1 = createElement("div", "author-studio-publication-item__stat-item author-studio-publication-item__stat-item_type_views");
    const col1 = createElement("div", "prozen-card-container-item");
    publicationItemStats.appendChild(col1);

    // Показы
    const c1r1 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 editor--author-studio-publication-item__name");
    c1r1.setAttribute("title", "Показы");
    col1.appendChild(c1r1);
    const c1r1Icon = createElement("span", "prozen_studio_card_icon_shows");
    const c1r1Text = createElement("span");
    c1r1Text.innerText = card.feedShowStr;
    c1r1.appendChild(c1r1Icon);
    c1r1.appendChild(c1r1Text);

    // Просмотры / Дочитывания
    const c1r2 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 editor--author-studio-publication-item__name");
    c1r2.setAttribute("title", card.viewsTitle);
    const c1r2Icon = createElement("span", "prozen_studio_card_icon_views");
    const c1r2Text = createElement("span");
    c1r2Text.innerText = card.viewsStr;
    c1r2.appendChild(c1r2Icon);
    c1r2.appendChild(c1r2Text);
    col1.appendChild(c1r2);

    // Дочитывания
    const c1r3 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 editor--author-studio-publication-item__name");
    c1r3.setAttribute("title", card.viewsTillEndTitle);
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

    // Лайки / Шеры
    const c2r1 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 editor--author-studio-publication-item__name");
    c2r1.setAttribute("title", `Лайки: ${card.likesStr}\nРепосты: ${card.sharesStr}`);
    const c2r1LikesIcon = createElement("span", "prozen_studio_card_icon_like");
    const c2r1LikesText = createElement("span");
    c2r1LikesText.innerText = card.likes;

    const c2r1RepostsIcon = createElement("span", "prozen_studio_cards_reposts");
    const c2r1RepostsText = createElement("span");
    c2r1RepostsText.innerText = card.shares;


    //const c3r3IconRepost = createElement("span", "prozen_studio_card_icon_repost");
    c2r1.appendChild(c2r1LikesIcon);
    c2r1.appendChild(c2r1LikesText);
    c2r1.appendChild(c2r1RepostsIcon);
    c2r1.appendChild(c2r1RepostsText);
    col2.appendChild(c2r1);

    // Подписки / Коменты
    const c2r2 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 editor--author-studio-publication-item__name");
    c2r2.setAttribute("title", `Подписки: ${card.subscriptionsStr}\nКомментарии: ${card.commentsStr}`);
    const c2r2SubscriptionsIcon = createElement("span", "prozen_studio_cards_subscribers");
    const c2r2SubscriptionsText = createElement("span");
    c2r2SubscriptionsText.innerText = card.subscriptions;
    const c2r2CommentsIcon = createElement("span", "prozen_studio_card_icon_comments");
    const c2r2CommentsText = createElement("span");
    c2r2CommentsText.innerText = card.comments;
    c2r2.appendChild(c2r2SubscriptionsIcon);
    c2r2.appendChild(c2r2SubscriptionsText);
    c2r2.appendChild(c2r2CommentsIcon);
    c2r2.appendChild(c2r2CommentsText);
    col2.appendChild(c2r2);

    // Просмотры подписчиков //prozen-subscribers-views
    const c2r3 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 editor--author-studio-publication-item__name");
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
    const c3r1 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 editor--author-studio-publication-item__name");
    c3r1.style.textAlign = "right";
    c3r1.setAttribute("title", "Коэффициент вовлечённости, ER (%)");
    const c3r1Icon = createElement("span", "prozen_studio_card_icon_er");
    const c3r1Text = createElement("span");
    c3r1Text.innerText = card.erStr;
    c3r1.appendChild(c3r1Icon);
    c3r1.appendChild(c3r1Text);
    col3.appendChild(c3r1);

    // Среднее время просмотра
    const c3r2 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 editor--author-studio-publication-item__name");
    c3r2.style.textAlign = "right";
    c3r2.setAttribute("title", `Среднее время просмотра: ${card.readTimeStr}`);
    const c3r2Icon = createElement("span", "prozen_studio_card_icon_clock");
    const c3r2Text = createElement("span");
    c3r2Text.innerText = card.readTimeStrHMS;
    c3r2.appendChild(c3r2Icon);
    c3r2.appendChild(c3r2Text);
    col3.appendChild(c3r2);

    // Теги и ссылка
    const c3r3 = createElement("div", "Text Text_weight_medium Text_color_full Text_typography_text-12-16 editor--author-studio-publication-item__name");
    c3r3.style.textAlign = "right";
    c3r3.setAttribute("title", "Короткая ссылка.\nНажмите, чтобы скопировать в буфер обмена.");

    // Ссылка
    const c3r3IconLink = createElement("span", "prozen_studio_card_icon_link");
    c3r3.appendChild(c3r3IconLink);
    const shortUrl = `https://dzen.ru/media/id/${publisherId}/${card.id}`;
    c3r3IconLink.addEventListener("click", event => {
        copyTextToClipboard(shortUrl);
        event.preventDefault();
    });

    // Репост
    const c3r3IconRepost = createElement("span", "prozen_studio_card_icon_repost");
    c3r3IconRepost.setAttribute("title", "Сделать репост публикации");
    c3r3IconRepost.addEventListener("click", event => {
        openUrl(`https://dzen.ru/media/zen/login?briefEditorPublicationId=draft&repostId=${card.id}`);
        event.preventDefault();
    });
    c3r3.appendChild(c3r3IconRepost);

    col3.appendChild(c3r3);

}

function modifyDashboardCard(publicationBlock, card) {
    /*
       Показы         Лайки / Репосты                       ER
       Просмотры      Подписки / Коменты                     Среднее время
       Дочитывания    Просм. подписчиков          Короткая ссылка/ Теги
     */

    /*
       Показы                     Лайки       Репосты
       Просмотры / дочитывания    Коменты     ER
       Просм. подписчиков         Подписки    Ссылка / Репост
     */

    const timeBlock = publicationBlock.querySelector("div[class^=editor--dashboard-publication-item__titleContainer-] > span");
    timeBlock.innerText = card.timeStr;
    const publicationItemStats = publicationBlock.querySelector("div[class^=editor--dashboard-publication-item__publicationStat-]");
    removeChilds(publicationItemStats);
    modifyPublicationsCard(publicationItemStats, card);
}

function getPublicationBlockUrl(publicationBlock) {
    return publicationBlock.hasAttribute("href") ? publicationBlock.getAttribute("href") : null;
}

function getPublicationBlockId(publicationBlock) {
    const cover = publicationBlock.querySelector("div.editor--publication-cover__image-gr");
    let id = null;
    if (cover != null) {
        const url = cover.style.backgroundImage.slice(4, -1).replace(/"/g, "");
        id = url.split("_")[2];
    }
    if (id == null && publicationBlock.hasAttribute("href")) {
        const href = publicationBlock.getAttribute("href");
        const idArray = href.split("-");
        id = idArray[idArray.length - 1];
    }
    return id;
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

function getData() {
    return this.data;
}

// Информер
async function addInformerBlock() {
    if (!await getOption(OPTIONS.informer)) {
        return;
    }

    if (document.getElementById("prozen-informer")) {
        return;
    }
    let column = document.querySelector("div[class^=editor--author-studio-dashboard__stickyWrapper-]"); //"div[class^=author-studio-dashboard__rightContent-]"
    if (column == null) column = document.querySelector("div[class^=editor--author-studio-dashboard__rightContent-]");

    if (column == null) {
        return;
    }
    const informer = createElement("div", "editor--notifications-preview-block-desktop__block-39");
    informer.id = "prozen-informer";
    column.appendChild(informer);
    informer.style.marginTop = "24px";

    const channelUrl = mediaUrl.replace("/media/", "/");

    const date = new Date();
    const todayStr = dateToYYYYMMDD(date); //`${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`;
    date.setDate(date.getDate() - 7);
    const fromStr7 = dateToYYYYMMDD(date); //`${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`;
    date.setDate(date.getDate() - 23); // назад ещё на 23 дня, в сумме 30 дней
    const fromStr30 = dateToYYYYMMDD(date);

    const result = await Promise.all([
        checkHasNoneUrl(channelUrl),
        getStatsActuality(),
        getStrikesInfo(),
        getBannedUsers(),
        getTimespentRewards(fromStr7, todayStr),
        getSCR(fromStr30, todayStr)
    ]);

    const hasNone = result[0];
    const actuality = result [1]; // const statsInfo = result[1];
    const strikesInfo = result[2];
    const bannedUsers = result[3];
    const rewards = result[4];
    const scr = result[5];

    const informerContent = createElement("div", "editor--loading-boundary-stacked-layout__content-15"); //author-studio-useful-articles-block
    informer.appendChild(informerContent);

    const informerH3 = createElement("h3", "editor--author-studio-section-title__title-uh Text Text_weight_medium Text_color_full Text_typography_headline-18-22 editor--author-studio-section-title__text-2P");
    informerH3.innerText = "ПРОДЗЕН-инфо";
    informerH3.setAttribute("title", "Добавлено расширением „Продзен“");
    informerH3.style.marginBottom = "10px";

    informerContent.appendChild(informerH3);

    if (strikesInfo != null && strikesInfo.limitations != null) {
        const informerStrikes = createElement("span", "Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block");
        informerStrikes.innerText = `Предупреждения: ${strikesInfo.limitations}`;
        informerStrikes.setAttribute("title", "Информация получена на основе данных раздела «Предупреждения»");
        informerContent.appendChild(informerStrikes);
    }

    if (strikesInfo != null && strikesInfo.channelRestricted != null) {
        const informerPyos = createElement("span", "Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block");
        informerPyos.innerText = strikesInfo.channelRestricted ? "Канал ограничен" : "Канал не ограничен";
        informerPyos.setAttribute("title", "Информация получена на основе данных раздела «Предупреждения»");
        informerContent.appendChild(informerPyos);
    }

    if (hasNone != null) {
        const allNone = createElement("span", "Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block");
        if (hasNone) {
            allNone.innerText = "Канал не индексируется 🤖";
            allNone.setAttribute("title", "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
                "Главная страница канала не индексируется поисковиками.\n" +
                "Это нормальная ситуация для новых каналов.");
        } else {
            allNone.innerText = "Канал индексируется";
        }
        informerContent.appendChild(allNone);
    }

    if (scr != null) {
        const scrEl = createElement("span", "Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block");
        scrEl.innerText = `Охват подписчиков (SCR): ${scr}%`;
        scrEl.setAttribute("title",
            "Коэффициент охвата подписчиков (Subscribers Coverage Rate).\n" +
            "Показывает какая доля подписчиков видит карточки публикаций.");
        informerContent.appendChild(scrEl);
    }

    if (!!bannedUsers && !!bannedUsers.bannedUsers) {
        const banCount = createElement("span", "Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block");
        banCount.innerText = `Заблокировано читателей: ${bannedUsers.bannedUsers.length}`;
        banCount.setAttribute("title", "Количество заблокированных комментаторов");
        informerContent.appendChild(banCount);
    }

    if (actuality) {
        const informerActuality = createElement("span", "Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block");
        informerActuality.innerText = `Статистика от ${actuality}`;
        informerActuality.setAttribute("title", "Время обновления статистики");
        informerContent.appendChild(informerActuality);
    }

    if (rewards?.length > 0) {
        const lastReward = rewards.at(-1);
        const previousReward = rewards.at(-2);
        let change = "";
        if (lastReward.course > previousReward.course) change = "↑️";
        if (lastReward.course < previousReward.course) change = "↓️";

        const informerCourse = createElement("span", "Text Text_typography_text-15-20 editor--notification__textWrapper-1- editor--notification__text-3k prozen-mb5-block");
        informerCourse.innerText = `Курс минуты ${lastReward.dateStr}: ${change}${lastReward.courseStr}₽`;
        informerCourse.setAttribute("title", `Стоимость минуты вовлечённого просмотра\nПредыдущий курс (${previousReward.dateStr}): ${previousReward.courseStr} ₽`);
        informerContent.appendChild(informerCourse);
    }

    // ZenReader Subscribe link
    const zenReaderLink = createElement("a");
    zenReaderLink.setAttribute("href", zenReaderUrl());
    const zenReaderSpan = createElement("span", "Text Text_color_full Text_typography_text-14-18 editor--author-studio-article-card__title prozen-mb5-block");
    zenReaderSpan.innerText = "🔗 Подписка в ZenReader";
    zenReaderSpan.setAttribute("title", "Ссылка для подписки на канал\nв телеграм-боте ZenReader");
    zenReaderLink.appendChild(zenReaderSpan);
    informerContent.appendChild(zenReaderLink);
}

function zenReaderUrl() {
    return data.publisher.nickname === undefined
        ? `https://t.me/zenreaderbot?start=id-${publisherId}`
        : `https://t.me/zenreaderbot?start=${data.publisher.nickname.raw.replace(".", "-")}`;
}

class Card {
    static createCardFromPublicationData2(publicationData) {
        return new Card(
            publicationData.content.preview.title, publicationData.id,
            publicationData.publisherId, publicationData.addTime, publicationData.content.modTime,
            publicationData.publishTime, publicationData.impressions,
            publicationData.clicks, publicationData.views, publicationData.typeSpecificViews /*deepViews*/,
            publicationData.subscribersViews, publicationData.sumViewTimeSec,
            publicationData.likeCount, publicationData.commentCount, publicationData.content.type, [],
            publicationData.subscriptions, publicationData.shares
        );
    }

    static createCardFromPublicationData(publicationData) {
        return new Card(publicationData.content.title, publicationData.id,
            publicationData.publisherId, publicationData.addTime, publicationData.modTime,
            publicationData.publishTime, publicationData.privateData.statistics.feedShows,
            publicationData.privateData.statistics.shows, publicationData.privateData.statistics.views,
            publicationData.privateData.statistics.viewsTillEnd, publicationData.subscribersViews,
            publicationData.privateData.statistics.sumViewTimeSec, publicationData.privateData.statistics.likes,
            publicationData.privateData.statistics.comments, publicationData.content.type,
            arraysJoin(publicationData.privateData.tags, publicationData.privateData.embeddedTags)
        );
    }

    //constructor(publicationData, publicationUrl) {
    constructor(title, publicationId, publisherId, addTime, modTime, publishTime,
                feedShows, clicks, views, viewsTillEnd, subscribersViews, sumViewTimeSec,
                likes, comments, contentType, tags, subscriptions = 0, shares = 0) {
        this.title = title;
        this.id = publicationId;
        this.publisherId = publisherId;
        this.addTime = addTime;
        this.modTime = modTime;
        this.publishTime = publishTime;
        this.feedShows = feedShows;
        this.clicks = clicks;
        this.views = views;
        this.viewsTillEnd = viewsTillEnd;
        this.subscribersViews = subscribersViews;
        this.sumViewTimeSec = sumViewTimeSec;
        this.likes = likes;
        this.comments = comments;
        this.type = contentType;
        this.tags = tags;
        this.subscriptions = subscriptions;
        this.shares = shares;

        // Текстовые представления данных
        // Время модификации
        this.dayMod = dateTimeFormat(this.modTime);
        this.dayCreate = this.addTime === undefined ? this.dayMod : dateTimeFormat(this.addTime);
        this.showTime = this.dayMod !== this.dayCreate ? `${this.dayCreate} (${this.dayMod})` : this.dayCreate;
        this.timeStr = this.showTime;

        // Показы
        this.feedShowStr = infiniteAndNanToStr(this.feedShows);

        // Просмотры (CTR%)
        this.ctr = (infiniteAndNan(this.clicks / this.feedShows) * 100).toFixed(2);
        this.viewsStr = `${infiniteAndNanToStr(this.views == null ? this.clicks : this.views)} (${this.ctr}%)`;
        this.viewsTitle = this.type === "article" ? "Клики (CTR)" : "Просмотры (VTR)";

        // Дочитывания
        this.readsPercent = infiniteAndNan(this.viewsTillEnd / (this.views == null ? this.clicks : this.views) * 100).toFixed(2);

        this.viewsTillEndStr = `${infiniteAndNanToStr(this.viewsTillEnd)} (${this.readsPercent}%)`;
        this.viewsTillEndTitle = this.type === "article" ? "Дочитывания" : "Просмотры";


        // "Просмотры: ХХХ, Дочитывания: ХХХ"
        // this.fullViewsTitle = `${}`;

        if (this.type === "brief" || this.type === "gif" || this.type === "short_video") {
            this.ctr = (infiniteAndNan(this.viewsTillEnd / this.feedShows) * 100).toFixed(2);
            this.viewsStr = `${infiniteAndNanToStr(this.viewsTillEnd)} (${this.ctr}%)`;
            this.readsPercent = "";
            this.viewsTillEndStr = `${infiniteAndNanToStr(this.viewsTillEnd)}`;
        }

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

        // Добавленные подписчики
        this.subscriptionsEr = infiniteAndNan((this.subscriptions / this.erViews) * 100);
        this.subscriptionsStr = this.subscriptions === 0 ? "0 (0.00%)" : `${infiniteAndNanToStr(this.subscriptions)} (${this.subscriptionsEr.toFixed(2)}%)`;

        // Шеры
        this.sharesEr = infiniteAndNan((this.shares / this.erViews) * 100);
        this.sharesStr = this.shares === 0 ? "0 (0.00%)" : `${infiniteAndNanToStr(this.shares)} (${this.sharesEr.toFixed(2)}%)`;

        // Коэффициент вовлечённости
        this.erStr = `${infiniteAndNan((((this.comments + this.likes + this.subscriptions + this.shares) / this.erViews)) * 100).toFixed(2)}%`;

        // Теги
        this.tagsStr = joinByThree(this.tags);

        // Ссылка на статью (сокращённая)
        this.shortUrl = `https://dzen.ru/media/id/${this.publisherId}/${this.id}`;

    }

    getSubscribersViewsHint() {
        return this.subscribersViews < 0 ? "Не удалось получить данные\nо просмотрах подписчиках." : "Просмотры/дочитывания подписчиков, %";
    }

    getSubscribersViews(updateValue) {
        if (updateValue != null) {
            this.subscribersViews = updateValue;
        }
        this.subscribersViewsPercent = infiniteAndNan((this.subscribersViews / this.viewsTillEnd) * 100).toFixed(2);
        if (this.subscribersViews == null || this.subscribersViews === 0) {
            this.subscribersViewsStr = "0";
        } else if (this.subscribersViews < 0) {
            this.subscribersViewsStr = "–";
        } else {
            this.subscribersViewsStr = `${infiniteAndNanToStr(this.subscribersViews)} (${this.subscribersViewsPercent}%)`;
        }
        return this.subscribersViewsStr;
    }
}