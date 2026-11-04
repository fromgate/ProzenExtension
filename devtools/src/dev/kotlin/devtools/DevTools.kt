package devtools

import common.ApiObservers
import common.Option
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
import org.w3c.dom.HTMLTextAreaElement
import kotlin.js.Date

/** Идентификатор кнопки: по нему находим элемент, созданный через DSL. */
private const val SCHEMA_BUTTON_ID = "prozen-dev-schema-button"

/** Кнопка «Скопировать схему API»: полный эталон с дописанными `unknown`. */
private var schemaButton: HTMLButtonElement? = null

/**
 * Текст, готовый к копированию, число новых полей и число новых значений monitor.
 *
 * Собирается заранее, при открытии настроек и возврате фокуса: буфер обмена
 * вызывается синхронно из обработчика клика, пока браузер считает действие
 * пользовательским. Если собирать текст после `await` на storage, пользовательский
 * жест теряется, и Clipboard API может отказать.
 */
private class PreparedSchema(val text: String, val additions: Int, val newMonitorValues: Int)

private var preparedSchema: PreparedSchema? = null

/** Причина, по которой схему не удалось подготовить: показывается при клике. */
private var prepareError: String? = null

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
 * Dev-интерфейс в настройках: кнопка «Скопировать схему API (Новое: X)».
 *
 * Контейнер `DIV` — контейнер кнопки «Сохранить»: кнопка встаёт рядом.
 * При каждом открытии настроек эталон переоценивается по хешу, а текст
 * для копирования собирается заранее.
 */
@OptIn(DelicateCoroutinesApi::class)
fun devSettingsSection(container: DIV) {
    container.button(classes = "prozen-save-button") {
        attributes["id"] = SCHEMA_BUTTON_ID
        style = "display: none; margin-left: 10px;"
        onClickFunction = { copySchema() }
        +"Скопировать схему API"
    }

    // Счётчик мог измениться в другой вкладке: обновляем при возврате на страницу.
    window.addEventListener("focus", { refreshDevSettingsIndicator() })

    prepareSchemaCopy()
}

/**
 * Обновляет кнопку после смены режима отладки (D+Numpad0): видимость зависит
 * от `Option.DEBUG`, счётчик и текст — от накопленных записей.
 *
 * `debugEnabled` передаётся сразу после переключения: storage.local пишется
 * асинхронно, и чтение настройки успело бы вернуть старое значение.
 */
@OptIn(DelicateCoroutinesApi::class)
fun refreshDevSettingsIndicator(debugEnabled: Boolean? = null) {
    prepareSchemaCopy(debugEnabled)
}

/**
 * Пересобирает текст для буфера обмена и обновляет кнопку.
 *
 * Подготовка не трогает буфер обмена, поэтому может выполняться в фоне:
 * при клике достаточно взять готовый текст.
 */
@OptIn(DelicateCoroutinesApi::class)
private fun prepareSchemaCopy(knownEnabled: Boolean? = null) {
    GlobalScope.launch {
        // Кнопка могла ещё не попасть в документ: тогда готовим текст при следующем
        // обновлении интерфейса (фокус окна, переключение отладки).
        val button = schemaButtonElement() ?: return@launch

        val enabled = knownEnabled ?: try {
            Option.DEBUG.value().await()
        } catch (error: Throwable) {
            logValidatorFailure(error)
            false
        }
        if (!enabled) {
            button.style.display = "none"
            preparedSchema = null
            prepareError = null
            return@launch
        }

        button.style.display = ""
        if (preparedSchema == null) {
            button.textContent = "Скопировать схему API (готовится…)"
        }

        try {
            val prepared = loadPreparedSchema()
            preparedSchema = prepared
            prepareError = null
            button.textContent = "Скопировать схему API (Новое: ${prepared.additions})"
        } catch (error: Throwable) {
            // Копировать нечем, но причину не скрываем: она попадёт в уведомление
            // при клике и в консоль при открытии настроек.
            preparedSchema = null
            prepareError = error.message ?: error.toString()
            logValidatorFailure(error)
            button.textContent = "Скопировать схему API (ошибка подготовки)"
        }

        button.disabled = false
        button.style.opacity = ""
    }
}

/** Читает записи, переоценивает их по эталону и собирает полный текст схемы. */
private suspend fun loadPreparedSchema(): PreparedSchema {
    try {
        DiffStore.ensureReferenceCurrent()
    } catch (error: Throwable) {
        // Переоценка по хешу не должна мешать копированию: берём то, что уже накоплено.
        logValidatorFailure(error)
    }

    val records = DiffStore.snapshot()
    val monitorRecords = MonitorStore.snapshot()
    val reference = ReferenceHolder.document
    val copied = buildSchemaCopy(records, monitorRecords, reference, ReferenceHolder.sourceText, Date())
    return PreparedSchema(
        text = copied.text,
        additions = copied.newRecords + copied.newMonitorValues,
        newMonitorValues = copied.newMonitorValues,
    )
}

/**
 * Копирует весь эталон, дописав новые поля в `[endpoints.<имя>.unknown]`.
 *
 * Кнопка активна и без новых полей: тогда в буфер попадает текущий файл
 * байт-в-байт — это удобно для проверки round-trip после сохранения.
 */
private fun copySchema() {
    val prepared = preparedSchema
    if (prepared == null) {
        val reason = prepareError
        showNotification(
            if (reason == null) {
                "Схема API готовится: нажмите кнопку ещё раз через секунду."
            } else {
                "Схема API не подготовлена: $reason"
            }
        )
        prepareSchemaCopy()
        return
    }
    copyPrepared(prepared)
}

/**
 * Пишет готовый текст в буфер обмена, не разрывая пользовательский жест.
 *
 * `writeText` вызывается синхронно из обработчика клика; промис ожидается
 * через `then`. Если Clipboard API недоступен или отказал, используется
 * скрытая textarea и `document.execCommand('copy')`.
 */
private fun copyPrepared(prepared: PreparedSchema) {
    val promise = try {
        window.navigator.clipboard.writeText(prepared.text)
    } catch (error: Throwable) {
        // В части браузеров Clipboard API на странице расширения недоступен вовсе.
        logValidatorFailure(error)
        finishCopy(prepared, legacyCopy(prepared.text), error.message)
        return
    }

    promise.then(
        { finishCopy(prepared, true, null) },
        { error ->
            val reason = errorMessage(error)
            console.error("Prozen API validator: Clipboard API отказал: $reason")
            finishCopy(prepared, legacyCopy(prepared.text), reason)
        },
    )
}

/** Резервное копирование: скрытая textarea и синхронный `execCommand`. */
private fun legacyCopy(text: String): Boolean {
    val area = document.createElement("textarea") as? HTMLTextAreaElement ?: return false
    area.value = text
    area.setAttribute("readonly", "")
    area.setAttribute("style", "position: fixed; top: 0; left: -9999px; opacity: 0;")
    document.body?.appendChild(area)
    return try {
        area.select()
        js("document.execCommand('copy')") as Boolean
    } catch (error: Throwable) {
        logValidatorFailure(error)
        false
    } finally {
        area.remove()
    }
}

private fun finishCopy(prepared: PreparedSchema, copied: Boolean, failure: String?) {
    if (!copied) {
        val reason = failure?.takeIf { it.isNotBlank() } ?: "буфер обмена недоступен"
        showNotification("Не удалось скопировать схему API: $reason")
        return
    }

    showNotification(
        when {
            prepared.additions == 0 -> "Схема API скопирована: новых полей нет."
            prepared.newMonitorValues > 0 ->
                "Схема API скопирована. Новых полей и значений: ${prepared.additions} " +
                    "(значений: ${prepared.newMonitorValues}). После сохранения пересоберите расширение."
            else ->
                "Схема API скопирована. Новых полей: ${prepared.additions}. " +
                    "После сохранения пересоберите расширение."
        }
    )

    // Записи могли измениться в другой вкладке: счётчик и текст обновляем в фоне.
    prepareSchemaCopy()
}

private fun errorMessage(error: dynamic): String {
    // Явный тип String? обязателен: у dynamic-выражения Kotlin/JS ищет
    // расширение isNotBlank как метод объекта и падает с TypeError.
    val message: String? = error?.message
    return if (message != null && message.isNotEmpty()) message else "причина неизвестна"
}

/** Элемент кнопки ищется лениво: страница настроек достраивается после вызова `devSettingsSection`. */
private fun schemaButtonElement(): HTMLButtonElement? {
    schemaButton?.let { return it }
    val element = document.getElementById(SCHEMA_BUTTON_ID) as? HTMLButtonElement
    if (element != null) schemaButton = element
    return element
}

private fun logValidatorFailure(error: Throwable) {
    console.error("Prozen API validator: ${error.message}", error)
}
