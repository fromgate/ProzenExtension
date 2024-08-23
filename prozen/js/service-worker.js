isProzenEnabled().then(enabled => {
    if (enabled) {
        registerWebRequestListener();
        registerMainPageRequestListener();
    }
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === "keepAlive") {
        sendResponse({status: "Prozen Service worker is active"});
    }
    return true;
});

function isProzenEnabled() {
    return new Promise(resolve => {
        chrome.storage.local.get(["prozen-switch"], option => {
            resolve(option["prozen-switch"] !== undefined ? option["prozen-switch"] : true);
        });
    });
}

function registerWebRequestListener() {
    chrome.webRequest.onBeforeSendHeaders.addListener(details => {
        handleRequest(details, "prozen-webrequest");
    }, {
        urls: [
            "https://dzen.ru/editor-api/v2/get-publications-by-filter?group=published&publisherId=*"
        ]
    }, ["requestHeaders"]);
}

function registerMainPageRequestListener() {
    chrome.webRequest.onBeforeSendHeaders.addListener(details => {
        handleRequest(details, "prozen-mainpage-request");
    }, {
        urls: [
            "https://dzen.ru/editor-api/v3/publications?*"
        ]
    }, ["requestHeaders"]);
}

function handleRequest(details, messageType) {
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
            type: messageType,
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
}
