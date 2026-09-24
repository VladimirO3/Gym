package com.business.gym_app

import com.business.gym_app.util.formatNewsText
import com.business.gym_app.util.formatNewsTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class NewsTextFormatTest {

    @Test
    fun `collapses multiple spaces inside a line`() {
        assertEquals("Привет мир", formatNewsText("Привет   мир"))
        assertEquals("а б в", formatNewsText("а   б     в"))
    }

    @Test
    fun `replaces non-breaking and exotic spaces with regular ones`() {
        assertEquals("а б", formatNewsText("а\u00A0\u202Fб"))
        assertEquals("а б", formatNewsText("а\tб"))
        assertEquals("а б", formatNewsText("а\u3000б"))
    }

    @Test
    fun `keeps at most one blank line between paragraphs`() {
        assertEquals("А\n\nБ", formatNewsText("А\n\n\n\nБ"))
        assertEquals("А\n\nБ", formatNewsText("А\n \n Б"))
    }

    @Test
    fun `trims edges and normalizes line endings`() {
        assertEquals("А\nБ", formatNewsText("  А\r\nБ  \r\n\r\n"))
        assertEquals("А\nБ", formatNewsText("\n\n А \n Б \n\n"))
    }

    @Test
    fun `title becomes single line`() {
        assertEquals("Заголовок новости", formatNewsTitle("  Заголовок\u00A0  новости \n"))
        assertEquals("", formatNewsTitle("   \n  "))
    }

    @Test
    fun `keeps single newlines as paragraph breaks`() {
        assertEquals("Строка 1\nСтрока 2", formatNewsText("Строка 1\nСтрока 2"))
    }

    @Test
    fun `empty and blank input stays empty`() {
        assertEquals("", formatNewsText(""))
        assertEquals("", formatNewsText("   \n\n  "))
    }
}
