window.browser = (function () {
    return window.msBrowser ||
        window.browser ||
        window.chrome;
})();
const nameVersion = document.getElementById("extver");
nameVersion.innerText = nameVersion.innerText.replace("1.0.0", browser.runtime.getManifest().version);

