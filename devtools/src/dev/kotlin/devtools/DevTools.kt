package devtools

import common.ApiObservers
import common.Option
import common.copyTextToClipboard
import common.showNotification
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.html.DIV
import kotlinx.html.button
import kotlinx.html.js.onClickFunction
import kotlinx.html.style
import org.w3c.dom.HTMLButtonElement
import kotlin.js.Date

/** Идентификатор кнопки: по нему находим элемент, созданный через DSL. */
private const val DIFF_BUTTON_ID = "prozen-dev-diffs-button"

private const val SCHEMA_BUTTON_ID = "prozen-dev-schema-button"

/** Кнопка «Скопировать расхождения» на странице настроек. */
private var diffButton: HTMLButtonElement? = null

/** Кнопка «Скопировать схему API»: полный эталон с дописанными `unknown`. */
private var schemaButton: HTMLButtonElement? = null

/**
 * Ставит валидатор наблюдателем `Requester`.
 *
 * Вызывается первой строкой `main()` в каждом бандле, где создаётся Requester,
 * то есть до первого Requester: Requester запоминает наблюдателя при создании.
 *
 * Валидатор всегда активен в dev-сборке; DEBUG управляет только видимостью кнопки.
 */
@OptIn(DelicateCoroutinesApi::class)
fun installDevValidator() {
    ApiObservers.current = ApiValidator
    GlobalScope.launch {
        try {
            // Проверка эталона: при смене хеша переоцениваются накопленные расхождения.
            DiffStore.ensureReferenceCurrent()
        } catch (error: Throwable) {
            logValidatorFailure(error)
        }
    }
}

/**
 * Dev-интерфейс в настройках: кнопки «Скопировать расхождения (N)» и
 * «Скопировать схему API (Новое: X)».
 *
 * Контейнер `DIV` — контейнер кнопки «Сохранить»: кнопки встают рядом.
 * При каждом открытии настроек эталон переоценивается по хешу.
 */
@OptIn(DelicateCoroutinesApi::class)
fun devSettingsSection(container: DIV) {
    container.button(classes = "prozen-save-button") {
        attributes["id"] = DIFF_BUTTON_ID
        style = "display: none; margin-left: 10px;"
        onClickFunction = { copyDiffs() }
        +"Скопировать расхождения"
    }

    container.button(classes = "prozen-save-button") {
        attributes["id"] = SCHEMA_BUTTON_ID
        style = "display: none; margin-left: 10px;"
        onClickFunction = { copySchema() }
        +"Скопировать схему API"
    }

    // Счётчик мог измениться в другой вкладке: обновляем при возврате на страницу.
    window.addEventListener("focus", { refreshDevSettingsIndicator() })

    GlobalScope.launch {
        try {
            DiffStore.ensureReferenceCurrent()
        } catch (error: Throwable) {
            logValidatorFailure(error)
        }
        updateButton()
    }
}

/**
 * Обновляет кнопку после смены режима отладки (D+Numpad0):
 * видимость зависит от `Option.DEBUG`, счётчик — от накопленных записей.
 *
 * `debugEnabled` передаётся сразу после переключения: storage.local пишется
 * асинхронно, и чтение настройки успело бы вернуть старое значение.
 */
@OptIn(DelicateCoroutinesApi::class)
fun refreshDevSettingsIndicator(debugEnabled: Boolean? = null) {
    GlobalScope.launch { updateButton(debugEnabled) }
}

@OptIn(DelicateCoroutinesApi::class)
private fun copyDiffs() {
    GlobalScope.launch {
        try {
            DiffStore.ensureReferenceCurrent()
            val records = DiffStore.snapshot()
            copyTextToClipboard(buildCopyText(records, ReferenceHolder.document))
            showNotification(
                if (records.isEmpty()) "Расхождений с эталоном нет" else "Скопировано расхождений: ${records.size}"
            )
            updateButtonFrom(records.size, schemaCount(records))
        } catch (error: Throwable) {
            logValidatorFailure(error)
            showNotification("Не удалось скопировать расхождения")
        }
    }
}

/**
 * Копирует весь эталон, дописав новые поля в `[endpoints.<имя>.unknown]`.
 *
 * Кнопка активна и без новых полей: тогда в буфер попадает текущий файл
 * байт-в-байт — это удобно для проверки round-trip после сохранения.
 */
@OptIn(DelicateCoroutinesApi::class)
private fun copySchema() {
    GlobalScope.launch {
        try {
            DiffStore.ensureReferenceCurrent()
            val records = DiffStore.snapshot()
            val reference = ReferenceHolder.document
            val additions = newSchemaRecords(records, reference).size
            copyTextToClipboard(buildSchemaCopy(records, reference, ReferenceHolder.sourceText, Date()))
            showNotification(
                if (additions == 0) {
                    "Схема API скопирована: новых полей нет."
                } else {
                    "Схема API скопирована. Новых полей: $additions. " +
                        "После сохранения пересоберите расширение."
                }
            )
            updateButtonFrom(records.size, additions)
        } catch (error: Throwable) {
            logValidatorFailure(error)
            showNotification("Не удалось скопировать схему API")
        }
    }
}

/** Сколько `new` попадёт в `unknown` при копировании схемы. */
private fun schemaCount(records: List<DiffRecord>): Int =
    newSchemaRecords(records, ReferenceHolder.document).size

@OptIn(DelicateCoroutinesApi::class)
private suspend fun updateButton(knownEnabled: Boolean? = null) {
    val diff = diffButtonElement()
    val schema = schemaButtonElement()
    if (diff == null && schema == null) return

    val enabled = knownEnabled ?: try {
        Option.DEBUG.value().await()
    } catch (error: Throwable) {
        logValidatorFailure(error)
        false
    }
    if (!enabled) {
        diff?.style?.display = "none"
        schema?.style?.display = "none"
        return
    }

    val records = try {
        DiffStore.snapshot()
    } catch (error: Throwable) {
        logValidatorFailure(error)
        return
    }
    diff?.style?.display = ""
    schema?.style?.display = ""
    updateButtonFrom(records.size, schemaCount(records))
}

private fun updateButtonFrom(count: Int, schemaAdded: Int) {
    diffButtonElement()?.let { button ->
        button.textContent = "Скопировать расхождения ($count)"
        button.disabled = count == 0
        button.style.opacity = if (count == 0) "0.6" else ""
    }
    schemaButtonElement()?.let { button ->
        button.textContent = "Скопировать схему API (Новое: $schemaAdded)"
        // Копирование схемы полезно и без новых полей: в буфер идёт текущий эталон.
        button.disabled = false
        button.style.opacity = ""
    }
}

/**
 * Элементы кнопок ищутся лениво: страница настроек достраивается после вызова
 * `devSettingsSection`, поэтому сразу `getElementById` может ничего не найти.
 */
private fun diffButtonElement(): HTMLButtonElement? {
    diffButton?.let { return it }
    val element = document.getElementById(DIFF_BUTTON_ID) as? HTMLButtonElement
    if (element != null) diffButton = element
    return element
}

private fun schemaButtonElement(): HTMLButtonElement? {
    schemaButton?.let { return it }
    val element = document.getElementById(SCHEMA_BUTTON_ID) as? HTMLButtonElement
    if (element != null) schemaButton = element
    return element
}

private fun logValidatorFailure(error: Throwable) {
    console.error("Prozen API validator: ${error.message}")
}
