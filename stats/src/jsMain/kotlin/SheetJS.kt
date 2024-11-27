
import common.Card
import common.toYYYYMMDD
import common.views
import kotlinx.datetime.Clock
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.set
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlin.js.Date
import kotlin.js.json

external object XLSX {
    val utils: dynamic
    fun write(workbook: dynamic, options: dynamic): String
}

object ExcelExporter {
    /**
     * Создаёт новый Excel файл на основе предоставленных данных.
     * @param cards Список карточек публикаций
     */
    fun export(cards: List<Card>) {
        val workbook = XLSX.utils.book_new()

        workbook["Props"] = json(
            "Title" to "PROZEN stats",
            "Subject" to "Stats",
            "Author" to "PROZEN extension",
            "CreatedDate" to Date()
        )

        val sheetNames = mapOf(
            "article" to "Статьи",
            "brief" to "Посты",
            "gif" to "Видео",
            "short_video" to "Ролики"
        )

        for ((type, sheetName) in sheetNames) {
            val filteredStats = cards.filter { it.type == type }
            if (filteredStats.isNotEmpty()) {
                val data = getExcelData(filteredStats, type)
                val sheet = XLSX.utils.aoa_to_sheet(data)
                XLSX.utils.book_append_sheet(workbook, sheet, sheetName)
            }
        }

        val excelBinary = XLSX.write(
            workbook, json(
                "bookType" to "xlsx",
                "type" to "binary"
            )
        )

        val blob = Blob(arrayOf(s2ab(excelBinary)), BlobPropertyBag("application/octet-stream"))
        val fileName = "prozen-stats-${generateTimestamp()}.xlsx"
        FileSaver().saveAs(blob, fileName)
    }

    /**
     * Генерация данных для таблицы.
     */
    private fun getExcelData(cards: List<Card>, type: String): Array<Array<Any>> {
        val header = when (type) {
            "article" -> arrayOf<Any>("","Статья", "Показы", "Клики", "Дочитывания")
            "brief" -> arrayOf<Any>("","Пост", "Показы", "Просмотры")
            "gif" -> arrayOf<Any>("","Видео", "Показы", "Просмотры")
            "short_video" -> arrayOf<Any>("","Ролик", "Показы", "Просмотры")
            else -> arrayOf<Any>("", type, "Показы", "Просмотры")
        }
        val rows = cards.groupBy { it.type }.flatMap { (_, cardList) ->
            cardList.map { it.toArray() }
        }.toTypedArray()
        return arrayOf(header) + rows
    }

    /**
     * Конвертация строки в бинарный формат.
     */
    private fun s2ab(s: String): Uint8Array {
        val buffer = ArrayBuffer(s.length)
        val view = Uint8Array(buffer)
        for (i in s.indices) {
            view[i] = s[i].code.toByte()
        }
        return view
    }

    /**
     * Генерация текущей метки времени для имени файла.
     */
    private fun generateTimestamp(): String = Clock.System.now().toYYYYMMDD(showTime = true)

    /**
     * Преобразование карточки в массив данных, которые будут отображаться
     * в строке таблицы
     */
    fun Card.toArray(): Array<Any> {
        val list: MutableList<Any> = mutableListOf(
            type,
            title,
            feedShows,
            clicks
        ) as MutableList<Any>
        if (type == "article") list.add(views())
        return list.toTypedArray()
    }
}
