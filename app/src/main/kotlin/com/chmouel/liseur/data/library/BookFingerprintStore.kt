package com.chmouel.liseur.data.library

import android.content.Context
import android.net.Uri
import android.util.Log
import com.chmouel.liseur.data.db.Book
import com.chmouel.liseur.data.db.BookFingerprintRow
import com.chmouel.liseur.data.db.WorkIdentityDao
import com.chmouel.liseur.domain.BookFingerprint
import com.chmouel.liseur.domain.BookFingerprints
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The hash of a book's file, worked out once and remembered.
 *
 * Hashing means reading the whole file, which on a phone with a large
 * library on a memory card is slow enough to be felt. So it happens
 * lazily — when something actually needs to name the book to a server,
 * never during a library scan — off the main thread, and the answer is
 * kept.
 *
 * What is kept is tied to the file's modification time. A file rewritten
 * in place is a different set of bytes, and sending reading positions
 * under the old file's name would attach them to the wrong book on every
 * other device.
 */
class BookFingerprintStore(
    private val context: Context,
    private val dao: WorkIdentityDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * The fingerprint of [book]'s file, or null when there is no file
     * here to hash.
     *
     * A book known only from a server catalog has nothing to fingerprint
     * and must fall back to its own identifier and its title; that is
     * not a failure and is not logged as one.
     */
    suspend fun of(book: Book): BookFingerprint? {
        val url = book.openableUri() ?: return null

        val cached = dao.fingerprint(book.url)
        if (cached != null && cached.fileModifiedAt == book.fileModifiedAt) {
            return cached.fingerprint
        }

        val fresh = read(url) ?: return null
        dao.upsert(
            BookFingerprintRow(
                bookUrl = book.url,
                sha256 = fresh.sha256,
                partialMd5 = fresh.partialMd5,
                fileSize = fresh.size,
                fileModifiedAt = book.fileModifiedAt,
                computedAt = now(),
            ),
        )
        return fresh
    }

    /**
     * The fingerprint of a file that has no library row yet.
     *
     * Nothing is cached, because there is nothing to cache it against:
     * this answers for a file the reader has only just picked, which
     * may turn out to be a book the library already has and may never
     * be shelved at all.
     */
    suspend fun compute(url: String): BookFingerprint? = read(url)

    /**
     * Reads the current bytes even when the provider reports the same (or no)
     * modification timestamp. SAF providers are not consistent about updating
     * COLUMN_LAST_MODIFIED when a document is overwritten.
     *
     * The Boolean is true when no baseline exists or the bytes differ. The
     * caller commits the new fingerprint only after the EPUB was re-indexed,
     * so a temporarily unreadable/invalid replacement is retried on the next scan.
     */
    suspend fun inspectCurrent(book: Book, sourceUrl: String): Pair<BookFingerprint, Boolean>? {
        val fresh = read(sourceUrl) ?: return null
        val cached = dao.fingerprint(book.url)
        val changed = cached == null ||
            cached.sha256 != fresh.sha256 ||
            cached.fileSize != fresh.size
        return fresh to changed
    }

    /** Stores a fingerprint only after the source has been read successfully. */
    suspend fun remember(bookUrl: String, fileModifiedAt: Long?, fingerprint: BookFingerprint) {
        dao.upsert(
            BookFingerprintRow(
                bookUrl = bookUrl,
                sha256 = fingerprint.sha256,
                partialMd5 = fingerprint.partialMd5,
                fileSize = fingerprint.size,
                fileModifiedAt = fileModifiedAt,
                computedAt = now(),
            ),
        )
    }

    private suspend fun read(url: String): BookFingerprint? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(Uri.parse(url))?.use(BookFingerprints::of)
        } catch (e: IOException) {
            Log.i(TAG, "Could not read the file to fingerprint it", e)
            null
        } catch (e: SecurityException) {
            // A folder whose permission was not persisted, or was
            // revoked when the app was reinstalled.
            Log.i(TAG, "No longer allowed to read the file to fingerprint it", e)
            null
        }
    }

    private companion object {
        const val TAG = "book-fingerprint"
    }
}
