object Versions {
    const val datetime = "0.6.2"
    const val serialization = "1.8.1"
    const val coroutines = "1.10.1"
    const val html = "0.12.0"
    const val ktor = "3.1.2"
}

object Libs {
    const val kotlinxDatetime = "org.jetbrains.kotlinx:kotlinx-datetime:${Versions.datetime}"
    const val kotlinxSerializationJson = "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}"
    const val kotlinxCoroutinesJs = "org.jetbrains.kotlinx:kotlinx-coroutines-core-js:${Versions.coroutines}"
    const val kotlinxHtmlJs = "org.jetbrains.kotlinx:kotlinx-html-js:${Versions.html}"

    const val ktorClientCore = "io.ktor:ktor-client-core:${Versions.ktor}"
    const val ktorClientJs =  "io.ktor:ktor-client-js:${Versions.ktor}"
    const val ktorClientSerialization = "io.ktor:ktor-client-serialization:${Versions.ktor}"
    const val ktorClientSerializationKotlinxJson =  "io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor}"
    const val ktorClientContentNegotiation = "io.ktor:ktor-client-content-negotiation:${Versions.ktor}"
}