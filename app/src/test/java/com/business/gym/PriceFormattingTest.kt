package com.business.gym

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PriceFormattingTest {

    @Test
    fun testPriceFormatting() {
        val price = 1500
        val formatted = String.format(Locale("ru", "RU"), "%, d", price).replace(",", " ").trim() + " ₽"
        assertEquals("1 500 ₽", formatted)
    }

    @Test
    fun testPriceParsing() {
        val priceString = "1 500 ₽"
        val priceCleaned = priceString.replace(" ", "").replace("\u00A0", "")
        val match = Regex("(\\d+)").find(priceCleaned)
        val priceInt = match?.value?.toIntOrNull() ?: 0
        assertEquals(1500, priceInt)
    }
}
