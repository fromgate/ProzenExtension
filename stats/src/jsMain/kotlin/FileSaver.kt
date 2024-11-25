import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.MouseEventInit
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.xhr.BLOB
import org.w3c.xhr.XMLHttpRequest
import org.w3c.xhr.XMLHttpRequestResponseType

class FileSaver {

    // Преподготовка BOM (Byte Order Mark) для определённых типов файлов
    private fun bom(blob: Blob, opts: dynamic = js("{}")): Blob {
        val autoBom = opts.autoBom as? Boolean ?: false
        val typeRegex = Regex("""^\s*(?:text/\S*|application/xml|\S*/\S*\+xml)\s*;.*charset\s*=\s*utf-8""", RegexOption.IGNORE_CASE)

        return if (autoBom && typeRegex.containsMatchIn(blob.type)) {
            Blob(arrayOf("\uFEFF", blob), BlobPropertyBag(blob.type))
        } else {
            blob
        }
    }

    // Загрузка файла с указанного URL
    fun download(url: String, name: String, opts: dynamic = js("{}")) {
        val xhr = XMLHttpRequest()
        xhr.open("GET", url)
        xhr.responseType = XMLHttpRequestResponseType.BLOB
        xhr.onload = {
            saveAs(xhr.response as Blob, name, opts)
        }
        xhr.onerror = {
            console.error("Could not download file")
        }
        xhr.send()
    }

    // Проверка доступности CORS для указанного URL
    private fun corsEnabled(url: String): Boolean {
        val xhr = XMLHttpRequest()
        xhr.open("HEAD", url, async = false)
        return try {
            xhr.send()
            xhr.status in 200..299
        } catch (e: Throwable) {
            false
        }
    }

    // Симуляция клика на элементе
    private fun click(node: HTMLAnchorElement) {
        try {
            // Используем MouseEvent с конструктором
            val event = MouseEvent("click", MouseEventInit(bubbles = true, cancelable = true))
            node.dispatchEvent(event)
        } catch (e: Throwable) {
            console.error("Failed to dispatch click event", e)
        }
    }

    // Основной метод для сохранения файла
    fun saveAs(blob: Blob, name: String, opts: dynamic = js("{}")) {
        val autoBomBlob = bom(blob, opts)
        val a = document.createElement("a") as HTMLAnchorElement
        val url = URL.createObjectURL(autoBomBlob)

        a.href = url
        a.download = name
        a.rel = "noopener"
        document.body?.appendChild(a)

        click(a)
        document.body?.removeChild(a)
        window.setTimeout({ URL.revokeObjectURL(url) }, 40000)
    }
}