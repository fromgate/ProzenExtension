const OPTIONS = {
    prozen: "prozen-switch",
    shortDashboardRealtime: "prozen-realtime-switch",
    prozenMenu: "prozen-menu-switch",
    informer: "prozen-informer-switch",
    subtitleLinks: "prozen-article-link-switch2",
    commentsWidget: "prozen-comments-switch2",
    promoteShow: "prozen-promote-show"
};

const DISABLED_BY_DEFAULT = [OPTIONS.subtitleLinks, OPTIONS.commentsWidget, OPTIONS.promoteShow];

function getOption(optionId) {
    const optionsIds = Object.values(OPTIONS);
    return new Promise(resolve => {
        chrome.storage.local.get(optionsIds, option => {
            if (option.hasOwnProperty(optionId)) {
                resolve(option[optionId]);
            } else {
                resolve(!DISABLED_BY_DEFAULT.includes(optionId));
            }
        });
    });
}