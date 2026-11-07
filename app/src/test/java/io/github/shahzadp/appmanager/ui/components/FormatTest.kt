package io.github.shahzadp.appmanager.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun formatsBytesHumanReadable() {
        assertEquals("—", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("3.0 MB", formatBytes(3_181_792))
        assertEquals("1.0 KB", formatBytes(1024))
    }

    @Test
    fun cleanNotesStripsMarkdownButKeepsText() {
        val markdown = """
            ## What's new

            - **Faster** start-up
            - Fixed [the crash](https://example.com) on launch
            ![screenshot](https://example.com/a.png)
        """.trimIndent()

        val cleaned = cleanNotes(markdown)
        assertEquals(
            "What's new\n\n•  Faster start-up\n•  Fixed the crash on launch",
            cleaned,
        )
    }

    @Test
    fun cleanNotesFallsBackWhenEmpty() {
        assertEquals(
            "No release notes were published for this version.",
            cleanNotes(""),
        )
    }
}
