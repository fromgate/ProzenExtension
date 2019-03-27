const data = {
    type: "prozen-data",
    text: window._csrfToken,
    jsonData: window._data
};
window.postMessage(data, "*");