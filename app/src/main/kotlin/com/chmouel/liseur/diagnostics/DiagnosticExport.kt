package com.chmouel.liseur.diagnostics

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.chmouel.liseur.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Copies a prepared diagnostic export into a user-selected SAF folder. */
object DiagnosticExport {
    data class Result(val folderName: String, val filesWritten: Int)

    fun writeToTree(context: Context, treeUri: Uri, parts: List<DiagnosticHistory.Part>): Result {
        require(parts.isNotEmpty()) { "There is no diagnostic history to export." }
        val selectedRoot = requireNotNull(DocumentFile.fromTreeUri(context, treeUri)) {
            "The selected folder is unavailable."
        }
        require(selectedRoot.isDirectory && selectedRoot.canWrite()) { "The selected folder is not writable." }
        val root = selectedRoot.findFile("Papa Liseur Diagnostics")?.takeIf { it.isDirectory }
            ?: requireNotNull(selectedRoot.createDirectory("Papa Liseur Diagnostics")) {
                "Could not create the Papa Liseur Diagnostics folder."
            }

        val folderName = buildFolderName()
        val apkFolder = root.findFile(folderName)?.takeIf { it.isDirectory }
            ?: requireNotNull(root.createDirectory(folderName)) {
                "Could not create the APK diagnostic folder."
            }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val prefix = "export-$stamp"
        var written = 0
        parts.forEach { part ->
            writeNewFile(context, apkFolder,
                "$prefix-part-%03d-of-%03d.txt".format(Locale.US, part.index, part.count),
                part.text)
            written++
        }

        val combined = buildString {
            appendLine("PAPA-LISEUR COMPLETE DIAGNOSTIC EXPORT")
            appendLine("APK_FOLDER: $folderName")
            appendLine("EXPORTED_AT: $stamp")
            appendLine("PARTS: ${parts.size}")
            appendLine()
            parts.forEach { part ->
                appendLine("===== PART ${part.index}/${part.count} =====")
                appendLine(part.text)
                appendLine()
            }
        }
        writeNewFile(context, apkFolder, "$prefix-diagnostics.txt", combined)
        written++

        val info = buildString {
            appendLine("PAPA-LISEUR DIAGNOSTIC EXPORT INFO")
            appendLine("APK_LABEL: ${BuildConfig.PAPA_BUILD_LABEL}")
            appendLine("VERSION: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("COMMIT: ${BuildConfig.PAPA_BUILD_COMMIT}")
            appendLine("RUN_ID: ${BuildConfig.PAPA_BUILD_RUN_ID.ifBlank { "local" }}")
            appendLine("BUILD_EPOCH_SECONDS: ${BuildConfig.PAPA_BUILD_EPOCH_SECONDS}")
            appendLine("EXPORTED_AT: $stamp")
            appendLine("PARTS: ${parts.size}")
            appendLine("CHARACTER_LIMIT_PER_PART: ${DiagnosticHistory.DEFAULT_CHAT_PART_CHARACTERS}")
            appendLine("Files are local diagnostic data. Review before sharing.")
        }
        writeNewFile(context, apkFolder, "$prefix-export-info.txt", info)
        written++

        val readme = """PAPA LISEUR DIAGNOSTICS

Each APK build has its own folder, named from its Build Label.
Inside that folder:
- export-*-diagnostics.txt: combined export for convenient review.
- export-*-part-*.txt: individually copyable parts, each with APK and part metadata.
- export-*-export-info.txt: build identity and export details.

Keep older APK folders. They may contain evidence needed to compare regressions.
No diagnostic data is uploaded automatically.
"""
        writeOrReplace(context, root, "README.txt", readme)
        return Result(folderName, written)
    }

    private fun buildFolderName(): String {
        val label = BuildConfig.PAPA_BUILD_LABEL.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-', '.', '_').take(48).ifBlank { "build" }
        val commit = BuildConfig.PAPA_BUILD_COMMIT.take(7).ifBlank { "unknown" }
        return "APK-$label-v${BuildConfig.VERSION_CODE}-$commit"
    }

    private fun writeNewFile(context: Context, folder: DocumentFile, name: String, contents: String) {
        val uniqueName = if (folder.findFile(name) == null) name else {
            val dot = name.lastIndexOf('.')
            val stem = if (dot >= 0) name.substring(0, dot) else name
            val extension = if (dot >= 0) name.substring(dot) else ""
            var suffix = 2
            var candidate: String
            do {
                candidate = "$stem-$suffix$extension"
                suffix++
            } while (folder.findFile(candidate) != null)
            candidate
        }
        val file = requireNotNull(folder.createFile("text/plain", uniqueName)) {
            "Could not create $uniqueName."
        }
        context.contentResolver.openOutputStream(file.uri, "w").use { stream ->
            requireNotNull(stream) { "Could not open $uniqueName for writing." }
            stream.write(contents.toByteArray(Charsets.UTF_8))
            stream.flush()
        }
    }

    private fun writeOrReplace(context: Context, folder: DocumentFile, name: String, contents: String) {
        val existing = folder.findFile(name)
        if (existing != null && existing.isFile) {
            context.contentResolver.openOutputStream(existing.uri, "wt").use { stream ->
                requireNotNull(stream) { "Could not update $name." }
                stream.write(contents.toByteArray(Charsets.UTF_8))
                stream.flush()
            }
        } else {
            writeNewFile(context, folder, name, contents)
        }
    }
}
