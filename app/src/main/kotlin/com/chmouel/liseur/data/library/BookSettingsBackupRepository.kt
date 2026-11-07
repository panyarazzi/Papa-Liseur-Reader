package com.chmouel.liseur.data.library

import com.chmouel.liseur.data.db.Book
import com.chmouel.liseur.data.db.BookCustomTypography
import com.chmouel.liseur.data.db.BookCustomTypographyDao
import com.chmouel.liseur.data.db.BookDao
import com.chmouel.liseur.data.db.BookTypography
import com.chmouel.liseur.data.db.BookTypographyDao
import java.io.File
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup for settings whose identity and values live in Room rather than DataStore.
 *
 * Every library book is written, including books with no per-book overrides. That
 * explicit absence matters on restore: a book which was not set apart in the
 * source archive must not keep a stale per-book override on the destination.
 */
class BookSettingsBackupRepository(
    private val books: BookDao,
    private val typography: BookTypographyDao,
    private val customTypography: BookCustomTypographyDao,
) {
    data class Entry(
        val bookId: String,
        val title: String,
        val author: String?,
        val ownTypography: BookTypography?,
        val customTypography: BookCustomTypography?,
    )

    suspend fun writeContents(file: File, maxBytes: Long) {
        val knownBooks = books.allOnce()
        val ownRows = typography.all().associateBy { it.bookUrl }
        val customRows = customTypography.all().associateBy { it.bookUrl }
        val root = JSONObject()
            .put("format", 1)
            .put("application", "liseur")
            .put("books", JSONArray().also { array ->
                knownBooks.forEach { book ->
                    val own = ownRows[book.url]
                    val custom = customRows[book.url]
                    val item = JSONObject()
                        .put("book_id", book.url)
                        .put("title", book.title)
                        .put("separate", own != null)
                    book.author?.let { item.put("author", it) }
                    own?.let {
                        item.put("typography", JSONObject()
                            .put("font", it.font)
                            .put("font_size", it.fontSize)
                            .putNullable("line_height", it.lineHeight)
                            .putNullable("page_margins", it.pageMargins))
                    }
                    custom?.let {
                        item.put("custom_typography", JSONObject()
                            .putNullable("line_height", it.lineHeight)
                            .putNullable("text_indent", it.textIndent))
                    }
                    array.put(item)
                }
            })
        val bytes = root.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > maxBytes) throw IOException("Book settings backup exceeds limit")
        file.writeBytes(bytes)
    }

    fun readContents(file: File, maxBytes: Long): List<Entry> {
        if (!file.isFile || file.length() > maxBytes) throw IOException("Invalid book settings entry size")
        val root = JSONObject(file.readText(Charsets.UTF_8))
        require(root.optInt("format", -1) == 1 && root.optString("application") == "liseur")
        val array = root.optJSONArray("books") ?: throw IllegalArgumentException("Missing books")
        val seen = mutableSetOf<String>()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: throw IllegalArgumentException("Invalid book settings")
                val id = item.optString("book_id").takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("Missing book id")
                require(seen.add(id)) { "Duplicate book settings id" }
                val title = item.optString("title")
                require(title.isNotBlank())
                val author = item.optString("author").takeIf { item.has("author") && it.isNotBlank() }
                val separate = item.optBoolean("separate", false)
                val own = if (separate) {
                    val value = item.optJSONObject("typography")
                        ?: throw IllegalArgumentException("Missing separate typography")
                    val font = value.optString("font").takeIf { it.isNotBlank() }
                        ?: throw IllegalArgumentException("Invalid font")
                    val size = value.optDouble("font_size", Double.NaN)
                    require(size.isFinite() && size in 0.5..3.0)
                    BookTypography(
                        bookUrl = id,
                        font = font,
                        fontSize = size,
                        lineHeight = value.nullableDouble("line_height"),
                        pageMargins = value.nullableDouble("page_margins"),
                    )
                } else {
                    require(!item.has("typography")) { "Typography present while separate is off" }
                    null
                }
                val custom = item.optJSONObject("custom_typography")?.let { value ->
                    BookCustomTypography(
                        bookUrl = id,
                        lineHeight = value.nullableDouble("line_height"),
                        textIndent = value.nullableDouble("text_indent"),
                    ).sanitized().also {
                        require(it.lineHeight == value.nullableDouble("line_height"))
                        require(it.textIndent == value.nullableDouble("text_indent"))
                    }
                }
                add(Entry(id, title, author, own, custom))
            }
        }
    }

    /**
     * Match by permanent URL first; when a file was re-picked on another device,
     * use an unambiguous title/author pair. Ambiguous matches are left untouched.
     */
    suspend fun restore(entries: List<Entry>): Int {
        val known = books.allOnce()
        val byUrl = known.associateBy { it.url }
        val matches = entries.map { entry ->
            entry to (byUrl[entry.bookId] ?: known.filter {
                it.title.equals(entry.title, ignoreCase = true) &&
                    (it.author ?: "").equals(entry.author ?: "", ignoreCase = true)
            }.singleOrNull())
        }.filter { it.second != null }
        val duplicateTargets = matches.groupingBy { it.second!!.url }.eachCount()
        var restored = 0
        for ((entry, target) in matches) {
            val book = requireNotNull(target)
            if (duplicateTargets.getValue(book.url) != 1) continue
            val own = entry.ownTypography
            if (own == null) typography.clear(book.url)
            else typography.upsert(own.copy(bookUrl = book.url))
            val custom = entry.customTypography
            if (custom == null) customTypography.clear(book.url)
            else customTypography.upsert(custom.copy(bookUrl = book.url))
            restored++
        }
        return restored
    }
}

private fun JSONObject.putNullable(name: String, value: Double?): JSONObject =
    if (value == null) put(name, JSONObject.NULL) else put(name, value)

private fun JSONObject.nullableDouble(name: String): Double? {
    if (!has(name) || isNull(name)) return null
    val value = optDouble(name, Double.NaN)
    require(value.isFinite()) { "Invalid $name" }
    return value
}
