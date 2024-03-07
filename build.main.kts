#!/usr/bin/env kotlin

import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

val version = "2.7.20"

println("Building ProzenExtension v$version")

createZip("prozen-chrome.zip")
println("prozen-chrome.zip")

createZip("prozen-firefox.zip", true)
println("prozen-firefox.zip")

createZip("prozen-edge.zip", false /*, File("prozen-edge/manifest.json")*/)
println("prozen-edge.zip")

fun createZip(zipName: String, skipSubFolder: Boolean = false, manifest: String = "prozen/manifest.json") {
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
                val entry = ZipEntry("${zipFileName.replace(File.separator, "/")}${(if (file.isDirectory) "/" else "")}")
                zos.putNextEntry(entry)
                if (file.isFile) {
                    if (file.name == "manifest.json") {
                        updatedManifest(manifest).copyTo(zos)
                        //manifest.inputStream().use { it.copyTo(zos) }
                    } else {
                        file.inputStream().use { it.copyTo(zos) }
                    }
                }
            }
        }
    }
}

fun updatedManifest(filename: String): ByteArrayInputStream {
    val manifestFile = File (filename)
    val manifestLines = manifestFile.inputStream().reader().readLines()
    val newManifest = mutableListOf<String>()
    manifestLines.forEach {
        if (it.contains("\"version\": \"2.7.19\",")) {
            newManifest.add(it.replace("2.7.19", version))
        } else {
            newManifest.add(it)
        }
    }
    return newManifest.joinToString(separator = "\n").byteInputStream()
}