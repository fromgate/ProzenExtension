window.browser = (function () {
    return window.msBrowser ||
        window.browser ||
        window.chrome;
})();
const nameVersion = document.getElementById("extver");
nameVersion.innerText = nameVersion.innerText.replace("1.0.0", browser.runtime.getManifest().version);
document.getElementById("prozen-image").style.visibility  = "hidden";
showLastPost ();

///////////////////////////////////////////////////////////////////////////////////////////////
async function showLastPost () {
    const channel = new Channel("prodzn", true);
    const lastPost = await channel.getLastPostCard();
    if (lastPost !== undefined) {
        const a = document.getElementById("prozen-post-url");
        a.setAttribute("href",lastPost.link + "?utm_source=extension_popup");
        const img = document.getElementById("prozen-post-image");
        if (lastPost.image !== undefined && lastPost.image !== '') {
            img.setAttribute("src", lastPost.image);
        }
        const title = document.getElementById("prozen-post-title");
        title.innerText = lastPost.title;
    }
    document.getElementById("prozen-image").style.visibility  = "visible";
}