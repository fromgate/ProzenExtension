let token;
let publisherId;
let publications;

const picker = new Litepicker({
    element: document.getElementById("start-date"),
    elementEnd: document.getElementById("end-date"),
    singleMode: false,
    dropdowns: {"minYear": 2017, "months": false, "years": true},
    numberOfColumns: 2,
    numberOfMonths: 2,
    format: "DD-MM-YYYY",
    lang: "ru-RU",
    tooltipText: {one: "день", few: "дня", many: "дней"},
    position: "bottom left",
    allowRepick: true,
    plugins: ["ranges"],
    ranges: {
        position: "left",
        customRanges: getCustomRanges()
    }
});

picker.getDayAfterEnd = function () {
    const dateEnd = this.getEndDate();
    dateEnd.setDate(dateEnd.getDate() + 1);
    return dateEnd;
};

main();

//////////////////////////////////////////////////////////////////////////////////////

async function main() {
    picker.setDateRange("30-05-2017", Date());
    document.getElementById("range-filter").onclick = showStatsByRange;
    document.getElementById("excel-export").onclick = excelExport;
    showSpinner();
    token = await getToken();
    publisherId = await getPublisherId();
    publications = await loadAllPublications(true);
    const stats = countStats();
    showStats(stats);
    hideSpinner();

}

function getToken() {
    return new Promise(resolve => {
        chrome.storage.local.get("prozenToken", result => {
            resolve(result.prozenToken);
        });
    });
}

function getPublisherId() {
    return new Promise((resolve) => {
        chrome.storage.local.get("prozenPublisherId", result => {
            resolve(result.prozenPublisherId);
        });
    });
}

function getCustomRanges() {
    const ranges = {};
    const today = new Date();
    const year = today.getFullYear();

    ranges["Текущий месяц"] = [new Date(new Date().setDate(1)), today];
    ranges["Прошлый месяц"] = [new Date(today.getFullYear(), today.getMonth() - 1), new Date(today.getFullYear(), today.getMonth())];
    ranges["Последние 30 дней"] = [new Date(new Date().setDate(today.getDate() - 30)), today];
    ranges["Последние 180 дней"] = [new Date(new Date().setDate(today.getDate() - 180)), today];
    ranges[year.toString() + " год"] = [new Date(year, 0), today];
    ranges[(year - 1).toString() + " год"] = [new Date(year - 1, 0), new Date(year, 0)];
    ranges[(year - 2).toString() + " год"] = [new Date(year - 2, 0), new Date(year - 1, 0)];
    ranges[(year - 3).toString() + " год"] = [new Date(year - 3, 0), new Date(year - 2, 0)];
    ranges["Всё время"] = [new Date(2017, 4, 30), today];
    return ranges;
}

function showStats(stats) {
    let fieldsets = 0;
    stats.forEach((stat, publicationType) => {
        if (stat.count === 0) {
            showFieldset(publicationType, false);
        } else {
            showFieldset(publicationType, true);
            fieldsets++;
            document.getElementById(publicationType + "-count").textContent = numFormat(stat.count) + stat.publicationsPerDayStr();
            document.getElementById(publicationType + "-impressions").textContent = numFormat(stat.impressions);
            document.getElementById(publicationType + "-clicks").textContent = numFormat(stat.clicks) + stat.clicksPercentStr(publicationType);
            document.getElementById(publicationType + "-typeSpecificViews").textContent = numFormat(stat.typeSpecificViews) + stat.typeSpecificViewsPercentStr(publicationType);
            document.getElementById(publicationType + "-firstpost").textContent = stat.minAddTimeStr();
        }
    });
    showNotFound(fieldsets === 0);
}

function getMedian(arrOfNums) {
    let result;
    const sortedArr = arrOfNums.sort((num1, num2) => num1 - num2);
    const medianIndex = Math.floor(sortedArr.length / 2);
    if (arrOfNums.length % 2 === 0) {
        result = (sortedArr[medianIndex - 1] + sortedArr[medianIndex]) / 2;
    } else {
        result = sortedArr[medianIndex];
    }
    return result;
}

function countStats() {
    const stats = new Map();
    for (let i = 0; i < TYPES.length; i++) {
        stats.set(TYPES[i], new Stat());
    }
    if (publications == null) {
        return stats;
    }

    const dateStart = picker.getStartDate();
    const dateEnd = picker.getDayAfterEnd();// .getEndDate();
    //dateEnd.setDate(dateEnd.getDate() + 1);


    for (let i = 0; i < publications.length; i++) {
        const publication = publications[i];
        if (publication.addTime < dateStart.getTime() || publication.addTime > dateEnd.getTime()) {
            continue;
        }
        const type = publication.type; //publication.content.type;
        if (type === "story") {
            continue;
        }
        const stat = stats.get(type);
        stat.count++;
        stat.plus(publication);
        stats.set(type, stat);
    }

    // Count totals stats
    const total = new Stat();

    stats.forEach((stat, type) => {
        total.plus(stat);
    });
    stats.set("total", total);
    return stats;
}

/**
 * returns HH:MM:SS / MM:SS
 * see withHours boolean parameter
 */
function getStrTime(secondsTot, withHours) {
    if (isNaN(parseInt(secondsTot))) {
        return 0;
    }

    secondsTot = parseInt(secondsTot);

    let res = !!withHours ? "HH:MM:SS" : "MM:SS";
    let minutesTot = parseInt(secondsTot / 60);
    let _roundSec = minutesTot * 60;
    let sec = secondsTot - _roundSec;
    let strSec = String(sec).padStart(2, "0");

    let hours = parseInt(minutesTot / 60);
    let _roundMin = hours * 60;
    let min = minutesTot - _roundMin;
    let strMin = String(min).padStart(2, "0");
    let strHours = String(hours).padStart(2, "0");

    res = res.replace("SS", strSec).replace("MM", strMin).replace("HH", strHours);
    return res;
}


function getExcelData(type) {

    if (!publications) {
        return ["no result"];
    }

    let pubs = publications.filter(pub => pub.type === type);

    let arResult = [];

    switch (type) {
        case "article":
        case "gif":
        case "short_video":
            let firstCol = type === "article" ? "Статья" : (type === "gif" ? "Видео" : "Ролики");
            arResult.push([firstCol, "Показы", "Клики", type === "article" ? "Дочитывания" : "Просмотры", "Время (среднее)"]);

            pubs.map(function (article) {
                let arRow = [];
                arRow.push(article.title);
                arRow.push(article.impressions);
                arRow.push(article.clicks);
                arRow.push(article.typeSpecificViews);
                arRow.push(getStrTime(article.sumViewTimeSec / article.typeSpecificViews, false));
                arResult.push(arRow);
            });

            break;
        case "brief":
            arResult.push(["Пост", "Показы", "Клики", "Просмотры"]);

            pubs.map(function (article) {
                let arRow = [];
                arRow.push(article.title == null || article.title.length === 0 ? article.url : article.title);
                arRow.push(article.impressions);
                arRow.push(article.clicks);
                arRow.push(article.typeSpecificViews);
                arResult.push(arRow);
            });

            break;

        default:
            break;
    }

    return arResult;

}

function s2ab(s) {
    var buf = new ArrayBuffer(s.length);
    var view = new Uint8Array(buf);
    for (var i = 0; i < s.length; i++) view[i] = s.charCodeAt(i) & 0xFF;
    return buf;
}

function excelExport() {
    document.getElementById("excel-msg").innerText = "Waiting excel generation...";


    generateExcelExport().then(function () {
        document.getElementById("excel-msg").innerText = "";
    })
        .catch(function (e) {
            document.getElementById("excel-msg").innerText = e;

        });
}

function generateExcelExport() {

    return new Promise(function (resolve, reject) {

        try {
            let fileExcel = XLSX.utils.book_new();

            const cfgSheets = {
                article: "Статьи",
                brief: "Посты",
                gif: "Видео",
                short_video: "Ролики"
            };

            fileExcel.Props = {
                Title: "PROZEN stats",
                Subject: "Stats",
                Author: "PROZEN ext",
                CreatedDate: new Date()
            };

            Object.keys(cfgSheets).map(function (sheet) {
                fileExcel.SheetNames.push(cfgSheets[sheet]);
                let arData = getExcelData(sheet);
                fileExcel.Sheets[cfgSheets[sheet]] = XLSX.utils.aoa_to_sheet(arData);
            });

            const fileExcelOutput = XLSX.write(fileExcel, {bookType: "xlsx", type: "binary"});
            let now = new Date();

            // YYYYMMDDTHHMMSS
            let strTimestamp = now.getFullYear()
                + String(now.getMonth() + 1).padStart(2, "0")
                + String(now.getDate()).padStart(2, "0")
                + "T"
                + String(now.getHours()).padStart(2, "0")
                + String(now.getMinutes()).padStart(2, "0")
                + String(now.getSeconds()).padStart(2, "0")
            ;
            saveAs(new Blob([s2ab(fileExcelOutput)], {type: "application/octet-stream"}), "prozen-stats-" + strTimestamp + ".xlsx");
            resolve(true);

        } catch (e) {
            reject("ERROR in excel export: " + e.message);
        }

    });


}


function hideSpinner() {
    document.getElementById("spinner").style.display = "none";
    document.getElementById("stats").style.display = "block";
    document.getElementById("records_count").innerText = "";
}

function showSpinner() {
    document.getElementById("spinner").style.display = "block";
    document.getElementById("stats").style.display = "none";
    document.getElementById("records_count").innerText = "";
}

function showStatsByRange() {
    if (publications) {
        const stats = countStats(publications);
        showStats(stats);
    }
}

function showFieldset(type, show) {
    const fieldset = document.getElementById(`${type}-fieldset`);
    fieldset.style.display = show ? "block" : "none";
}

function showNotFound(show) {
    document.getElementById("not-found").style.display = show ? "block" : "none";
}

class Stat {
    constructor() {
        this.count = 0;
        this.impressions = 0;
        this.impressionsMed = 0;
        this.impressionsAvg = 0;
        this.clicks = 0;
        this.clicksMed = 0;
        this.clicksAvg = 0;
        this.typeSpecificViews = 0;
        this.typeSpecificViewsMed = 0;
        this.typeSpecificViewsAvg = 0;
        this.deepViews = 0;
        this.deepViewsMed = 0;
        this.deepViewsAvg = 0;
        this.minAddTime = 0;
    }

    plus(publication) {
        for (const key of Object.keys(this)) {
            if (publication.hasOwnProperty(key)) {
                if (key !== "minAddTime") {
                    this[key] += publication [key];
                }
            }
        }
        const pubMinAddTime = publication.hasOwnProperty("addTime") ? publication.addTime : publication.minAddTime;
        this.minAddTime = (pubMinAddTime != null) && (pubMinAddTime > 0) && (pubMinAddTime < this.minAddTime || this.minAddTime === 0) ? pubMinAddTime : this.minAddTime;
    }

    clicksPercent(publicationType = "article") {
        return this.impressions === 0 || publicationType !== "article" ? 0 : this.typeSpecificViews / this.impressions * 100;
        //return this.impressions === 0 ? 0 : this.typeSpecificViews / this.impressions * 100;
    }

    clicksPercentStr() {
        const clicksPercent = this.clicksPercent();
        return clicksPercent === 0 ? "" : " (" + numFormat(clicksPercent, 2) + "%)";
    }

    typeSpecificViewsPercent() {
        return this.typeSpecificViews === 0 ? 0 : this.typeSpecificViews / this.clicks * 100;
    }

    typeSpecificViewsPercentStr(publicationType = "article") {
        const typeSpecificViewsPercent = this.typeSpecificViewsPercent();
        return publicationType !== "article" || typeSpecificViewsPercent === 0 ? "" : " (" + numFormat(typeSpecificViewsPercent, 2) + "%)";
    }

    minAddTimeStr() {
        let minAddTimeStr = "-";
        const days = daysSinceDate(this.minAddTime);
        if (this.minAddTime > 0) {
            minAddTimeStr = dateFormat(this.minAddTime);
            if (days > 0) {
                minAddTimeStr += "; Прошло: " + paucalDay(days) + " (" + daysReadable(days) + ")";
            }
        }
        return minAddTimeStr;
    }

    publicationsPerDayStr() {
        const days = daysSinceDate(this.minAddTime);
        return this.count === 0 || days === 0 ? "" : " (" + numFormat(this.count / days, 2) + " в день)";
    }

}