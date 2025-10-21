object Versions {
    const val datetime = "0.6.2"
    const val serialization = "1.9.0"
    const val coroutines = "1.10.2"
    const val ktor = "3.3.1"
    const val html = "0.12.0"
}

object Libs {
    // https://github.com/Kotlin/kotlinx-datetime
    const val kotlinxDatetime = "org.jetbrains.kotlinx:kotlinx-datetime:${Versions.datetime}"

    // https://github.com/Kotlin/kotlinx.serialization
    const val kotlinxSerializationJson = "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}"

    // https://github.com/Kotlin/kotlinx.coroutines
    const val kotlinxCoroutinesJs = "org.jetbrains.kotlinx:kotlinx-coroutines-core-js:${Versions.coroutines}"

    // https://github.com/Kotlin/kotlinx.html
    const val kotlinxHtmlJs = "org.jetbrains.kotlinx:kotlinx-html-js:${Versions.html}"

    // https://github.com/ktorio/ktor
    const val ktorClientCore = "io.ktor:ktor-client-core:${Versions.ktor}"
    const val ktorClientJs = "io.ktor:ktor-client-js:${Versions.ktor}"
    const val ktorClientSerialization = "io.ktor:ktor-client-serialization:${Versions.ktor}"
    const val ktorClientSerializationKotlinxJson = "io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor}"
    const val ktorClientContentNegotiation = "io.ktor:ktor-client-content-negotiation:${Versions.ktor}"
}