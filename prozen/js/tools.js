const DEBUG = false;

/**
 * @deprecated Dzen doesn't use publisherId and publicationId in publication URL anymore. Use zenObjectId meta instead.
 */
function getPostIdFromUrl(link) {
    const url = link.endsWith("/") ? link.substring(0, link.length - 1) : link;
    const ln = url.replace("?from=editor", "").split(url.includes("-") ? "-" : "/");
    return ln[ln.length - 1];
}

function getZenObject() {
    const zenObjectIdStr = document.head.querySelector("meta[property=zen_object_id][content]").content;
    const zenObjectArray = zenObjectIdStr?.split(":", 2);
    if (zenObjectArray == null || zenObjectArray.length !== 2) return null;
    return {
        publisherId: zenObjectArray[0],
        publicationId: zenObjectArray[1]
    };
}

function dateTimeFormat(unixTime) {
    const date = new Date(unixTime);
    const day = "0" + date.getDate();
    const month = "0" + (date.getMonth() + 1);
    const year = "" + date.getFullYear();
    const hours = "0" + date.getHours();
    const minutes = "0" + date.getMinutes();
    return day.substr(-2) + "." + month.substr(-2) + "."
        + year.substr(-2) + "\u00A0" + hours.substr(-2) + ":" + minutes.substr(-2);
}

function secToHHMMSS(seconds) {
    let time = seconds;
    const hours = Math.floor(time / 3600);
    time = time % 3600;
    const min = ("0" + Math.floor(time / 60)).substr(-2);
    const sec = ("0" + Math.floor(time % 60)).substr(-2);

    if (isNaN(hours) || isNaN(min) || isNaN(sec)) return "0";
    return (hours > 0 ? hours + ":" : "") + min + ":" + sec;
}

function secToText(seconds) {
    let time = seconds;
    const hours = Math.floor(time / 3600);
    time = time % 3600;
    const min = Math.floor(time / 60);
    const sec = Math.floor(time % 60);
    if (isNaN(hours) || isNaN(min) || isNaN(sec)) return "не определено";
    return (hours > 0 ? hours + " час " : "") + (min > 0 ? min + " мин " : "") + sec + " сек";
}

function joinByThree(list) {
    let text = "";
    for (let i = 0; i < list.length; i++) {
        if (i === 0) {
            text = list[i];
        } else if ((i / 3) === Math.floor(i / 3)) {
            text = text + ",\n" + list[i];
        } else {
            text = text + ", " + list[i];
        }
    }
    return text;
}

function infiniteAndNan(number) {
    return isNaN(number) ? 0 : (isFinite(number) ? number : 0);
}

function firstNotZ(a, b, c) {
    if (a !== 0) {
        return a;
    }
    if (b !== 0) {
        return b;
    }
    return c;
}

function createElement(elementType, elementClass, childElement) {
    const newElement = document.createElement(elementType);
    if (elementClass !== undefined) {
        newElement.setAttribute("class", elementClass);
    }
    if (childElement !== undefined) {
        newElement.appendChild(childElement);
    }
    return newElement;
}

function infiniteAndNanToStr(num, digits) {
    return infiniteAndNan(num).toLocaleString("ru-RU", {maximumFractionDigits: digits === undefined ? 0 : digits});
}

function removeChilds(element) {
    while (element.firstChild) {
        element.removeChild(element.firstChild);
    }
}

function removeByClass(className) {
    const elements = document.getElementsByClassName(className);
    while (elements.length > 0) {
        elements[0].parentNode.removeChild(elements[0]);
    }
}

function numFormat(num, digits) {
    return num.toLocaleString("ru-RU", {maximumFractionDigits: digits === undefined ? 0 : digits});
}

function paucalYear(num) {
    return paucal(num, "год", "года", "лет");
}

function paucalMonth(num) {
    return paucal(num, "месяц", "месяца", "месяцев");
}

function paucalDay(num) {
    return paucal(num, "день", "дня", "дней");
}

function paucal(num, p1, p234, p) {
    const x = num % 100;
    if (x >= 10 && x < 20) {
        return num + " " + p;
    }
    const numStr = infiniteAndNanToStr(num, 0);
    switch (num % 10) {
        case 1:
            return numStr + " " + p1;
        case 2:
        case 3:
        case 4:
            return numStr + " " + p234;
        default:
            return numStr + " " + p;
    }
}

function copyTextToClipboard(text) {
    const copyFrom = document.createElement("textarea");
    copyFrom.setAttribute("readonly", "");
    copyFrom.style.position = "absolute";
    copyFrom.style.left = "-9999px";
    copyFrom.style.too = "0px";
    copyFrom.textContent = text;
    document.body.appendChild(copyFrom);
    copyFrom.select();
    document.execCommand("copy");
    copyFrom.blur();
    document.body.removeChild(copyFrom);
}

function openUrl(url) {
    window.open(url);
}

function checkNoIndex() {
    const metas = document.querySelectorAll("meta[name=robots]");
    let noindex = false;
    for (const meta of metas) {
        if (meta.content.includes("noindex")) {
            noindex = true;
            break;
        }
    }
    return noindex;
}

// Это тег больше не используется
// <meta property="robots" content="none" />
function checkNone() {
    const metas = document.getElementsByTagName("meta");
    let none = false;
    for (const meta of metas) {
        if (meta.getAttribute("property") === "robots"
            && meta.getAttribute("content") === "none") {
            none = true;
        }
    }
    return none;
}

function shortUrl(publisherId, publicationId) {
    let url = window.location.href.split("\?")[0].split("#")[0];
    url = url.substr(0, url.lastIndexOf("/")) + "/" + url.substr(url.lastIndexOf("-") + 1, url.length - 1);
    if (publisherId != null) {
        const urlParts = url.split("/");
        const id = publicationId == null ? urlParts[urlParts.length - 1] : publicationId;
        url = `https://dzen.ru/media/id/${publisherId}/${id}`;
    }
    return url;
}

function dateFormat(unixTime, showTime = true) {
    const date = new Date(unixTime);
    const day = "0" + date.getDate();
    const month = "0" + (date.getMonth() + 1);
    const year = "" + date.getFullYear();
    const hours = "0" + date.getHours();
    const minutes = "0" + date.getMinutes();
    let dateStr = day.substr(-2) + "." + month.substr(-2) + "."
        + year.substr(-2);
    if (showTime) dateStr = dateStr + "\u00A0" + hours.substr(-2) + ":" + minutes.substr(-2);
    return dateStr;
}

function daysSinceDate(unixTime) {
    const date1 = new Date(unixTime).getTime();
    const date2 = new Date().getTime();
    const oneDay = 1000 * 60 * 60 * 24;
    const difference = date2 - date1;
    return Math.round(difference / oneDay);
}

function daysReadable(daysInterval) {
    let days = daysInterval;
    const years = Math.floor(days / 365);
    days = days % 365;
    const months = Math.floor(days / 30);
    days = days % 30;
    return paucalYear(years) + ", " + paucalMonth(months) + ", " + paucalDay(days);
}

function debug(message, message2) {
    if (DEBUG) {
        let str = "[ПРОДЗЕН]: " + message;
        if (message2 !== undefined) {
            str += " " + message2;
        }
        console.log(str);
    }
}

function log(message) {
    if (DEBUG) {
        console.log(message);
    }
}

