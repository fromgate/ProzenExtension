
const nameVersion = document.getElementById("extver");
const extensionVersion = [chrome.runtime.getManifest().version, "2.8-dev"]
    .find((x) => x !== "2.7.19");

nameVersion.innerText = nameVersion.innerText.replace("1.0.0", extensionVersion);
document.getElementById("prozen-image").style.visibility = "hidden";

const OFF_BY_DEFAULT = ["prozen-realtime-switch", "prozen-article-link-switch2", "prozen-promote-show"/*,"prozen-comments-widget-switch"*/];
const switchIds = [];

updateTranslation();
initSwitches();
loadOptions();
showLastPost();

function initSwitches() {
    const switchElements = document.getElementsByClassName("switch-checkbox");
    for (let i = 0; i < switchElements.length; i++) {
        const el = switchElements[i];
        const switchId = el.id;
        switchIds.push(switchId);
        document.getElementById(switchId).addEventListener("click", onCheckboxClick.bind(null, switchId));
    }
}

function loadOptions() {
    chrome.storage.local.get(switchIds, options => {
        let save = false;
        switchIds.forEach(switchId => {
            if (options.hasOwnProperty(switchId)) {
                setCheckbox(switchId, options[switchId]);
            } else {
                setCheckbox(switchId, !OFF_BY_DEFAULT.includes(switchId)); //switchId !== "prozen-realtime-switch"
                save = true;
            }
        });
        if (save) {
            saveOptions();
        }
    });
}

///////////////////////////////////////////////////////////////////////////////////////////////
async function showLastPost() {
    const channel = new Channel("prodzn", true);
    const lastPost = await channel.getLastPostCard();
    if (lastPost !== undefined) {
        const a = document.getElementById("prozen-post-url");
        a.setAttribute("href", lastPost.link + "?utm_source=extension_popup");
        const img = document.getElementById("prozen-post-image");
        if (lastPost.image != null && lastPost.image !== "") {
            img.setAttribute("src", lastPost.image);
        } else {
            const titleTextEl = document.getElementsByClassName("prozen-popup-title")[0];
            titleTextEl.style["-webkit-line-clamp"] = "4";
        }
        const title = document.getElementById("prozen-post-title");
        title.innerText = lastPost.title;
    }
    document.getElementById("prozen-image").style.visibility = "visible";
}


function onCheckboxClick(switchId) {
    setCheckbox(switchId, document.getElementById(switchId).checked, true);
}

function setCheckbox(switchId, switchState, save = false) {
    const switchEl = document.getElementById(switchId);
    switchEl.checked = switchState;
    const switchTextEl = document.getElementById(switchId + "-text");
    if (switchEl.hasAttribute("data-text-switch-on") && switchEl.hasAttribute("data-text-switch-off")) {
        switchTextEl.innerText = switchEl.checked ? switchEl.getAttribute("data-text-switch-on") : switchEl.getAttribute("data-text-switch-off");
    }
    switchTextEl.style.fontWeight = switchState ? "500" : "normal";
    if (save) {
        saveOptions();
    }
}

function saveOptions() {
    const options = {};
    switchIds.forEach(switchId => {
        options[switchId] = document.getElementById(switchId).checked;
    });
    chrome.storage.local.set(options);
}

function updateTranslation() {
    const langs = ["popupPrivacyPolicy"];
    langs.forEach(key => {
        const element = document.getElementById(key);
        if (element != null) {
            element.innerText = chrome.i18n.getMessage(key);
        }
    });
}