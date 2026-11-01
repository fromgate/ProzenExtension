package common



import kotlin.js.Console

object DebugLogger {
    var isDebugEnabled: Boolean = false

    init {
        updateDebugState()
    }

    /** Перечитывает режим отладки из настроек (асинхронно). */
    fun updateDebugState() {
        Option.DEBUG.value().then { isDebugEnabled = it }
    }

    /**
     * Применяет только что записанное значение сразу: после D+Numpad0
     * не ждём асинхронного `storage.local.get`, иначе `dLog` отстаёт на шаг.
     */
    fun updateDebugState(enabled: Boolean) {
        isDebugEnabled = enabled
    }
}

inline fun Console.dLog(message: Any?) {
    if (DebugLogger.isDebugEnabled)  {
        this.log(message)
    }
}

inline fun Console.dInfo(message: Any?) {
    if (DebugLogger.isDebugEnabled)  {
        this.log(message)
    }
}

inline fun Console.dWarn(message: Any?) {
    if (DebugLogger.isDebugEnabled)  {
        this.warn(message)
    }
}

inline fun Console.dError(message: Any?) {
    if (DebugLogger.isDebugEnabled)  {
        this.error(message)
    }
}