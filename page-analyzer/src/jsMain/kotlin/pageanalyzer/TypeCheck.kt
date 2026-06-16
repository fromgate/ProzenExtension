package pageanalyzer

import common.M

enum class TypeCheck (val icon: String, val title: String, val hint: String, val skip: Boolean = false) {
    IS_BANNED("❌", "Блокировка", "Публикация заблокирована", true),
    NO_INDEX ("🤖", "Индексация", M.publicationNotIndexed),
    THEMATICS("📚", "Темы", "Публикации назначена тема", true),
    NO_THEMATICS ("🎈", "Нет темы", "Публикации не назначена тема"),
    PREMIUM ("👑", "Премиум", "Публикация опубликована в премиальном разделе"),
    NOT_PREMIUM ("🔓", "Общий доступ", "Публикация опубликована для всех", true),
    DMCA ("🎹", "DMCA","Нарушение авторских прав (DMCA)?"),
    COVID ("😷","COVID-19", "Публикация отнесена к теме коронавируса"),
    COMMENTS_OFF ("😶","Комментарии отключены","Комментарии отключены",),
    COMMENTS_SUBSCRIBERS ("😬","Комментарии для подписчиков","Комментарии открыты для подписчиков") ,
    COMMENTS_ALL ("😁","Комментарии для всех","Комментарии открыты для всех"),
    NO_ADV ("🪙","Реклама отключена","Рекламные блоки не обнаружены"),

    SEO_RATING ("⭐️","Рейтинг статьи","Рейтинг для поисковиков", true),
    HTTP_STATUS_CODE ("⚠️","HTTP_STATUS_CODE","HTTP_STATUS_CODE", true);

    companion object {
        fun getByName (name: String?) : TypeCheck? {
            return TypeCheck.entries.firstOrNull { it.name == name}
        }
    }
}