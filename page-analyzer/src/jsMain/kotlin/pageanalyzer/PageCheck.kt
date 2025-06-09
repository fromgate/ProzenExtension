package pageanalyzer

import common.arr
import common.bool
import common.string
import kotlinx.serialization.json.jsonObject

abstract class PageCheck {

    protected val results = mutableMapOf<TypeCheck, Any>()

    fun analyze(context: PageContextBuilder) {
        results.clear()
        perform(context)
    }

    protected abstract fun perform(context: PageContextBuilder)

    fun getResults(): Map<TypeCheck, Any> = results
}

object CheckNoIndex : PageCheck() {
    override fun perform(context: PageContextBuilder) {
        val noindex = context.metaTags.any { meta ->
            val propertyAttr = meta.getAttribute("property")
            (meta.name == "robots" || propertyAttr == "robots") && meta.content.contains("noindex")
        }
        if (noindex) results[TypeCheck.NO_INDEX] = true
    }
}

object CheckThematics : PageCheck() {
    override fun perform(context: PageContextBuilder) {
        if (context.thematics.isNotEmpty()) {
            results[TypeCheck.THEMATICS] = context.thematics
        } else {
            results[TypeCheck.NO_THEMATICS] = true
        }
    }
}

object CheckComments : PageCheck() {
    override fun perform(context: PageContextBuilder) {
        val item = context.embeddedJson?.arr("ssrData/socialMetaResponse/items")?.firstOrNull()?.jsonObject
        val comments = when (item?.string("metaInfo.visibleComments")?.lowercase()) {
            "invisible" -> TypeCheck.COMMENTS_OFF
            "subscribe_visible" -> TypeCheck.COMMENTS_SUBSCRIBERS
            "visible" -> TypeCheck.COMMENTS_ALL
            else -> null
        }
        comments?.let { results[it] = true }
    }
}

object CheckAdv : PageCheck() {
    override fun perform(context: PageContextBuilder) {
        if (context.embeddedJson != null) {
            when (context.type) {
                "article" -> {
                    if (
                        context.embeddedJson!!.string("ssrData.publishersResponse.data.data.adData.adBlocks.desktop-header.rsyaAdData.blockId") == null
                        && context.embeddedJson!!.string("ssrData.publishersResponse.data.data.adData.adBlocks.desktop-footer.rsyaAdData.blockId") == null
                        && context.embeddedJson!!.string("ssrData.publishersResponse.data.data.adData.adBlocks.desktop-right.rsyaAdData.blockId") == null
                    ) {
                        results[TypeCheck.NO_ADV] = true
                    }
                }

                "gif" -> {
                    ///ssrData/videoMetaResponse/video/ad/adVideoConfigData/enabled ???

                    if (context.embeddedJson!!.string("ssrData.videoSettings.exportData.video.adBlocks.TOP_SIDEBAR.rsyaAdData") == null
                        && context.embeddedJson!!.string("ssrData.videoSettings.exportData.video.adBlocks.BOTTOM_PLAYER.rsyaAdData") == null
                        && context.embeddedJson!!.string("ssrData.videoSettings.exportData.video.adBlocks.LIVE_ADS_BANNER.rsyaAdData") == null
                        && context.embeddedJson!!.string("ssrData.videoSettings.exportData.clientDefinition.video_ads_under_desktop_player") == null
                        && context.embeddedJson!!.string("ssrData.videoSettings.exportData.clientDefinition.video_related_ads_banner") == null

                        && context.embeddedJson!!.string("ssrData.videoMetaResponse.adBlocks.TOP_SIDEBAR.rsyaAdData.blockId") == null
                        && context.embeddedJson!!.string("ssrData.videoMetaResponse.adBlocks.BOTTOM_PLAYER.rsyaAdData.blockId") == null
                    ) {
                        results[TypeCheck.NO_ADV] = true
                    }
                }
            }
        }
    }
}

object CheckCovid : PageCheck() {
    override fun perform(context: PageContextBuilder) {
        val covid: Boolean? = if (context.isText()) {
            context.embeddedJson?.bool("ssrData/publishersResponse/data/data/publication/covid19Mentioned")
        } else {
            context.embeddedJson?.bool("ssrData.videoSettings.exportData.video.covid_19")
        }

        if (covid == true) results[TypeCheck.COVID] = true
    }
}

object CheckDmca : PageCheck() {
    override fun perform(context: PageContextBuilder) {
        if (context.isVideo()) {
            val dmca: Boolean? = context.embeddedJson?.bool("ssrData.videoMetaResponse.isDmcaMusicCopyright")
            if (dmca == true) results[TypeCheck.DMCA] = true
        }
    }
}

object CheckPremium : PageCheck() {
    override fun perform(context: PageContextBuilder) {
        if (context.isText()) {
            val visibilityType =
                context.embeddedJson?.string("ssrData/publishersResponse/data/data/publication/visibilityType")
            console.log("visibilityType=$visibilityType")
            if (visibilityType == "premium_subscribers_only") {
                results[TypeCheck.PREMIUM] = true
            } else {
                results[TypeCheck.NOT_PREMIUM] = true
            }
        } else {
            val isPremium = context.embeddedJson?.bool("ssrData/videoMetaResponse/video/isPremium")
            if (isPremium == true) {
                results[TypeCheck.PREMIUM] = true
            } else {
                results[TypeCheck.NOT_PREMIUM] = true
            }
        }
    }
}