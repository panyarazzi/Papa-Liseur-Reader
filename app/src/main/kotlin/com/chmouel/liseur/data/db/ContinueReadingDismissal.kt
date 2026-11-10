package com.chmouel.liseur.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A local, reversible choice to keep a book off the Continue Reading strip.
 * It is deliberately separate from the library's hidden and finished states.
 */
@Entity(
    tableName = "continue_reading_dismissals",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["url"],
            childColumns = ["book_url"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["book_url"])],
)
data class ContinueReadingDismissal(
    @PrimaryKey @ColumnInfo(name = "book_url") val bookUrl: String,
)
