const css = document.createElement("link");
css.setAttribute("rel", "stylesheet");
css.setAttribute("type", "text/css");

css.setAttribute("href", chrome.extension.getURL("/css/icons.css"));
document.head.appendChild(css);

const script = document.createElement("script");
script.setAttribute("type","text/javascript");
script.setAttribute("src", chrome.extension.getURL("/js/content.js"));
document.body.appendChild(script);

