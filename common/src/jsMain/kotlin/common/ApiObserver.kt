package common

import kotlinx.serialization.json.JsonElement

/**
 * Один выполненный запрос к API. Всё, что нужно наблюдателю; про методы Requester
 * он ничего не знает.
 */
class ApiCall(
    /** Метод HTTP. Пока всегда GET. */
    val method: String,
    /** Полный URL вместе с query, в том виде, в каком он ушёл в сеть. */
    val url: String,
    /** HTTP-статус ответа. */
    val status: Int,
    /** Разобранный JSON или `null`, если тело пустое или это не JSON. */
    val body: JsonElement?,
    /** Необязательная подсказка «кто вызвал», только для отчётов. */
    val caller: String? = null,
)

/**
 * Наблюдатель за запросами. Вызывается после каждого ответа, в том числе при
 * статусе не 200.
 *
 * Реализация не должна бросать исключения и надолго задерживать запрос:
 * Requester оборачивает вызов в try/catch, но тяжёлую работу лучше выносить
 * в собственный scope.
 */
interface ApiObserver {
    fun onResponse(call: ApiCall)
}

/** Заглушка: в release других наблюдателей нет. */
object NoopObserver : ApiObserver {
    override fun onResponse(call: ApiCall) = Unit
}

/**
 * Точка регистрации. Значение по умолчанию вычисляется при создании Requester,
 * поэтому dev-регистрация должна выполниться раньше первого Requester.
 *
 * В каждом исполняемом бандле своя копия `common`, поэтому регистрироваться
 * нужно в каждом бандле, который создаёт Requester.
 */
object ApiObservers {
    var current: ApiObserver = NoopObserver
}
