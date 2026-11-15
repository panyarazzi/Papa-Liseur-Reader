package com.chmouel.liseur.diagnostics

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosticHistoryTest {
    @Test
    fun `chat export keeps every part below the full character limit`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "diagnostics").deleteRecursively()
        val history = DiagnosticHistory(context)

        repeat(70) { index ->
            history.record(
                event = "pagination.measurement",
                attributes = mapOf(
                    "resourceIndex" to index.toString(),
                    "diagnosticPayload" to "x".repeat(256),
                ),
            )
        }

        val parts = history.exportParts(maxCharacters = 8_000)

        assertTrue(parts.size > 1)
        assertEquals(parts.size, parts.last().count)
        parts.forEachIndexed { index, part ->
            assertEquals(index + 1, part.index)
            assertEquals(part.text.length, part.characterCount)
            assertTrue("Part ${part.index} exceeded limit", part.characterCount <= 8_000)
            assertTrue(part.text.contains("COMMIT:"))
            assertTrue(part.text.contains("PART: ${part.index}/${parts.size}"))
            assertTrue(part.text.endsWith("--- END PART ${part.index}/${parts.size} ---"))
        }
    }
}
