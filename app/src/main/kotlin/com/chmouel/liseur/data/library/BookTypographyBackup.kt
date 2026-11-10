package com.chmouel.liseur.data.library

import androidx.room.withTransaction
import com.chmouel.liseur.data.db.BookCustomTypography
import com.chmouel.liseur.data.db.BookTypography
import com.chmouel.liseur.data.db.LiseurDatabase
import com.chmouel.liseur.data.settings.ReadingFont
import com.chmouel.liseur.data.settings.TypographyRange
import com.chmouel.liseur.domain.KnownBook
import com.chmouel.liseur.domain.matchBackedUpBookUrl
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal data class BackedUpTypography(
    val bookUrl: String,
    val title: String,
    val author: String?,
    val typography: BookTypography?,
    val custom: BookCustomTypography?,
)

internal data class MatchedTypography(
    val bookUrl: String,
    val typography: BookTypography?,
    val custom: BookCustomTypography?,
    val hasSettings: Boolean,
)

/** Archives per-book typography while allowing book URLs to differ across devices. */
internal class BookTypographyBackup(private val database: LiseurDatabase?) {
    suspend fun encode(): Pair<ByteArray, Int> {
        val db = database ?: return emptyPayload()
        val books = db.bookDao().allOnce()
        val typography = db.typographyDao().all().associateBy { it.bookUrl }
        val custom = db.customTypographyDao().all().associateBy { it.bookUrl }
        var configured = 0
        val payload = JSONObject()
            .put("format", 1)
            .put("application", "liseur")
            .put("books", JSONArray().also { rows ->
                books.forEach { book ->
                    val own = typography[book.url]
                    val fine = custom[book.url]
                    if (own != null || fine != null) configured++
                    rows.put(
                        JSONObject()
                            .put("book_url", book.url)
                            .put("title", book.title)
                            .put("author", book.author ?: JSONObject.NULL)
                            .put("typography", own?.let(::encodeTypography) ?: JSONObject.NULL)
                            .put("custom", fine?.let(::encodeCustom) ?: JSONObject.NULL),
                    )
                }
            })
        return payload.toString().toByteArray(Charsets.UTF_8) to configured
    }

    fun decode(bytes: ByteArray): List<BackedUpTypography> {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.opt("format") == 1 && root.opt("application") == "liseur")
        val rows = root.optJSONArray("books") ?: throw IllegalArgumentException("Missing books")
        val seen = mutableSetOf<String>()
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: throw IllegalArgumentException("Invalid book row")
                val url = (row.opt("book_url") as? String)?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("Missing book URL")
                require(seen.add(url)) { "Duplicate book URL" }
                val title = row.opt("title") as? String ?: throw IllegalArgumentException("Invalid title")
                val authorValue = row.opt("author")
                val author = when (authorValue) {
                    JSONObject.NULL -> null
                    is String -> authorValue
                    else -> throw IllegalArgumentException("Invalid author")
                }
                require(row.has("typography") && row.has("custom"))
                val own = when (val value = row.opt("typography")) {
                    JSONObject.NULL -> null
                    is JSONObject -> decodeTypography(url, value)
                    else -> throw IllegalArgumentException("Invalid typography")
                }
                val fine = when (val value = row.opt("custom")) {
                    JSONObject.NULL -> null
                    is JSONObject -> decodeCustom(url, value)
                    else -> throw IllegalArgumentException("Invalid custom typography")
                }
                add(BackedUpTypography(url, title, author, own, fine))
            }
        }
    }

    suspend fun match(records: List<BackedUpTypography>): List<MatchedTypography> {
        val db = database ?: return emptyList()
        if (records.isEmpty()) return emptyList()
        val books = db.bookDao().allOnce()
        val byUrl = books.associateBy { it.url }
        fun identity(title: String, author: String?): Pair<String, String?>? {
            val normalizedTitle = title.trim().lowercase(Locale.ROOT).takeIf { it.isNotEmpty() } ?: return null
            val normalizedAuthor = author?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
            return normalizedTitle to normalizedAuthor
        }
        val fallbackIdentityCounts = records.groupingBy { identity(it.title, it.author) }.eachCount()
        val known = books.map { KnownBook(it.url, it.title, it.author) }
        val claimed = mutableSetOf<String>()
        return buildList {
            records.forEach { record ->
                val targetUrl = if (record.bookUrl in byUrl) {
                    record.bookUrl
                } else if (fallbackIdentityCounts[identity(record.title, record.author)] == 1) {
                    matchBackedUpBookUrl(record.bookUrl, record.title, record.author, known)
                } else {
                    null
                }
                val target = targetUrl?.let(byUrl::get)
                if (target != null && claimed.add(target.url)) {
                    add(
                        MatchedTypography(
                            target.url,
                            record.typography?.copy(bookUrl = target.url),
                            record.custom?.copy(bookUrl = target.url),
                            record.typography != null || record.custom != null,
                        ),
                    )
                }
            }
        }
    }

    suspend fun restore(matches: List<MatchedTypography>): Int {
        val db = database ?: return 0
        db.withTransaction {
            matches.forEach { match ->
                if (match.typography == null) db.typographyDao().clear(match.bookUrl)
                else db.typographyDao().upsert(match.typography)
                if (match.custom == null) db.customTypographyDao().clear(match.bookUrl)
                else db.customTypographyDao().upsert(match.custom)
            }
        }
        return matches.count { it.hasSettings }
    }

    private fun encodeTypography(value: BookTypography) = JSONObject()
        .put("font", value.font)
        .put("font_size", value.fontSize)
        .put("line_height", value.lineHeight ?: JSONObject.NULL)
        .put("page_margins", value.pageMargins ?: JSONObject.NULL)

    private fun encodeCustom(value: BookCustomTypography) = JSONObject()
        .put("line_height", value.lineHeight ?: JSONObject.NULL)
        .put("text_indent", value.textIndent ?: JSONObject.NULL)

    private fun decodeTypography(url: String, json: JSONObject): BookTypography {
        require(json.has("font") && json.has("font_size") && json.has("line_height") && json.has("page_margins"))
        val font = json.opt("font") as? String ?: throw IllegalArgumentException("Invalid font")
        require(ReadingFont.fromId(font).id == font) { "Unknown font" }
        val fontSize = requiredDouble(json, "font_size")
        TypographyRange.FONT_SIZE.require(fontSize)
        val lineHeight = optionalDouble(json, "line_height")
        val margins = optionalDouble(json, "page_margins")
        require(lineHeight == TypographyRange.LINE_HEIGHT.sanitize(lineHeight))
        require(margins == TypographyRange.PAGE_MARGINS.sanitize(margins))
        return BookTypography(url, font, fontSize, lineHeight, margins)
    }

    private fun decodeCustom(url: String, json: JSONObject): BookCustomTypography {
        require(json.has("line_height") && json.has("text_indent"))
        val value = BookCustomTypography(
            bookUrl = url,
            lineHeight = optionalDouble(json, "line_height"),
            textIndent = optionalDouble(json, "text_indent"),
        )
        require(value.sanitized() == value) { "Custom typography is outside its range" }
        return value
    }

    private fun requiredDouble(json: JSONObject, key: String): Double {
        val value = json.opt(key) as? Number ?: throw IllegalArgumentException("Invalid $key")
        return value.toDouble().also { require(it.isFinite()) }
    }

    private fun optionalDouble(json: JSONObject, key: String): Double? {
        val raw = json.opt(key)
        if (raw == null || raw === JSONObject.NULL) return null
        val value = (raw as? Number)?.toDouble() ?: throw IllegalArgumentException("Invalid $key")
        require(value.isFinite())
        return value
    }

    companion object {
        fun emptyPayload(): Pair<ByteArray, Int> {
            val payload = JSONObject().put("format", 1).put("application", "liseur").put("books", JSONArray())
            return payload.toString().toByteArray(Charsets.UTF_8) to 0
        }
    }
}
