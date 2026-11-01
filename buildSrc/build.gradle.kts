plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // TOML для эталона API: нужен только при сборке, в расширение не попадает.
    implementation("org.tomlj:tomlj:1.1.1")
    // JSON нужен и задачам сборки (dev-манифест, проверка release), и тестам.
    // Версия совместима с компилятором buildSrc (ограничение kotlin-dsl).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
