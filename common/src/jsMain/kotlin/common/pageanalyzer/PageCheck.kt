package common.pageanalyzer

import common.arr
import common.bool
import common.dInfo
import common.string
import kotlinx.serialization.json.jsonObject

abstract class PageCheck {

    protected val results = mutableMapOf<TypeCheck, Any>()

    fun analyze(context: PageContext) {
        results.clear()
        perform(context)
    }
    protected abstract fun perform(context: PageContext)

    fun getResults(): Map<TypeCheck, Any> = results
}

object CheckNoIndex : PageCheck() {
    override fun perform(context: PageContext) {
        val noindex = context.metaTags.any { meta ->
            val propertyAttr = meta.getAttribute("property")
            (meta.name == "robots" || propertyAttr == "robots") && meta.content.contains("noindex")
        }
        console.dInfo("CheckNoIndex: $noindex")
        if (noindex) results[TypeCheck.NO_INDEX] = true
    }
}

object CheckThematics : PageCheck() {
    override fun perform(context: PageContext) {
        if (context.thematics.isNotEmpty()) {
            results[TypeCheck.THEMATICS] = context.thematics
            console.dInfo("CheckThematics: ${context.thematics.map { it.title }.joinToString()}")
        } else {
            console.dInfo("CheckThematics: Thematics are empty")
        }
    }
}

object CheckComments : PageCheck() {
    override fun perform(context: PageContext) {
        val comments: TypeCheck? = if (context.isText()) {
            when (context.embeddedJson?.string("publication.visibleComments")) {
                "invisible" -> TypeCheck.COMMENTS_OFF
                "subscribe-visible" -> TypeCheck.COMMENTS_SUBSCRIBERS
                "visible" -> TypeCheck.COMMENTS_ALL
                else -> null
            }
        } else {
            val item = context.embeddedJson?.arr("ssrData/socialMetaResponse/items")?.firstOrNull()?.jsonObject
            when (item?.string("metaInfo.visibleComments")) {
                "invisible" -> TypeCheck.COMMENTS_OFF
                "subscribe-visible" -> TypeCheck.COMMENTS_SUBSCRIBERS
                "visible" -> TypeCheck.COMMENTS_ALL
                else -> null
            }
        }
        comments?.let { results[it] = true }
        console.dInfo("CheckComments: ${comments?.name ?: "null"}")
    }
}

object CheckAdv : PageCheck() {
    override fun perform(context: PageContext) {
        if (context.embeddedJson != null) {
            when (context.type) {
                "article" -> {
                    if (
                        context.embeddedJson!!.string("adData.adBlocks.desktop-header.rsyaAdData.blockId") == null
                        && context.embeddedJson!!.string("adData.adBlocks.desktop-right.rsyaAdData.blockId") == null
                        && context.embeddedJson!!.string("adData.adBlocks.desktop-footer.rsyaAdData.blockId") == null
                    ) {
                        results[TypeCheck.NO_ADV] = true
                    }
                }

                "gif" -> {
                    if (context.embeddedJson!!.string("ssrData.videoSettings.exportData.video.adBlocks.TOP_SIDEBAR.rsyaAdData") == null
                        && context.embeddedJson!!.string("ssrData.videoSettings.exportData.video.adBlocks.BOTTOM_PLAYER.rsyaAdData") == null
                        && context.embeddedJson!!.string("ssrData.videoSettings.exportData.video.adBlocks.LIVE_ADS_BANNER.rsyaAdData") == null
                        && context.embeddedJson!!.string("ssrData.videoSettings.exportData.clientDefinition.video_ads_under_desktop_player") == null
                        && context.embeddedJson!!.string("ssrData.videoSettings.exportData.clientDefinition.video_related_ads_banner") == null
                        ) {
                        results[TypeCheck.NO_ADV] = true
                    }
                }
            }
        }
    }
}

object CheckCovid : PageCheck() {
    override fun perform(context: PageContext) {
        val covid: Boolean? = if (context.isText()) {
            context.embeddedJson?.bool("publication.covid19Mentioned")
        } else {
            context.embeddedJson?.bool("ssrData.videoSettings.exportData.video.covid_19")
        }

        if (covid == true) results[TypeCheck.COVID] = true
    }
}