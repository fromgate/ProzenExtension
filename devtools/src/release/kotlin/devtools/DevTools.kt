package devtools

import kotlinx.html.DIV

/**
 * Release-заглушка: валидатора в release нет.
 *
 * Тело пустое, поэтому вызов из `main()` ничего не делает и выбрасывается
 * минификатором вместе с неиспользуемым кодом.
 */
fun installDevValidator() = Unit

/** Release-заглушка: dev-интерфейса в release нет. */
fun devSettingsSection(container: DIV) = Unit

/** Release-заглушка: обновлять в интерфейсе нечего. */
fun refreshDevSettingsIndicator(debugEnabled: Boolean? = null) = Unit
