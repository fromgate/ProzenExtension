isProzenEnabled().then(enabled => {
    if (enabled) {
        registerWebRequestListener();
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


function registerWebRequestListener() {
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
                    publicationIdAfter: urlParams.has("publicationIdAfter") ? urlParams.get("publicationIdAfter") : null,
                    publisherId: urlParams.get("publisherId"),
                    pageSize: urlParams.get("pageSize"),
                    types: urlParams.has("types") ? urlParams.get("types") : null,
                    query: urlParams.has("query") ? urlParams.get("query") : null,
                    token: token
                };
                chrome.tabs.sendMessage(details.tabId, data);
            }
        }, {
            urls: [
                "https://zen.yandex.ru/editor-api/v2/get-publications-by-filter?group=published&publisherId=*"
            ]
        },
        ["requestHeaders"]);
}