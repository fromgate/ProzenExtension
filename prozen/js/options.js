const OPTIONS = {
    prozen: "prozen-switch",
    subtitleLinks: "prozen-article-link-switch",
    shortDashboardRealtime: "prozen-realtime-switch",
    prozenMenu: "prozen-menu-switch",
    informer: "prozen-informer-switch",
    commentsWidget: "prozen-comments-widget-switch"
}

function getOption(optionId) {
    const optionsIds = Object.values(OPTIONS);
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