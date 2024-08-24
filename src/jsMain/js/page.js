sendProzenData();

window.addEventListener("message", event => {
    if (event.source !== window) {
        return;
    }
    if (event.data.type && (event.data.type === "prozen-request")) {
        sendProzenData();
    }
});

function sendProzenData() {
    const data = {
        type: "prozen-data",
        text: window._csrfToken,
        jsonData: window._data
    };
    window.postMessage(data, "*");
}