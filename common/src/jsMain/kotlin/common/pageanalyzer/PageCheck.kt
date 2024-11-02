package common.pageanalyzer

import common.arr
import common.bool
import common.string
import kotlinx.serialization.json.jsonObject

abstract class PageCheck {
    protected val results = mutableMapOf<TypeCheck, Any>()

    abstract fun analyze(context: PageContext)

    fun getResults(): Map<TypeCheck, Any> = results
}

class CheckNoIndex : PageCheck() {
    override fun analyze(context: PageContext) {
        val noindex = context.metaTags.any { meta ->
            val propertyAttr = meta.getAttribute("property")
            (meta.name == "robots" || propertyAttr == "robots") && meta.content.contains("noindex")
        }
        results[TypeCheck.NO_INDEX] = noindex
    }
}

class CheckThematics : PageCheck() {
    override fun analyze(context: PageContext) {
        if (context.thematics.isNotEmpty()) {
            results[TypeCheck.THEMATICS] = context.thematics
        }
    }
}

class CheckComments : PageCheck() {
    override fun analyze(context: PageContext) {
        var comments: String? = if (context.isText()) {
            when (context.embeddedJson?.string("publication.visibleComments")) {
                "invisible" -> "off"
                "subscribe-visible" -> "subscribers"
                "visible" -> "all"
                else -> null
            }
        } else {
            val item = context.embeddedJson?.arr("ssrData/socialMetaResponse/items")?.firstOrNull()?.jsonObject
            when (item?.string ("metaInfo.visibleComments")) {
                "invisible" -> "off"
                "subscribe-visible" -> "subscribers"
                "visible" -> "all"
                else -> null
            }
        }
        comments?.let { results[TypeCheck.COMMENTS] = it }
    }
}

class CheckCovid : PageCheck() {
    override fun analyze(context: PageContext) {
        val covid = if (context.isText()) {
           context.embeddedJson?.bool("publication.covid19Mentioned")
        } else {
            context.embeddedJson?.bool("ssrData.videoSettings.exportData.video.covid_19")
        } ?: false
        results[TypeCheck.COVID] = covid
    }
}