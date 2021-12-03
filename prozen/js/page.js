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


/*
(function () {
    var results, currentWindow,
        // create an iframe and append to body to load a clean window object
        iframe = document.createElement('iframe');
    iframe.style.display = 'none';
    document.body.appendChild(iframe);
    // get the current list of properties on window
    currentWindow = Object.getOwnPropertyNames(window);
    // filter the list against the properties that exist in the clean window
    results = currentWindow.filter(function(prop) {
        return !iframe.contentWindow.hasOwnProperty(prop);
    });
    // log an array of properties that are different
    console.log(results);
    document.body.removeChild(iframe);

    console.log(window["YandexZen"]);
    console.log(window["Ya"]);
}()); */

