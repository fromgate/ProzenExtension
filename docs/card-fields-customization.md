# Кастомизация карточек публикаций: концепция и план подготовительных работ

Статус: концепция, реализация не начата.
Назначение документа — зафиксировать целевую архитектуру карточек публикаций и поэтапный план подготовительного рефакторинга, после которого визуально и по DOM **ничего не меняется**, но появляется публичная точка подключения для:

- настроек скрытия/показа отдельных полей карточек;
- дополнительных полей из модуля дев-режима (отображаются только в дев-сборке);
- полей из иных внешних модулей (например, премиального режима).

Подготовительный этап затрагивает **только модуль `content`**. Модули `common`, `devtools`, `settings` на этом этапе не изменяются.

---

## 1. Текущее состояние

Карточки не строятся с нуля: контент-скрипт находит в DOM страницы Дзена готовые карточки и перезаписывает в них блоки статистики. Три точки рендера — все в `content/src/jsMain/kotlin/publications/Publications.kt`:

| Метод | Раскладка | Что перезаписывается |
|---|---|---|
| `updateStudioCardElement()` | Карточки главной страницы студии (`PageType.MAIN`) | `div.prozen-card-stats` — 4 колонки `prozen-card-column` со строками `prozen-card-row` |
| `modifyTableRow()` | Страница «Публикации», табличный режим | Ячейки строки `cells[3..6]`, заголовки колонок, ячейка времени `cells[1]` |
| `modifyGridCell()` | Страница «Публикации», сеточный режим | `div.prozen-grid-card-stats` — 4 строки `prozen-grid-card-row` по 2 ячейки `prozen-grid-card-cell`, замена текста даты |

Точки входа: `Studio.run()` → `processDashboardCards()` (главная); сообщения сервис-воркера → `backgroundListener()` в `content/src/jsMain/kotlin/Listeners.kt` → `processPublicationsCards()` (страница «Публикации»). Выбор сетка/таблица — `isPublicationGrid()`.

Отображаемые поля захардкожены в HTML-коде трёх методов:

- показы (`Card.showsStr()`);
- просмотры/клики с CTR/VTR (`Card.viewsStrAndTitle()`);
- дочитывания (`Card.fullReadsStrTitle()`);
- просмотры от подписчиков (`Card.subscribersViewStr()`);
- лайки, комментарии, репосты, подписки (`Card.likes()`, `comments()`, `reposts()`, `subscriptions()`);
- ER (`Card.er()`);
- время создания/редактирования (`Card.timeCrateAndModStr()`, иконка правки);
- иконка источника импорта (`Card.originIcon()`);
- кнопки-действия: «скопировать ссылку» (`Card.shorUrl()` / `Card.url()`), «репост» (`Card.repostUrl()`).

Данные — `common.Card` (common/src/jsMain/kotlin/common/Card.kt), форматирование — extension-функции рядом с ней. CSS-классы — в `prozen/src/jsMain/resources/css/prozen.css` (`.prozen-card-*`, `.prozen-grid-card-*`, иконки `.prozen_studio_card_icon_*`). Защита от повторного рендера — атрибуты `data-prozen-publication-id` и `data-prozen-updated`.

Настройки — enum `Option` в `common/src/jsMain/kotlin/common/Options.kt`: все опции boolean, ключи в `chrome.storage.local`, чтение через `Option.value()` / `isSet()`, страница настроек рендерит все опции автоматически, кроме `group = "hidden"`. Пример пакетного чтения опций перед рендером — `Studio.modifyStudioStyles()` (content/src/jsMain/kotlin/studio/Studio.kt).

Дев-функции — модуль `devtools` с сорс-сетами `src/dev` и `src/release`: одинаковые сигнатуры функций, в release — no-op заглушки, которые минификатор выбрасывает. Точка вызова из content — `installDevValidator()` первой строкой `main()` (content/src/jsMain/kotlin/Content.kt). `content` уже зависит от `:devtools` (content/build.gradle.kts) — направление зависимости «content → внешний модуль» является установленным паттерном проекта.

## 2. Цели и нецели

Цели подготовительного этапа:

1. Единое декларативное описание полей карточки вместо трёх копий захардкоженного HTML.
2. Публичный API для внешних модулей: регистрация дополнительных полей и управление их видимостью — без изменения кода рендера.
3. Публичная точка для настроек: резолвер видимости полей, который позже начнёт читать опции.
4. Поведение и внешний вид идентичны текущим: те же классы CSS, те же inline-стили, тот же порядок элементов, те же атрибуты, та же защита от повторного рендера.

Нецели подготовительного этапа:

- новые опции в настройках (будут позже, поверх резолвера видимости);
- какие-либо изменения в `common`, `devtools`, `settings`;
- новые отображаемые данные.

## 3. Концепция: три слоя

Архитектура — три слоя внутри модуля `content`. Новый код — в пакете `content/src/jsMain/kotlin/publications/cards/`.

### 3.1. Слой 1 — каталог полей (`CardField`)

Дескриптор одного отображаемого элемента карточки:

```kotlin
// publications/cards/CardField.kt
enum class CardFieldId {
    SHOWS, VIEWS, FULL_READS, SUBSCRIBER_VIEWS,
    LIKES, COMMENTS, REPOSTS, SUBSCRIPTIONS, ER,
    TIME, ORIGIN, ACTION_COPY_LINK, ACTION_REPOST
}

enum class CardFieldKind { STAT, ACTION }

class CardField(
    val id: String,                 // для базовых — CardFieldId.name, для внешних — "dev.api-diff" и т.п.
    val kind: CardFieldKind,
    val title: String,              // tooltip
    val render: (Card, HTMLElement) -> Unit,  // рисует иконку/текст/кнопки в контейнер
    val isVisible: suspend () -> Boolean = { true },  // для внешних полей; базовые решает CardVisibility
)
```

Рендер всегда использует готовые форматтеры из `common/Card.kt` — логику форматирования не дублировать. Рендер синхронный; если полю нужны асинхронные данные (как будущим дев-полям), внутри рендера используется `GlobalScope.launch` — действующий паттерн кнопки «скопировать ссылку».

Каталог базовых полей (`publications/cards/BaseCardFields.kt`) — перенос текущих строк из `Publications.kt` в дескрипторы: иконка, текст, tooltip, обработчики кликов — без изменений.

### 3.2. Слой 2 — композиции раскладок

Плоский список полей не описывает три разные раскладки, поэтому каждая раскладка описывает **свою композицию**: упорядоченный список контейнеров, каждый контейнер — упорядоченный список слотов полей. Так сохраняются текущие «составные» строки (например, «лайки | комментарии | репосты» в одной строке дашборда) и разные способы группировки в разных раскладках.

```kotlin
// publications/cards/CardComposition.kt
class CardSlot(val field: CardFieldId, val style: String? = null)  // style — локальные отступы слота
class CardContainer(val cssClass: String, val style: String? = null, val slots: List<CardSlot>)
class CardComposition(val containers: List<CardContainer>)
```

Целевое соответствие текущим раскладкам (детальная структура фиксируется при реализации, инвариант — DOM/CSS/порядок не меняются):

- **Дашборд** (`DashboardComposition`): 4 контейнера-колонки — `[SHOWS, VIEWS]`, `[FULL_READS, SUBSCRIBER_VIEWS]`, `[LIKES, COMMENTS, REPOSTS, SUBSCRIPTIONS, ER]` (третья колонка с `flex-grow: 1.5`, соседние слоты с отступами `margin-left: 8px`), `[TIME, ORIGIN, ACTION_COPY_LINK, ACTION_REPOST]` (последняя строка с `justify-content: right`, у TIME дополнительная иконка редактирования — часть рендера поля TIME).
- **Таблица** (`TableComposition`): привязка к фиксированным ячейкам — `cells[3] = [SHOWS, VIEWS]`, `cells[4] = [FULL_READS, SUBSCRIBER_VIEWS]`, `cells[5] = [LIKES, COMMENTS, REPOSTS, SUBSCRIPTIONS]`, `cells[6] = [ER]`; каждое поле в своей строке с `justify-content: right`. Переименование заголовков колонок, замена времени в `cells[1]` и сжатие сниппета — нативные правки страницы, остаются в адаптере таблицы (не поля).
- **Сетка** (`GridComposition`): 4 контейнера-строки по 2 ячейки — `[SHOWS | LIKES, REPOSTS]`, `[VIEWS | SUBSCRIPTIONS, COMMENTS]`, `[FULL_READS | ER]`, `[SUBSCRIBER_VIEWS | ORIGIN, ACTION_COPY_LINK, ACTION_REPOST]`. Замена текста даты — нативная правка, остаётся в адаптере.

**Зона расширения.** Каждая композиция имеет выделенный контейнер в конце блока статистики. Рендер раскладки после своих слотов добавляет туда все видимые зарегистрированные поля, не входящие в базовый набор раскладки. Внешнему модулю не нужно знать о раскладках: он регистрирует поле — все три раскладки подхватят его в свою зону расширения. В подготовительном этапе зоны расширения пусты.

### 3.3. Слой 3 — резолвер видимости (`CardVisibility`)

```kotlin
// publications/cards/CardVisibility.kt
object CardVisibility {
    suspend fun isVisible(field: CardField): Boolean
    suspend fun visibleIds(): Set<String>  // пакетно: все решения за одно чтение опций
}
```

На подготовительном этапе возвращает `true` для всех полей. Будущее поведение: для базовых полей — маппинг `CardFieldId -> Option` (например `SHOWS -> Option.CARD_FIELD_SHOWS`), читаемый пакетно перед рендером батча карточек (по образцу `Studio.modifyStudioStyles()`); для внешних — предикат `isVisible` из дескриптора. Дефолты новых опций = `true`, поэтому включение механизма не меняет вид по умолчанию.

### 3.4. Реестр и точка расширения для внешних модулей

```kotlin
// publications/cards/CardFieldRegistry.kt
class CardFieldRegistry {
    fun register(field: CardField)
    fun get(id: String): CardField?
    fun all(): List<CardField>
}

object CardFields {
    val registry = CardFieldRegistry()
    fun init(externalProviders: List<(CardFieldRegistry) -> Unit> = emptyList())
}
```

Реестр синглтон — допустимо: один контент-скрипт на страницу, инициализация один раз рядом с `installDevValidator()`. Базовые поля регистрируются из `BaseCardFields`; `externalProviders` — функции регистрации из внешних модулей.

**Контракт внешнего модуля** — повторение установленного паттерна `installDevValidator()`:

```kotlin
// в модуле-провайдере (devtools — в будущем):
//   src/dev/kotlin/...    — реальная регистрация полей
//   src/release/kotlin/... — fun registerCardFields(registry: CardFieldRegistry) = Unit
fun registerCardFields(registry: CardFieldRegistry)

// в content (один вызов рядом с installDevValidator()):
CardFields.init(listOf(::registerCardFields))
```

- **Дев-режим**: регистрация живёт в `devtools/src/dev`, release-заглушка — в `src/release`. Дев-поля физически отсутствуют в релизной сборке; дополнительно видимость может зависеть от `Option.DEBUG` через предикат дескриптора.
- **Иные модули (премиум)**: новый Gradle-модуль, зависимость `content` в content/build.gradle.kts, та же функция регистрации, предикат видимости — по состоянию модуля.

Подключение любого будущего модуля — один вызов в `CardFields.init()` плюс сама регистрация; код рендера не меняется.

## 4. План подготовительных работ

### Этап 0. Документ

Этот файл. Согласовать концепцию.

### Этап 1. Каркас пакета

Новый пакет `content/src/jsMain/kotlin/publications/cards/`: `CardFieldId`, `CardFieldKind`, `CardField`, `CardFieldRegistry`, `CardFields`, `CardVisibility` (константный `true`), `CardComposition` (`CardSlot`, `CardContainer`). Без изменений в `Publications.kt` — компилируется, ничего не делает.

Проверка: `./gradlew :content:compileKotlinJs`.

### Этап 2. Каталог базовых полей

`BaseCardFields.kt`: дескрипторы для всех полей из раздела 1 с переносом рендеров из `Publications.kt` (иконки, tooltip, обработчики кликов — копируются как есть, включая иконку редактирования у TIME). Регистрация в `CardFields.init()`.

### Этап 3. Композиции раскладок

`DashboardComposition`, `TableComposition`, `GridComposition` — по структуре раздела 3.2. Каждая умеет отдать свои контейнеры с видимыми слотами и зону расширения. Общий проход рендера: для контейнера создать DOM-узел с его классами/стилями, для каждого видимого слота вызвать `field.render(card, container)`.

### Этап 4. Переключение `Publications.kt`

Три метода (`updateStudioCardElement`, `modifyTableRow`, `modifyGridCell`) переписываются на композиции:

- в начале — пакетное вычисление видимых полей через `CardVisibility.visibleIds()` (сейчас — все);
- построение блока статистики — через композицию раскладки;
- нативные правки (заголовки таблицы, время `cells[1]`, сниппет, дата в сетке) остаются в методах как есть;
- атрибуты `data-prozen-publication-id` / `data-prozen-updated` и логика `waitList` не меняются.

После этапа в `Publications.kt` не остаётся ни одного захардкоженного поля — только выбор раскладки и нативные правки страницы.

### Этап 5. Проверка идентичности

1. `./gradlew :content:compileKotlinJs`, затем dev-сборка `./gradlew build`.
2. Ручная загрузка `build/extension/prozen-chrome-dev`, сверка трёх видов карточек: главная студии; «Публикации» — сетка; «Публикации» — таблица.
3. Сверка DOM до/после рефакторинга: классы, порядок дочерних элементов, inline-стили, атрибуты блоков статистики; скриншотное сравнение в обеих темах.
4. Регрессия: клик по кнопкам «ссылка»/«репост», tooltip'ы, старые форматы (`post`, `narrative`, `story`) пропускаются как раньше.

Критерий готовности: неотличимость результата и наличие задокументированного здесь API.

## 5. Как подключать будущие возможности

### 5.1. Настройки скрытия полей

1. В `common/Options.kt` добавить boolean-опции с id вида `card-field-shows`, `card-field-views`, ... — по одной на `CardFieldId`, `defaultValue = true`, `group = "Карточки"`. Страница настроек подхватит их автоматически (рендерит все группы, кроме `hidden`).
2. В `CardVisibility` добавить маппинг `CardFieldId -> Option` и читать значения пакетно; итог передать в композиции как `visibleIds`.
3. Дефолт `true` ⇒ сразу после подключения вид карточек не меняется; отключённые поля перестают рендериться.

Открытый вопрос — применение настроек без перезагрузки страницы (слушатель `chrome.storage.onChanged` → снять `data-prozen-publication-id` с карточек и перерендерить). Решается при подключении настроек.

### 5.2. Поля дев-режима

1. В `devtools/src/dev` — `registerCardFields(registry)`: регистрация полей со строковыми id (`"dev.*"`), рендер поверх данных валидатора (diff-хранилище, `storage.local`), предикат видимости (например, по `Option.DEBUG`).
2. В `devtools/src/release` — одноимённая заглушка `= Unit`.
3. В content — добавить `::registerCardFields` в вызов `CardFields.init()`.

Релизная сборка не меняется ни по размеру, ни по поведению (заглушки выбрасываются минификатором).

### 5.3. Новый внешний модуль (например, премиум)

1. Новый Gradle-модуль, зависимость `content` в content/build.gradle.kts.
2. В модуле — `registerCardFields(registry)` с полями и предикатом видимости (состояние лицензии/опции).
3. В content — один вызов в `CardFields.init()`.

Если модуль должен существовать только в части сборок — паттерн сорс-сетов `src/dev`/`src/release` из `devtools` применим и к нему (см. `prozen.dev` в корневом build.gradle.kts).

## 6. Ограничения и принятые решения

- Подготовительный этап — только модуль `content`; `common`, `devtools`, `settings` не изменяются.
- Форматирование чисел/дат/процентов остаётся в `common/Card.kt`; content раскладывает готовые строки.
- Рендер поля — синхронная функция; асинхронные данные — через `GlobalScope.launch` внутри рендера (действующий стиль кодовой базы).
- Реестр — синглтон: один контент-скрипт на страницу, инициализация один раз.
- Табличная раскладка привязана к фиксированным индексам ячеек (`cells[3..6]`) — контракт сохраняется; поля дев/внешних модулей в таблице попадают в зону расширения, а не сдвигают колонки.
- Внешние поля размещаются в зонах расширения раскладок; точное место в табличном режиме (новая колонка vs конец существующих ячеек) — открытый вопрос, решается при первом внешнем модуле.
- Защита от повторного рендера (`data-prozen-publication-id`, `data-prozen-updated`, `waitList` с повторным проходом) сохраняется без изменений.

## 7. Открытые вопросы

1. Применение настроек «на лету» без перезагрузки страницы (см. 5.1).
> Ответ: применение на лету не нужно.
2. Размещение внешних полей в табличном режиме (см. 6).
> Ответ: надо будет решить в процессе реализации
3. Группировка опций полей в настройках (плоский список vs группы «Статистика» / «Действия»).
> Ответ: Этот вопрос будет решать при модификации страницы настроек.
4. Локализация названий полей в настройках (сейчас tooltip'ы карточек захардкожены по-русски; для настроек, вероятно, нужны ключи в `_locales`).
> Ответ: Всё по-русски.
5. Нужна ли сортировка/упорядочивание полей пользователем — в текущей концепции не предусмотрена (только вкл/выкл).
> Ответ: Не нужна
