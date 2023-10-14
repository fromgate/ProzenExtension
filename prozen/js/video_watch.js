start();

function start() {
    getOption(OPTIONS.prozen).then(enabled => {
        if (enabled) {
            if (!document.getElementById("prozen-css")) {
                const css = document.createElement("link");
                css.setAttribute("rel", "stylesheet");
                css.setAttribute("type", "text/css");
                css.id = "prozen-css";
                css.setAttribute("href", chrome.extension.getURL("css/prozen.css"));
                document.head.appendChild(css);
            }
            setTimeout(showStatsVideo, 1000)

        }
    });
}


async function showStatsVideo() {
    const videoId = getPostIdFromUrl(window.location.pathname);
    const videoData = await loadPublicationStat(videoId);

    const sumViewTimeSec = videoData.sumViewTimeSec;
    const views = videoData.views;
    const viewsTillEnd = videoData.viewsTillEnd;

    const statsDiv = document.querySelector("div.card-channel-info__description");

    const spanCreateTime = createElement("span");
    spanCreateTime.innerText = statsDiv.innerText;
    statsDiv.innerText = "";
    statsDiv.appendChild(spanCreateTime);

    const spanViews = createElement("span");
    spanViews.innerText = `  • 📺 ${views.toLocaleString("ru-RU", {maximumFractionDigits: 0})}`;
    spanViews.setAttribute("title", "Просмотры");
    statsDiv.appendChild(spanViews);

    if (sumViewTimeSec > 0) {
        const spanTime = createElement("span");
        spanTime.innerText = `  • ⌚ ${secToText(infiniteAndNan(sumViewTimeSec / viewsTillEnd))}`;
        spanTime.setAttribute("title", "Среднее время просмотра");
        statsDiv.appendChild(spanTime);
    }

    if (checkNone()) {
        const spanSadRobot = createElement("span");
        spanSadRobot.innerText = " • 🤖";
        spanSadRobot.setAttribute("title", "Обнаружен мета-тег <meta property=\"robots\" content=\"none\" />\n" +
            "Публикация не индексируется поисковиками.\n" +
            "Примечание: связь этого тега с показами,\n" +
            "пессимизацией и иными ограничениями канала\n" +
            "официально не подтверждена.");
        statsDiv.appendChild(spanSadRobot);
    }
}