package com.fiw.tools.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextCodesTest {
    @Test
    fun splitsColorAndStyleSegments() {
        val segments = TextCodes.segments("&b&lStorm &7Blade")

        assertEquals("Storm ", segments[0].text)
        assertEquals(setOf('b', 'l'), segments[0].codes)
        assertEquals("Blade", segments[1].text)
        assertEquals(setOf('7'), segments[1].codes)
    }

    @Test
    fun resetClearsFormatting() {
        val segments = TextCodes.segments("&cCursed&r Normal")

        assertEquals("Cursed", segments[0].text)
        assertEquals(setOf('c'), segments[0].codes)
        assertEquals(" Normal", segments[1].text)
        assertTrue(segments[1].codes.isEmpty())
    }

    @Test
    fun unknownCodesRemainLiteral() {
        val segments = TextCodes.segments("&xLiteral")

        assertEquals(1, segments.size)
        assertEquals("&xLiteral", segments.single().text)
    }
}
