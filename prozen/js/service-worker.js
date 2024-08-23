isProzenEnabled().then(enabled => {
    if (enabled) {
        registerWebRequestListener();
        registerMainPageRequestListener();
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
            const urlParams = new URL(details.url).searchParams;
            details.requestHeaders.forEach(header => {
                if (header.name === "X-Csrf-Token") {
                    token = header.value;
                }
                if (header.name === "X-Prozen-Request") {
                    prozenRequest = true;
                }
            });
            if (!prozenRequest) {
                chrome.tabs.sendMessage(details.tabId, {
                    type: "prozen-webrequest",
                    url: details.url,
                    publicationIdAfter: urlParams.has("publicationIdAfter") ? urlParams.get("publicationIdAfter") : null,
                    publisherId: urlParams.get("publisherId"),
                    pageSize: urlParams.get("pageSize"),
                    types: urlParams.has("types") ? urlParams.get("types") : null,
                    view: urlParams.has("view") ? urlParams.get("view") : null,
                    query: urlParams.has("query") ? urlParams.get("query") : null,
                    token: token
                });
            }
        }, {
            urls: [
                "https://dzen.ru/editor-api/v2/get-publications-by-filter?group=published&publisherId=*"
            ]
        },
        ["requestHeaders"]);
}

function registerMainPageRequestListener() {
    chrome.webRequest.onBeforeSendHeaders.addListener(details => {
        let token = null;
        let prozenRequest = false;
        const urlParams = new URL(details.url).searchParams;
        details.requestHeaders.forEach(header => {
            if (header.name === "X-Csrf-Token") {
                token = header.value;
            }
            if (header.name === "X-Prozen-Request") {
                prozenRequest = true;
            }
        });
        if (!prozenRequest) {
            chrome.tabs.sendMessage(details.tabId, {
                type: "prozen-mainpage-request",
                url: details.url,
                publisherId: urlParams.get("publisherId"),
                pageSize: urlParams.get("pageSize"),
                types: urlParams.has("types") ? urlParams.get("types") : null,
                view: urlParams.has("view") ? urlParams.get("view") : null,
                query: urlParams.has("query") ? urlParams.get("query") : null,
                publicationIdAfter: urlParams.has("publicationIdAfter") ? urlParams.get("publicationIdAfter") : null,
                state: urlParams.get("state"),
                token: token
            });
        }
    }, {
        urls: [
            "https://dzen.ru/editor-api/v3/publications?*" //"https://dzen.ru/editor-api/v3/publications?publisherId=*"
        ]
    }, ["requestHeaders"]);
}
