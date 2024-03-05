#!/usr/bin/env kotlin
@file:Repository ("https://repo.maven.apache.org/maven2")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")


import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

// TODO Manifest builder

println("Building ProzenExtension")

createZip("prozen-chrome.zip")
println("prozen-chrome.zip")
createZip("prozen-firefox.zip", true, manifest = File ("prozen-firefox/manifest.json"))
println("prozen-firefox.zip")
createZip("prozen-edge.zip", false, File("prozen-edge/manifest.json"))
println("prozen-edge.zip")

fun createZip(zipName: String, skipSubFolder: Boolean = false, manifest: File? = null) {
    val sourceDir = File("prozen")
    val files = sourceDir.walkTopDown()
    val outputZipFile = File(zipName)
    if (outputZipFile.exists()) {
        outputZipFile.delete()
    }
    outputZipFile.createNewFile()
    ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
        files.forEach { file ->
            val zipFileName = "${if (skipSubFolder) "" else "prozen${File.separator}"}${
                file.absolutePath.removePrefix(sourceDir.absolutePath).removePrefix(File.separator)
            }"
            if (zipFileName != "" && !file.isDirectory) {
                val entry = ZipEntry("$zipFileName${(if (file.isDirectory) "/" else "")}".replace(File.separator, "/"))
                zos.putNextEntry(entry)
                if (file.isFile) {
                    if (file.name == "manifest.json" && manifest != null) {
                        manifest.inputStream().use { it.copyTo(zos) }
                    } else {
                        file.inputStream().use { it.copyTo(zos) }
                    }
                }
            }
        }
    }
}

fun setExtensionVersion (version: String, fileName: String) {
    val file = File (fileName)



}