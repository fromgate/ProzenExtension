package common



import kotlin.js.Console

object DebugLogger {
    var isDebugEnabled: Boolean = false

    init {
        updateDebugState()
    }

    fun updateDebugState() {
         Option.DEBUG.value().then { isDebugEnabled = it }
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