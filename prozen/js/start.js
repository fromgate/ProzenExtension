const body = document.getElementsByTagName("body")[0];
const script = document.createElement("script");
script.setAttribute("type", "text/javascript");
script.setAttribute("src", chrome.extension.getURL("/js/content.js"));
body.appendChild(script);