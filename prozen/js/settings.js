
const nameVersion = document.getElementById("extver");
nameVersion.innerText = nameVersion.innerText.replace("1.0.0", chrome.runtime.getManifest().version);

const OFF_BY_DEFAULT = ["prozen-realtime-switch" , "prozen-article-link-switch2", "prozen-comments-switch"/*,"prozen-comments-widget-switch"*/];
const switchIds = [];

initSwitches();
loadOptions();

function initSwitches() {
    const switchElements = document.getElementsByClassName("switch-checkbox");
    for (let i = 0; i < switchElements.length; i++) {
        const el = switchElements[i];
        const switchId = el.id;
        switchIds.push(switchId);
        document.getElementById(switchId).addEventListener('click', onCheckboxClick.bind(null, switchId));
    }
}

function loadOptions() {
    chrome.storage.local.get(switchIds, options => {
        let save = false;
        switchIds.forEach(switchId => {
            if (options.hasOwnProperty(switchId)) {
                setCheckbox(switchId, options[switchId])
            } else {
                setCheckbox(switchId, !OFF_BY_DEFAULT.includes(switchId)); //switchId !== "prozen-realtime-switch"
                save = true;
            }
        })
        if (save) {
            saveOptions();
        }
    });
}

///////////////////////////////////////////////////////////////////////////////////////////////

function onCheckboxClick(switchId) {
    setCheckbox(switchId, document.getElementById(switchId).checked, true)
}

function setCheckbox(switchId, switchState, save = false) {
    const switchEl = document.getElementById(switchId)
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
    const options = {}
    switchIds.forEach(switchId => {
        options[switchId] = document.getElementById(switchId).checked;
    })
    chrome.storage.local.set(options);
}