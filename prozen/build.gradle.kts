plugins {
    kotlin("multiplatform")
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled = true
                }
                distribution {
                    outputDirectory = File("$rootDir/build/distributions/")
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            resources.srcDir("src/jsMain/resources")
            dependencies {
                implementation(project(":page"))
            }
        }
    }
}

val prozenDev = rootProject.ext["prozenDev"] as Boolean
val extensionVersion = getVersionFromChangelog(rootProject.projectDir)

tasks.named("jsProcessResources", Copy::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks {
    val extensionRoot = rootProject.layout.buildDirectory.dir("extension")
    val extensionZipFolder = rootProject.layout.buildDirectory.dir("extension-zip")
    // Промежуточная сборка: JS-бандлы всех модулей + статика. Браузерные варианты — копии
    // этой базы + сгенерированный manifest.json.
    val baseDir = layout.buildDirectory.dir("extension-base")

    register("tagVersion") {
        group = "versioning"
        description = "Tags the last commit with the version number"
        // Проверка release-бандла должна пройти до создания и отправки тега.
        dependsOn(rootProject.tasks.named("checkReleaseBundle"))
        doLast {
            val existingTags = runCommand("git", "tag")
            if (existingTags.lines().any { it.trim() == "v$extensionVersion" }) {
                println("Tag v$extensionVersion already exists, skipping.")
            } else {
                println("Tagging commit with version $extensionVersion")
                runCommandChecked("git", "tag", "v$extensionVersion")
                runCommandChecked("git", "push", "origin", "v$extensionVersion")
            }
        }
    }

    val modules = rootProject.getJsSourceModules(prozenDev)

    val assembleBase = register<Sync>("assembleExtensionBase") {
        dependsOn(modules.map { ":$it:jsBrowserDistribution" })
        into(baseDir)
        modules.forEach {
            from(project(":$it").layout.buildDirectory.dir("distributions")) {
                into("js")
                exclude {
                    it.name.matches(Regex("""\d+\.js(\.map)?"""))
                        || it.name.endsWith(".LICENSE.txt")
                }
                // В release source maps не создаются, но в каталогах модулей могли остаться
                // от предыдущих сборок.
                if (!prozenDev) {
                    exclude("**/*.map")
                }
            }
        }
        from("src/jsMain/resources")
    }

    val variantZips = mutableListOf<TaskProvider<Zip>>()

    Browser.values().forEach { target ->
        val variantName = "prozen-${target.id}" + if (prozenDev) "-dev" else ""
        val variantDir = extensionRoot.map { it.dir(variantName) }

        val copyVariant = register<Copy>("copy${target.name}Variant") {
            dependsOn(assembleBase)
            // Каталог варианта — выход сборки: чистим целиком, иначе между режимами
            // переживают чужие артефакты (например, старые source maps).
            doFirst { delete(variantDir.get().asFile) }
            from(baseDir)
            into(variantDir)
        }

        val generateManifest = register<GenerateManifestTask>("generate${target.name}Manifest") {
            // После копии: иначе чистка варианта может удалить свежий манифест.
            dependsOn(copyVariant)
            browser.set(target.id)
            dev.set(prozenDev)
            version.set(extensionVersion)
            outputFile.set(variantDir.map { it.file("manifest.json") })
        }

        val packageVariant = register<Zip>("package${target.name}Extension") {
            dependsOn(generateManifest)
            destinationDirectory.set(extensionZipFolder)
            archiveFileName.set("$variantName.zip")
            from(variantDir) {
                // Chrome Web Store принимает архив с корневой папкой, AMO — с файлами в корне.
                into(if (target == Browser.FIREFOX) "" else "prozen")
            }
        }
        variantZips.add(packageVariant)
    }

    // Агрегат текущего режима. В процессе фиксирован один режим, поэтому регистрируем
    // только нужную задачу — корневой build вызывает её по имени.
    register("package${if (prozenDev) "Dev" else "Release"}Extensions") {
        group = "build"
        description = "Упаковывает ${if (prozenDev) "dev" else "release"}-варианты для всех браузеров"
        dependsOn(variantZips)
    }

    if (prozenDev) {
        // Dev-сборка не должна жить рядом со старым релизом: иначе можно случайно
        // вгрузить в маркетплейс протухший артефакт.
        register<Delete>("cleanReleaseArtifacts") {
            group = "build"
            description = "Удаляет release-каталоги, zip-архивы и архив исходников"
            Browser.values().forEach { target ->
                delete(rootProject.layout.buildDirectory.dir("extension/prozen-${target.id}"))
                delete(rootProject.layout.buildDirectory.file("extension-zip/prozen-${target.id}.zip"))
            }
            delete(rootProject.layout.buildDirectory.file("extension-zip/prozen-sources.zip"))
        }
    } else {
        // Исходники для Firefox AMO. Без dev-кода (devtools/src/dev) и эталона API
        // (reference/zen-api.toml): из этого архива dev-сборку не собрать, release — можно.
        register<Zip>("packageSourcesZip") {
            group = "build"
            description = "Архив исходников для загрузки на addons.mozilla.org"
            destinationDirectory.set(extensionZipFolder)
            archiveFileName.set("prozen-sources.zip")
            from(rootProject.layout.projectDirectory) {
                exclude("**/.git/**", "**/.gradle/**", ".idea/**", ".kotlin/**", ".work/**", ".env*")
                exclude("build/**", "*/build/**")
                exclude("reference/**")
                exclude("devtools/src/dev/**")
                exclude("*.zip")
            }
        }
    }
}
