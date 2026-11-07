package com.chmouel.liseur.data.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chmouel.liseur.data.db.Book
import com.chmouel.liseur.data.db.BookCustomTypography
import com.chmouel.liseur.data.db.BookTypography
import com.chmouel.liseur.data.db.LiseurDatabase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class BookSettingsBackupRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: LiseurDatabase
    private lateinit var repository: BookSettingsBackupRepository

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, LiseurDatabase::class.java).build()
        repository = BookSettingsBackupRepository(
            db.bookDao(), db.typographyDao(), db.customTypographyDao(),
        )
    }

    @After
    fun close() {
        db.close()
    }

    private fun book(url: String, title: String) = Book(
        url = url,
        title = title,
        author = "Author",
        coverPath = null,
        source = null,
        addedAt = 1,
        lastOpenedAt = null,
    )

    @Test
    fun `round trip preserves Separate and custom metrics and clears stale overrides`() = runTest {
        val separateBook = book("file:///one.epub", "One")
        val sharedBook = book("file:///two.epub", "Two")
        db.bookDao().upsertAll(listOf(separateBook, sharedBook))
        val own = BookTypography(
            bookUrl = separateBook.url,
            font = "bundled:serif",
            fontSize = 1.25,
            lineHeight = 1.45,
            pageMargins = 0.08,
        )
        val custom = BookCustomTypography(
            bookUrl = separateBook.url,
            lineHeight = 1.6,
            textIndent = 2.4,
        )
        db.typographyDao().upsert(own)
        db.customTypographyDao().upsert(custom)

        // The destination has stale values for a book which was not separate
        // and had no custom paragraph metrics in the source archive.
        db.typographyDao().upsert(own.copy(bookUrl = sharedBook.url))
        db.customTypographyDao().upsert(custom.copy(bookUrl = sharedBook.url))

        val file = File(folder.root, "book-settings.json")
        repository.writeContents(file, 4L * 1024 * 1024)
        db.typographyDao().clear(separateBook.url)
        db.customTypographyDao().clear(separateBook.url)

        val entries = repository.readContents(file, 4L * 1024 * 1024)
        assertEquals(2, entries.size)
        assertTrue(entries.single { it.bookId == separateBook.url }.ownTypography != null)
        assertFalse(entries.single { it.bookId == sharedBook.url }.ownTypography != null)

        assertEquals(2, repository.restore(entries))
        assertEquals(own, db.typographyDao().get(separateBook.url))
        assertEquals(custom, db.customTypographyDao().get(separateBook.url))
        assertEquals(null, db.typographyDao().get(sharedBook.url))
        assertEquals(null, db.customTypographyDao().get(sharedBook.url))
    }

    @Test
    fun `unique title and author match restores settings when file URL changed`() = runTest {
        val sourceBook = book("file:///old/location/book.epub", "Same title")
        val destinationBook = book("file:///new/location/book.epub", "Same title")
        db.bookDao().upsert(destinationBook)
        val own = BookTypography(sourceBook.url, "bundled:serif", 1.1, 1.3, 0.05)
        val custom = BookCustomTypography(sourceBook.url, 1.7, 1.2)
        val file = File(folder.root, "book-settings.json")
        // Export the source identity from a small source database is represented
        // by the same versioned entry shape used by writeContents.
        val payload = org.json.JSONObject()
            .put("format", 1)
            .put("application", "liseur")
            .put("books", org.json.JSONArray().put(
                org.json.JSONObject()
                    .put("book_id", sourceBook.url)
                    .put("title", sourceBook.title)
                    .put("author", sourceBook.author)
                    .put("separate", true)
                    .put("typography", org.json.JSONObject()
                        .put("font", own.font)
                        .put("font_size", own.fontSize)
                        .put("line_height", own.lineHeight)
                        .put("page_margins", own.pageMargins))
                    .put("custom_typography", org.json.JSONObject()
                        .put("line_height", custom.lineHeight)
                        .put("text_indent", custom.textIndent))
            ))
        file.writeText(payload.toString())
        val entries = repository.readContents(file, 4L * 1024 * 1024)
        assertEquals(1, repository.restore(entries))
        assertEquals(own.copy(bookUrl = destinationBook.url), db.typographyDao().get(destinationBook.url))
        assertEquals(custom.copy(bookUrl = destinationBook.url), db.customTypographyDao().get(destinationBook.url))
    }
}
