#!/usr/bin/env kotlin

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// TODO Manifest builder

println("Building ProzenExtension")

val edgeManifest = File("prozen-edge/manifest.json")

createZip("prozen-chrome.zip")
println("prozen-chrome.zip")
createZip("prozen-firefox.zip", true)
println("prozen-firefox.zip")
createZip("prozen-edge.zip", false)
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
            if (zipFileName != "") {
                val entry = ZipEntry("$zipFileName${(if (file.isDirectory) "/" else "")}")
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