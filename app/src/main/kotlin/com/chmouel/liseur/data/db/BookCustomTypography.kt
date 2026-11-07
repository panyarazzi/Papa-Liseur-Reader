package com.chmouel.liseur.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Custom paragraph metrics chosen for one EPUB, independent of shared typography. */
@Entity(tableName = "book_custom_typography")
data class BookCustomTypography(
    @PrimaryKey @ColumnInfo(name = "book_url") val bookUrl: String,
    @ColumnInfo(name = "line_height") val lineHeight: Double? = null,
    @ColumnInfo(name = "text_indent") val textIndent: Double? = null,
) {
    fun sanitized(): BookCustomTypography = copy(
        lineHeight = lineHeight?.takeIf { it.isFinite() && it in 0.1..10.0 },
        textIndent = textIndent?.takeIf { it.isFinite() && it in -20.0..20.0 },
    )
}

@Dao
interface BookCustomTypographyDao {
    @Query("SELECT * FROM book_custom_typography WHERE book_url = :bookUrl")
    fun observe(bookUrl: String): Flow<BookCustomTypography?>

    @Query("SELECT * FROM book_custom_typography WHERE book_url = :bookUrl")
    suspend fun get(bookUrl: String): BookCustomTypography?

    @Query("SELECT * FROM book_custom_typography ORDER BY book_url")
    suspend fun all(): List<BookCustomTypography>

    @Upsert
    suspend fun upsert(typography: BookCustomTypography)

    @Query("DELETE FROM book_custom_typography WHERE book_url = :bookUrl")
    suspend fun clear(bookUrl: String)
}
