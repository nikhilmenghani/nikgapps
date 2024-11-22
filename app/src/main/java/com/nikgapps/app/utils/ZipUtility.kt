package com.nikgapps.app.utils

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.nikgapps.app.presentation.ui.viewmodel.ProgressLogViewModel

object ZipUtility {
    suspend fun extractZip(
        viewModel: ProgressLogViewModel,
        zipFilePath: String,
        outputDirPath: String,
        includeExtn: List<String> = emptyList(),
        extractNestedZips: Boolean = false,
        deleteZipAfterExtract: Boolean = false,
        progressCallback: (String) -> Unit
    ): Boolean = coroutineScope {
        try {
            val zipFile = File(zipFilePath)
            val outputDir = File(outputDirPath)

            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val deferreds = mutableListOf<Deferred<Boolean>>()
            if (zipFile.extension == "zip") {
                viewModel.addLog("Extracting: ${zipFile.name}")
            }
            ZipInputStream(zipFile.inputStream()).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    val fileName = entry.name

                    if (includeExtn.isEmpty() || includeExtn.any {
                            fileName.contains(it, ignoreCase = true)
                        }) {
                        val extractedFile = File(outputDir, fileName)
                        if (entry.isDirectory) {
                            extractedFile.mkdirs()
                        } else {
                            progressCallback("Extracting $fileName...")
                            extractedFile.parentFile?.mkdirs()
                            FileOutputStream(extractedFile).use { outputStream ->
                                zipStream.copyTo(outputStream)
                            }

                            // If the extracted file is a zip and the extractNestedZips flag is true, extract it asynchronously
                            if (extractNestedZips && extractedFile.extension == "zip") {
                                val nestedOutputDir = File(
                                    extractedFile.parentFile,
                                    extractedFile.nameWithoutExtension
                                )
                                if (!nestedOutputDir.exists()) {
                                    nestedOutputDir.mkdirs()
                                }
                                val deferred = async(Dispatchers.IO) {
                                    extractZip(
                                        viewModel,
                                        extractedFile.absolutePath,
                                        nestedOutputDir.absolutePath,
                                        extractNestedZips = false,
                                        deleteZipAfterExtract = true,
                                        progressCallback = progressCallback
                                    )
                                }
                                deferreds.add(deferred)
                            }
                        }
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }

            // Await all nested extraction tasks to complete
            val nestedResults = deferreds.awaitAll()
            if (nestedResults.any { !it }) {
                return@coroutineScope false
            }
            if (deleteZipAfterExtract) {
                zipFile.delete()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
