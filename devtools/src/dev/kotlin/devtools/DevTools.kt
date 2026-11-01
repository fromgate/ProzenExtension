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

/** Идентификатор кнопки: по нему находим элемент, созданный через DSL. */
private const val DIFF_BUTTON_ID = "prozen-dev-diffs-button"

/** Кнопка «Скопировать расхождения» на странице настроек. */
private var diffButton: HTMLButtonElement? = null

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
 * Dev-интерфейс в настройках: кнопка «Скопировать расхождения (N)».
 *
 * Контейнер `DIV` — контейнер кнопки «Сохранить»: кнопка встаёт рядом.
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
            updateButtonFrom(records.size)
        } catch (error: Throwable) {
            logValidatorFailure(error)
            showNotification("Не удалось скопировать расхождения")
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
private suspend fun updateButton(knownEnabled: Boolean? = null) {
    val button = diffButtonElement() ?: return

    val enabled = knownEnabled ?: try {
        Option.DEBUG.value().await()
    } catch (error: Throwable) {
        logValidatorFailure(error)
        false
    }
    if (!enabled) {
        button.style.display = "none"
        return
    }

    val count = try {
        DiffStore.snapshot().size
    } catch (error: Throwable) {
        logValidatorFailure(error)
        return
    }
    button.style.display = ""
    updateButtonFrom(count)
}

private fun updateButtonFrom(count: Int) {
    val button = diffButtonElement() ?: return
    button.textContent = "Скопировать расхождения ($count)"
    button.disabled = count == 0
    button.style.opacity = if (count == 0) "0.6" else ""
}

/**
 * Элемент кнопки ищется лениво: страница настроек достраивается после вызова
 * `devSettingsSection`, поэтому сразу `getElementById` может ничего не найти.
 */
private fun diffButtonElement(): HTMLButtonElement? {
    diffButton?.let { return it }
    val element = document.getElementById(DIFF_BUTTON_ID) as? HTMLButtonElement
    if (element != null) diffButton = element
    return element
}

private fun logValidatorFailure(error: Throwable) {
    console.error("Prozen API validator: ${error.message}")
}
