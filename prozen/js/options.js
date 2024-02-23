const OPTIONS = {
    prozen: "prozen-switch",
    // subtitleLinks: "prozen-article-link-switch",
    shortDashboardRealtime: "prozen-realtime-switch",
    prozenMenu: "prozen-menu-switch",
    informer: "prozen-informer-switch",
    subtitleLinks: "prozen-article-link-switch2"
    // commentsWidget: "prozen-comments-widget-switch"
}

const DISABLED_BY_DEFAULT = [OPTIONS.subtitleLinks]

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