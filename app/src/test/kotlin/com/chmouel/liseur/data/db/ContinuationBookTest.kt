package com.chmouel.liseur.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chmouel.liseur.domain.FINISHED_PROGRESSION
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class ContinuationBookTest {
    private lateinit var db: LiseurDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), LiseurDatabase::class.java,
        ).build()
    }

    @After
    fun close() = db.close()

    private fun book(url: String, opened: Long? = 100) = Book(
        url = url, title = url, author = null, coverPath = null, source = null,
        addedAt = 0, lastOpenedAt = opened,
    )

    private suspend fun selected() = db.bookDao().continuationBook(FINISHED_PROGRESSION)?.url

    @Test
    fun `continue reading lists only real unfinished positions newest first`() = runTest {
        val dao = db.bookDao()
        dao.upsert(book("opened-only", 1_000))
        // Opening timestamps deliberately disagree with the actual reading
        // order: Continue Reading must follow read_at, not last_opened_at.
        dao.upsert(book("older", 900))
        dao.upsert(book("middle", 800))
        dao.upsert(book("newest", 700))
        dao.upsert(book("finished").copy(finishedAt = 1))
        dao.upsert(book("archived").copy(archivedAt = 1))
        dao.upsert(book("hidden").copy(hiddenAt = 1))
        dao.upsert(book("remote").copy(downloadState = DownloadState.REMOTE))
        dao.upsert(book("restored-remote").copy(
            downloadState = DownloadState.REMOTE,
            localUri = "content://restored/document",
        ))

        val progress = db.readingProgressDao()
        progress.restoreBackupPosition(
            "older", """{"href":"older"}""", 0.2, readAt = 100, now = 1_000,
        )
        progress.restoreBackupPosition(
            "middle", """{"href":"middle"}""", 0.4, readAt = 200, now = 1_000,
        )
        progress.restoreBackupPosition(
            "newest", """{"href":"newest"}""", 0.6, readAt = 300, now = 1_000,
        )
        // Even a valid position cannot put a finished, archived, hidden or
        // remote-only book on the local Continue Reading shelf.
        progress.restoreBackupPosition(
            "finished", """{"href":"finished"}""", 0.2, readAt = 700, now = 1_000,
        )
        progress.restoreBackupPosition(
            "archived", """{"href":"archived"}""", 0.2, readAt = 600, now = 1_000,
        )
        progress.restoreBackupPosition(
            "hidden", """{"href":"hidden"}""", 0.2, readAt = 500, now = 1_000,
        )
        progress.restoreBackupPosition(
            "remote", """{"href":"remote"}""", 0.2, readAt = 400, now = 1_000,
        )
        progress.restoreBackupPosition(
            "restored-remote", """{"href":"restored"}""", 0.2,
            readAt = 350, now = 1_000,
        )
        // A row created for a declined sync has no position and must not
        // appear, even though it has a recent timestamp.
        progress.restoreBackupPosition(
            "opened-only", "{}", null, readAt = 900, now = 1_000,
        )
        // A book at the completion boundary is no longer a continuation.
        dao.upsert(book("at-completion", 80))
        progress.restoreBackupPosition(
            "at-completion", """{"href":"end"}""", FINISHED_PROGRESSION,
            readAt = 800, now = 1_000,
        )

        val urls = dao.observeContinueReading(FINISHED_PROGRESSION)
            .first()
            .map { it.url }

        assertEquals(listOf("newest", "middle", "older", "restored-remote"), urls)
    }

    @Test
    fun `skips newer ineligible books without changing ordinary most recent`() = runTest {
        db.bookDao().upsert(book("older", 50))
        for (recent in listOf(
            book("finished").copy(finishedAt = 1),
            book("archived").copy(archivedAt = 1),
            book("hidden").copy(hiddenAt = 1),
            book("remote").copy(downloadState = DownloadState.REMOTE),
            book("queued").copy(downloadState = DownloadState.QUEUED),
            book("failed").copy(downloadState = DownloadState.FAILED),
        )) {
            db.bookDao().upsert(recent)
        }
        assertEquals("older", selected())
        assertEquals(100L, db.bookDao().mostRecentlyOpened()?.lastOpenedAt)
    }

    @Test
    fun `completion boundary is exact and unknown progress is eligible`() = runTest {
        db.bookDao().upsert(book("recent"))
        db.bookDao().upsert(book("older", 50))
        db.readingProgressDao().recordLocal("recent", """{"href":"chapter"}""", 0.97, null, null, 1)
        assertEquals("older", selected())
        db.readingProgressDao().recordLocal("recent", """{"href":"chapter"}""", 0.969, null, null, 2)
        assertEquals("recent", selected())
        db.readingProgressDao().recordLocal("recent", """{"href":"chapter"}""", null, null, null, 3)
        assertEquals("recent", selected())
    }

    @Test
    fun `synced locator alone counts but an empty placeholder does not`() = runTest {
        db.bookDao().upsert(book("placeholder", null))
        db.readingProgressDao().recordLocal("placeholder", "{}", null, null, null, 900)
        assertNull(selected())
        db.bookDao().upsert(book("synced", null))
        db.readingProgressDao().recordLocal("synced", """{"href":"chapter"}""", null, null, null, 100)
        assertEquals("synced", selected())
    }

    @Test
    fun `recent reading anywhere wins over local opening and import time`() = runTest {
        db.bookDao().upsert(book("local", 200))
        db.bookDao().upsert(book("synced", null))
        db.readingProgressDao().restoreBackupPosition(
            "synced", """{"href":"chapter"}""", 0.4, readAt = 300, now = 1000,
        )
        assertEquals("synced", selected())
        db.readingProgressDao().restoreBackupPosition(
            "synced", """{"href":"chapter"}""", 0.4, readAt = 100, now = 2000,
        )
        assertEquals("local", selected())
    }

    @Test
    fun `a newer placeholder row does not outrank a more recently opened book`() = runTest {
        db.bookDao().upsert(book("older", 50))
        db.bookDao().upsert(book("newer", 100))
        db.readingProgressDao().insertPending("older", 0.5, null, null, "account", now = 900)
        assertEquals("newer", selected())
    }

    @Test
    fun `a restored document URI remains available even for a remote row`() = runTest {
        db.bookDao().upsert(book("restored").copy(
            downloadState = DownloadState.REMOTE, localUri = "content://restored/document",
        ))
        assertEquals("restored", selected())
    }

    @Test
    fun `no reading evidence or no eligible books yields no candidate`() = runTest {
        assertNull(selected())
        db.bookDao().upsert(book("never opened", null))
        db.bookDao().upsert(book("finished").copy(finishedAt = 1))
        assertNull(selected())
    }
}
