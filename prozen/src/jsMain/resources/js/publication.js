start();

function start() {
    getOption(OPTIONS.prozen).then(enabled => {
        if (enabled) {
            window.removeEventListener("message", ReceiveProzenData);
            if (!document.getElementById("prozen-css")) {
                const css = document.createElement("link");
                css.setAttribute("rel", "stylesheet");
                css.setAttribute("type", "text/css");
                css.id = "prozen-css";
                css.setAttribute("href", chrome.runtime.getURL("css/prozen.css"));
                document.head.appendChild(css);
            }
            if (!document.getElementById("prozen-page-script")) {
                const script = document.createElement("script");
                script.setAttribute("type", "text/javascript");
                script.id = "prozen-page-script";
                script.setAttribute("src", chrome.runtime.getURL("js/page.js"));
                document.body.appendChild(script);
            }
            window.addEventListener("message", ReceiveProzenData);
        }
    });
}

function ReceiveProzenData(event) {
    if (event.source !== window) {
        return;
    }
    if (event.data.type && (event.data.type === "prozen-data")) {
        const data = event.data.jsonData;
        publisherId = event.data.jsonData.publisher.id;
        const pageType = getPageType(data);
        setTimeout(showPublicationStats.bind(null, pageType, data, publisherId), 500);
    }
}

function showPublicationStats(pageType, data, publisherId) {
    switch (pageType) {
        case "article":
            showStatsArticle(data, publisherId);
            break;
        case "brief":
            showStatsBrief(data, publisherId);
            break;
        case "video_old":
            showStatsVideoOld(data, publisherId);
            break;
    }
}

function getPageType(data) {
    const path = window.location.pathname;
    if (path.startsWith("/a/") && data != null && data.isArticle === true) {
        return "article";
    }

    if (path.startsWith("/b/") && data != null && data.isBrief === true) {
        return "brief";
    }

    if (path.startsWith("/media/")) {
        if (data != null) {
            if (data.isArticle === true) {
                return "article";
            }
            if (data.isBrief === true) {
                return "brief";
            }
            if (data.isGif === true) {
                return "video_old";
            }
        }
    }
    return "unknown";
}

function addHeaderClicks() {
    getOption(OPTIONS.subtitleLinks).then(option => {
        if (option) {

            let shortLink = document.head.querySelector("link[rel=canonical][href]").href;

            const headers = document.querySelectorAll("h2, h3");
            if (headers.length > 0) {
                for (let i = 0; i < headers.length; i++) {
                    const header = headers [i];
                    const ancorId = header.getAttribute("id");
                    if (ancorId !== undefined && ancorId !== null) {
                        const clickIcon = createElement("span", "publication_header_icon_url");
                        clickIcon.setAttribute("title", "Ссылка на заголовок.\n" +
                            "Кликните, чтобы скопировать её в буфер обмена");
                        clickIcon.addEventListener("click", copyTextToClipboard.bind(null, shortLink + "#" + ancorId));
                        header.insertBefore(clickIcon, header.firstChild);
                    }
                }
            }
        }
    });
}

async function showStatsBrief(data, publisherId) {
    if (data === null) {
        return;
    }

    const zenIds = getZenObject();
    const postId = zenIds !== null ? zenIds.publicationId : data.publication.id;

    const dayMod = dateTimeFormat(data.publication.content.modTime);
    const dayCreate = data.publication.addTime === undefined ? dayMod : dateTimeFormat(data.publication.addTime);
    const showTime = dayMod !== dayCreate ? dayCreate + " (" + dayMod + ")" : dayCreate;


    const views = data.publication.publicationStatistics.views;
    const sumTime = data.publication.publicationStatistics.sumViewTimeSec;
    const avgTime = Math.round(sumTime / views);
    const avgTimeStrHHMMSS = secToHHMMSS(avgTime);
    const avgTimeStr = secToText(avgTime);

    const briefStats = document.getElementsByClassName("desktop-brief-page__stats")[0];
    const dateDiv = briefStats.querySelector("div.article-stats-view__item");
    dateDiv.innerText = showTime;
    dateDiv.setAttribute("title", "Время создания (модификации)");

    const statsView = briefStats.querySelector("div.article-stats-view");
    let viewsDiv = statsView.querySelector("div.article-stats-view__item_no-opacity");
    if (!viewsDiv) {
        viewsDiv = createElement("div", "article-stats-view__item article-stats-view__item_no-opacity");
        statsView.appendChild(viewsDiv);
        const viewsContainer = createElement("div", "article-stats-view__info-container article-stats-view__info-container_loaded");
        viewsDiv.appendChild(viewsContainer);
        viewsContainer.appendChild(createElement("div", "article-stats-view__info-inner article-stats-view__info-inner_with-opacity"));
    }
    const viewsInner = viewsDiv.querySelector("div.article-stats-view__info-inner");
    viewsInner.innerText = "📃 " + numFormat(views);
    viewsInner.setAttribute("title", "Просмотры");

    const timeDiv = createElement("div", "article-stats-view__item");

    if (avgTime > 0) {
        const timeSpan = createElement("span");
        timeSpan.innerText = "  ⌚ " + avgTimeStrHHMMSS;
        timeSpan.setAttribute("title", "Среднее время просмотра " + avgTimeStr);
        timeDiv.appendChild(timeSpan);
        statsView.appendChild(timeDiv);
    }

    const divStat = createElement("div", "article-stats-view__item");
    {
        const spanLink = createElement("span");
        spanLink.innerText = "  🔗 ";
        spanLink.setAttribute("title", "Сокращённая ссылка на публикацию.\nКликните, чтобы скопировать её в буфер обмена.");
        spanLink.addEventListener("click", copyTextToClipboard.bind(null, shortUrl(publisherId, postId)));
        spanLink.style.cursor = "pointer";
        divStat.appendChild(spanLink);
    }

    {
        if (checkNoIndex()) {
            const spanRobot = createElement("span");
            spanRobot.innerText = " 🤖";
            spanRobot.setAttribute("title", "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
                "Публикация не индексируется поисковиками.\n" +
                "Примечание: связь этого тега с показами,\n" +
                "пессимизацией и иными ограничениями канала\n" +
                "официально не подтверждена.");
            divStat.appendChild(spanRobot);
        }
    }
    statsView.appendChild(divStat);
}



async function showStatsVideoOld(data, publisherId) {
    if (data === null) {
        return;
    }

    const zenIds = getZenObject();
    const postId = zenIds !== null ? zenIds.publicationId : data.publication.id;

    const dayMod = dateTimeFormat(data.publication.content.modTime);
    const dayCreate = data.publication.addTime === undefined ? dayMod : dateTimeFormat(data.publication.addTime);
    const showTime = dayMod !== dayCreate ? dayCreate + " (" + dayMod + ")" : dayCreate;
    const articleData = await loadPublicationStat(postId);

    const sumViewTimeSec = articleData.sumViewTimeSec;
    const views = articleData.views;
    //const shows = articleData.shows;
    const viewsTillEnd = articleData.viewsTillEnd;

    const elArticleDate = document.getElementsByClassName("article__date-video")[0];
    elArticleDate.innerText = showTime;

    const container = document.getElementsByClassName("article__about")[0];
    {
        // Просмотры
        //const spanIcon1 = createElement("span", "article__date-video article-stat__icon article-stat__icon_type_book-black");
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
        spanIcon4.addEventListener("click", copyTextToClipboard.bind(null, shortUrl(publisherId)));
        spanIcon4.style.cursor = "pointer";
        container.appendChild(spanIcon4);
    }
}


async function getPublicationStats(data) {
    const zenIds = getZenObject();
    const postId = zenIds !== null ? zenIds.publicationId : data.publication.id;
    let localStats = data.publication.publicationStatistics;
    if (postId != null) {
        const localStatsOld = await loadPublicationStat(postId);
        if (localStats == null) {
            localStats = localStatsOld;
        } else {
            localStats.views = Math.max(localStats.views, localStatsOld.views, 0);
            localStats.viewsTillEnd = Math.max(localStats.viewsTillEnd, localStatsOld.viewsTillEnd, 0);
            localStats.sumViewTimeSec = Math.max(localStats.sumViewTimeSec, localStatsOld.sumViewTimeSec, 0);
            localStats.comments = Math.max(localStats.comments, localStatsOld.comments, 0);
        }
    }
    return localStats;
}


async function showStatsArticleNew(data, publisherId) {
    if (data === null) {
        return;
    }
    const shortLink = document.head.querySelector("link[rel=canonical][href]").href;

    const zenIds = getZenObject();
    const postId = zenIds !== null ? zenIds.publicationId : data.publication.id;

    const dayMod = dateTimeFormat(data.publication.content.modTime);
    const dayCreate = data.publication.addTime === undefined ? dayMod : dateTimeFormat(data.publication.addTime);
    const showTime = dayMod !== dayCreate ? dayCreate + " (" + dayMod + ")" : dayCreate;

    const articleData = await getPublicationStats(data); // await loadPublicationStat(postId);
    if (articleData == null) {
        return;
    }

    const sumViewTimeSec = articleData.sumViewTimeSec != null ? articleData.sumViewTimeSec : 0;
    const views = articleData.views;
    //const shows = articleData.shows;
    const viewsTillEnd = articleData.viewsTillEnd;

    const infoBlock = document.querySelector("div[class^=content--article-info-block__articleInfoBlock-]")
    if (infoBlock == null) return;
    infoBlock.replaceChildren()
    const dateBlock = createElement("div", "article-info-block__addTimeInfo-25");
    dateBlock.innerText = `🕑 ${showTime}`;
    dateBlock.setAttribute("title", "Время создания (редактирования)");
    dateBlock.setAttribute("itemprop", "datePublished");
    infoBlock.appendChild(dateBlock);

    if (views !== viewsTillEnd) {
        const viewsBlock = createElement("div", "article-info-block__viewsInfo-1g");
        viewsBlock.setAttribute("title", "Просмотры");
        /* const viewsSpan = createElement("span", "publication_icon_views_2");
        viewsBlock.appendChild(viewsSpan);*/
        // const viewsText = document.createTextNode(views);
        viewsBlock.innerText = `📃 ${views}`
        infoBlock.appendChild(viewsBlock);
    }

    const viewsTillEndBlock = createElement("div", "article-info-block__viewsInfo-1g");
    viewsTillEndBlock.setAttribute("title", "Дочитывания");
    /*const viewsTillEndSpan = createElement("span", "publication_icon_views_2");
    viewsTillEndBlock.appendChild(viewsTillEndSpan); */
    //const viewsTillEndText = document.createTextNode(viewsTillEnd);
    viewsTillEndBlock.innerText = `📄 ${viewsTillEnd}`;
    infoBlock.appendChild(viewsTillEndBlock);

    if (sumViewTimeSec > 0) {
        const avgTimeBlock = createElement("div", "article-info-block__viewsInfo-1g");
        avgTimeBlock.setAttribute("title", "Среднее время дочитывания");
        /* const avgTimeIcon = createElement("span", "publication_icon_read_time");
        avgTimeBlock.appendChild(avgTimeIcon);*/
        //const avgTimeText = document.createTextNode(secToText(infiniteAndNan(sumViewTimeSec / viewsTillEnd)));
        const avgTime = secToText(infiniteAndNan(sumViewTimeSec / viewsTillEnd));
        avgTimeBlock.innerText = `⌚ ${avgTime}`;
        infoBlock.appendChild(avgTimeBlock);
    }

    if (postId != null) {
        const shortLinkBlock = createElement("div", "article-info-block__viewsInfo-1g");
        shortLinkBlock.setAttribute("title", "Сокращённая ссылка на статью.\nКликните, чтобы скопировать её в буфер обмена.");
        /* const shortLinkIcon = createElement("span", "publication_icon_short_url");
        shortLinkIcon.addEventListener("click", copyTextToClipboard.bind(null, shortLink !== null ? shortLink : shortUrl(publisherId, postId)));
        shortLinkIcon.style.cursor = "pointer";
        shortLinkBlock.appendChild(shortLinkIcon); */
        shortLinkBlock.style.cursor = "pointer";
        shortLinkBlock.innerText = "🔗";
        infoBlock.appendChild(shortLinkBlock);
    }

    if (checkNoIndex()) {
        const sadRobotBlock = createElement("div", "article-info-block__viewsInfo-1g");
        sadRobotBlock.setAttribute("title", "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
            "Публикация не индексируется поисковиками.\n" +
            "Если вы недавно редактировали статью, то это нормально.\n" +
            "Примечание: связь этого тега с показами,\n" +
            "пессимизацией и иными ограничениями канала\n" +
            "официально не подтверждена.");
        sadRobotBlock.innerText = "🤖";
        infoBlock.appendChild(sadRobotBlock);
    }

    addHeaderClicks();
}

async function showStatsArticleOld(data, publisherId) {
    if (data === null) {
        return;
    }

    const shortLink = document.head.querySelector("link[rel=canonical][href]").href;

    const zenIds = getZenObject();
    const postId = zenIds !== null ? zenIds.publicationId : data.publication.id;

    const dayMod = dateTimeFormat(data.publication.content.modTime);
    const dayCreate = data.publication.addTime === undefined ? dayMod : dateTimeFormat(data.publication.addTime);
    const showTime = dayMod !== dayCreate ? dayCreate + " (" + dayMod + ")" : dayCreate;

    const articleData = await getPublicationStats(data); // await loadPublicationStat(postId);
    if (articleData == null) {
        return;
    }

    const sumViewTimeSec = articleData.sumViewTimeSec != null ? articleData.sumViewTimeSec : 0;
    const views = articleData.views;
    //const shows = articleData.shows;
    const viewsTillEnd = articleData.viewsTillEnd;

    const hasAdv = document.getElementsByClassName("article-stats-view__block-item").length; // 1 - рекламная статья, 0 - обычная
    let articleStatsViewRedesignItems = document.getElementsByClassName("article-stats-view__item");
    const elArticleDate = articleStatsViewRedesignItems[hasAdv];
    elArticleDate.innerText = showTime;
    elArticleDate.setAttribute("title", "Время создания (редактирования)");

    if (articleStatsViewRedesignItems.length === 1 + hasAdv) {
        document.getElementsByClassName("article-stats-view article-stats-view_theme_white")[0].appendChild(createElement("div", "article-stats-view__item"));
        articleStatsViewRedesignItems = document.getElementsByClassName("article-stats-view__item");
    }
    const elArticleStats = articleStatsViewRedesignItems[articleStatsViewRedesignItems.length - 1];
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
    const viewsText = createElement("span", "article-stats-view__stats-item-count");
    viewsText.innerText = numFormat(views, 0);
    viewsContainer.appendChild(viewsText);

    containerInner.appendChild(viewsContainer);

    // Дочитывания
    const fullViewsContainer = createElement("div", "article-stats-view__stats-item");
    fullViewsContainer.setAttribute("title", "Дочитывания");
    const fullViewsIcon = createElement("span", "article-stats-view__stats-item-icon publication_icon_full_views");
    fullViewsContainer.appendChild(fullViewsIcon);
    const fullViewsText = createElement("span", "article-stats-view__stats-item-count");
    fullViewsText.innerText = numFormat(viewsTillEnd, 0) + " (" + infiniteAndNan(viewsTillEnd / views * 100).toFixed(2) + "%)";
    fullViewsContainer.appendChild(fullViewsText);

    containerInner.appendChild(fullViewsContainer);

    // Среднее время
    if (sumViewTimeSec > 0) {
        const avgTimeContainer = createElement("div", "article-stats-view__stats-item");
        avgTimeContainer.setAttribute("title", "Среднее время дочитывания");
        const avgTimeIcon = createElement("span", "article-stats-view__stats-item-icon publication_icon_read_time");
        avgTimeContainer.appendChild(avgTimeIcon);
        const avgTimeText = createElement("span", "article-stats-view__stats-item-count");
        avgTimeText.innerText = secToText(infiniteAndNan(sumViewTimeSec / viewsTillEnd));
        avgTimeContainer.appendChild(avgTimeText);

        containerInner.appendChild(avgTimeContainer);
    }

    // Короткая ссылка
    if (postId != null) {
        const shortLinkContainer = createElement("div", "article-stats-view__stats-item");
        shortLinkContainer.setAttribute("title", "Сокращённая ссылка на статью.\nКликните, чтобы скопировать её в буфер обмена.");
        const shortLinkIcon = createElement("span", "publication_icon_short_url");
        shortLinkIcon.addEventListener("click", copyTextToClipboard.bind(null, shortLink !== null ? shortLink : shortUrl(publisherId, postId)));
        shortLinkIcon.style.cursor = "pointer";
        shortLinkContainer.appendChild(shortLinkIcon);
        elArticleStats.appendChild(shortLinkContainer);
    }

    // Грустный робот
    if (checkNoIndex()) {
        const sadRobotContainer = createElement("div", "article-stats-view__stats-item");
        sadRobotContainer.setAttribute("title", "Обнаружен мета-тег <meta name=\"robots\" content=\"noindex\" />\n" +
            "Публикация не индексируется поисковиками.\n" +
            "Если вы недавно редактировали статью, то это нормально.\n" +
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

async function showStatsArticle(data, publisherId) {
    if (document.querySelector("div[class^=content--article-item__sideInfoBlock-]") != null) {
        showStatsArticleNew(data, publisherId);
    } else {
        showStatsArticleOld(data, publisherId);
    }
}