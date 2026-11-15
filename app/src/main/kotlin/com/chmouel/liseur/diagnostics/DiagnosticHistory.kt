package com.chmouel.liseur.diagnostics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.chmouel.liseur.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Local diagnostic history. Events never leave the device automatically.
 * Callers must pass technical metadata only, never book text or credentials.
 */
class DiagnosticHistory(context: Context) {
    data class Part(
        val index: Int,
        val count: Int,
        val characterCount: Int,
        val text: String,
    )

    private val directory = File(context.applicationContext.filesDir, "diagnostics")
    private val sessionId = UUID.randomUUID().toString()
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "diagnostic-history").apply { isDaemon = true }
    }
    private val sequence = java.util.concurrent.atomic.AtomicLong()
    private val lock = Any()

    init {
        // Diagnostics must never prevent the reader itself from starting.
        runCatching { directory.mkdirs() }
    }

    /** Queue one bounded event without blocking the reader thread. */
    fun record(
        event: String,
        attributes: Map<String, String> = emptyMap(),
        error: Throwable? = null,
    ) {
        if (event.isBlank()) return
        val safeAttributes = attributes.entries
            .filterNot { (key, _) -> SENSITIVE_ATTRIBUTE_KEY.containsMatchIn(key) }
            .take(MAX_ATTRIBUTES)
            .associate { (key, value) ->
                key.take(MAX_ATTRIBUTE_KEY_CHARS) to value.take(MAX_ATTRIBUTE_VALUE_CHARS)
            }

        // Both queue submission and disk writes are non-fatal to reading.
        runCatching {
            writer.execute {
                runCatching {
                    val item = JSONObject()
                    item.put("timestampUtcMs", System.currentTimeMillis())
                    item.put("elapsedRealtimeMs", SystemClock.elapsedRealtime())
                    item.put("sessionId", sessionId)
                    item.put("sequence", sequence.incrementAndGet())
                    item.put("event", event.take(MAX_EVENT_NAME_CHARS))
                    item.put("attributes", JSONObject(safeAttributes))
                    if (error != null) {
                        item.put("errorType", error.javaClass.name.take(200))
                        // Exception messages can contain local file paths, book
                        // names or provider URIs. Keep stack frames, not messages.
                        item.put(
                            "stack",
                            error.stackTrace.asSequence()
                                .take(12)
                                .joinToString("\n") { it.toString() }
                                .take(1800),
                        )
                    }

                    synchronized(lock) {
                        val file = currentFile()
                        file.appendText(item.toString() + "\n", Charsets.UTF_8)
                        rotateIfNeeded(file)
                    }
                }
            }
        }
    }

    suspend fun exportParts(maxCharacters: Int = DEFAULT_CHAT_PART_CHARACTERS): List<Part> =
        withContext(Dispatchers.IO) {
            require(maxCharacters in MIN_CHAT_PART_CHARACTERS..MAX_CHAT_PART_CHARACTERS)
            writer.submit {}.get(5, TimeUnit.SECONDS)
            if (!directory.isDirectory) return@withContext emptyList()
            val lines = synchronized(lock) {
                eventFiles().flatMap { it.readLines(Charsets.UTF_8) }.filter { it.isNotBlank() }
            }
            splitForChat(lines, maxCharacters)
        }

    private fun currentFile(): File =
        eventFiles().lastOrNull() ?: File(directory, "events-0001.jsonl")

    private fun eventFiles(): List<File> =
        directory.listFiles()
            .orEmpty()
            .filter { it.name.startsWith("events-") && it.extension == "jsonl" }
            .sortedBy { it.name }

    private fun rotateIfNeeded(file: File) {
        if (file.length() <= MAX_FILE_BYTES) return
        val next = (file.nameWithoutExtension.substringAfterLast('-').toIntOrNull() ?: 0) + 1
        File(directory, "events-%04d.jsonl".format(next)).createNewFile()
        eventFiles().dropLast(MAX_RETAINED_FILES).forEach(File::delete)
    }

    private fun splitForChat(lines: List<String>, limit: Int): List<Part> {
        val payloadLimit = limit - HEADER_RESERVE
        val groups = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        var length = 0
        for (line in lines) {
            val safeLine = if (line.length <= payloadLimit) line else {
                JSONObject()
                    .put("event", "diagnostic.export_line_too_large")
                    .put("originalCharacters", line.length)
                    .toString()
            }
            val extra = safeLine.length + if (current.isEmpty()) 0 else 1
            if (current.isNotEmpty() && length + extra > payloadLimit) {
                groups += current
                current = mutableListOf()
                length = 0
            }
            current += safeLine
            length += safeLine.length + if (current.size == 1) 0 else 1
        }
        if (current.isNotEmpty() || groups.isEmpty()) groups += current

        val total = groups.size
        return groups.mapIndexed { index, group ->
            val header = buildString {
                appendLine("PAPA-LISEUR DIAGNOSTIC EXPORT")
                appendLine("APK_LABEL: ${BuildConfig.PAPA_BUILD_LABEL}")
                appendLine("APPLICATION_ID: ${BuildConfig.APPLICATION_ID}")
                appendLine("VERSION: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("COMMIT: ${BuildConfig.PAPA_BUILD_COMMIT}")
                appendLine("RUN_ID: ${BuildConfig.PAPA_BUILD_RUN_ID.ifBlank { "local" }}")
                appendLine("SOURCE_COMMIT_EPOCH_SECONDS: ${BuildConfig.PAPA_BUILD_EPOCH_SECONDS}")
                appendLine("DEVICE: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("ANDROID_API: ${Build.VERSION.SDK_INT}")
                appendLine("EXPORT_SESSION: $sessionId (events may include retained earlier sessions)")
                appendLine("PART: ${index + 1}/$total")
                appendLine("EVENT_LINES: ${group.size}")
                appendLine("--- EVENTS ---")
            }
            val text = header + group.joinToString("\n") +
                "\n--- END PART ${index + 1}/$total ---"
            check(text.length <= limit) { "Diagnostic part exceeds the requested character limit" }
            Part(index + 1, total, text.length, text)
        }
    }

    companion object {
        const val DEFAULT_CHAT_PART_CHARACTERS = 12_000
        const val MIN_CHAT_PART_CHARACTERS = 8_000
        const val MAX_CHAT_PART_CHARACTERS = 20_000
        private const val HEADER_RESERVE = 1_200
        private const val MAX_FILE_BYTES = 256L * 1024L
        private const val MAX_RETAINED_FILES = 4
        private val SENSITIVE_ATTRIBUTE_KEY =
            Regex("(?i)(title|author|text|content|href|url|uri|path|token|password|secret|email|user)")
        private const val MAX_EVENT_NAME_CHARS = 100
        private const val MAX_ATTRIBUTES = 12
        private const val MAX_ATTRIBUTE_KEY_CHARS = 60
        private const val MAX_ATTRIBUTE_VALUE_CHARS = 256
    }
}
