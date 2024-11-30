package common

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class I18nDelegate : ReadOnlyProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): String {
        return chrome.i18n.getMessage(property.name)
    }
}

object M {
    val publicationNotIndexed by I18nDelegate()
    val notificationLinkCopied by I18nDelegate()
    val publicationCopyLink by I18nDelegate()
    val publicationViews by I18nDelegate()
    val publicationFullViews by I18nDelegate()
    val publicationTimeToRead by I18nDelegate()
    val publicationTime by I18nDelegate()
    val publicationHeaderCopyLink by I18nDelegate()
    val publicationLikes by I18nDelegate()
    val publicationComments by I18nDelegate()
    val publicationOkLink by I18nDelegate()
}