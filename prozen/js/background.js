isProzenEnabled().then(enabled => {
    console.log("Enabled: " + enabled);
    if (enabled) {
        registerWebrequestListener();
    }
});

function isProzenEnabled() {
    return new Promise(resolve => {
        chrome.storage.local.get(["prozen-switch"], option => {
            if (option.hasOwnProperty("prozen-switch")) {
                resolve(option["prozen-switch"]);
            } else {
                resolve(true);
            }
        });
    });
}

function registerWebrequestListener() {
    chrome.webRequest.onBeforeSendHeaders.addListener(details => {
            let token = null;
            let prozenRequest = false;
            const urlParams = new URLSearchParams(details.url);
            details.requestHeaders.forEach(header => {
                if (header.name === "X-Csrf-Token") {
                    token = header.value;
                }
                if (header.name === "X-Prozen-Request") {
                    prozenRequest = true;
                }
            });
            if (!prozenRequest) {
                data = {
                    type: "prozen-webrequest",
                    url: details.url,
                    publisherId: urlParams.get("publisherId"),
                    pageSize: urlParams.get("pageSize"),
                    token: token
                };
                chrome.tabs.sendMessage(details.tabId, data);
            }
        }, {urls: ["https://zen.yandex.ru/editor-api/v2/get-publications-by-filter?group=published&publisherId=*&pageSize=5"]},
        ["requestHeaders"]);
}

// Запрос при отправке
//https://zen.yandex.ru/media-api/publication-view-stat?publicationId=61211a41962db963dbf5eeca