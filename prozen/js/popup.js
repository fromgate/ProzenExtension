window.browser = (function () {
    return window.msBrowser ||
        window.browser ||
        window.chrome;
})();
const nameVersion = document.getElementById("extver");
document.getElementById("prozen-switch").addEventListener('click', onCheckboxClick);

nameVersion.innerText = nameVersion.innerText.replace("1.0.0", browser.runtime.getManifest().version);
document.getElementById("prozen-image").style.visibility = "hidden";

loadExtensionState();
showLastPost();

///////////////////////////////////////////////////////////////////////////////////////////////
async function showLastPost() {
    const channel = new Channel("prodzn", true);
    const lastPost = await channel.getLastPostCard();
    if (lastPost !== undefined) {
        const a = document.getElementById("prozen-post-url");
        a.setAttribute("href", lastPost.link + "?utm_source=extension_popup");
        const img = document.getElementById("prozen-post-image");
        if (lastPost.image !== undefined && lastPost.image !== '') {
            img.setAttribute("src", lastPost.image);
        }
        const title = document.getElementById("prozen-post-title");
        title.innerText = lastPost.title;
    }
    document.getElementById("prozen-image").style.visibility = "visible";
}


function onCheckboxClick() {
    setCheckbox (document.getElementById("prozen-switch").checked)
}

function setCheckbox (switchState, save=true) {
    document.getElementById("prozen-switch").checked = switchState;
    const text = switchState ? "Расширение включено" : "Расширение отключено";
    const divSwitch = document.getElementById("prozen-switch-text");
    divSwitch.innerText = text
    divSwitch.style.fontWeight = switchState ? "500" : "normal";
    saveExtensionState(switchState);
}


function loadExtensionState() {
        chrome.storage.local.get("prozenEnabled", function (result) {
            if (result.prozenEnabled === undefined) {
                setCheckbox(true)
            } else {
                setCheckbox(result.prozenEnabled, false)
            }
        });
}

function saveExtensionState(switchState) {
    chrome.storage.local.set({prozenEnabled: document.getElementById("prozen-switch").checked}, function () {
    });
}