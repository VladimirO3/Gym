package com.business.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Тесты для проверки логики обработки идентификаторов пользователей при удалении.
 */
class UserDeletionTest {

    @Test
    fun testUidParsing_NumericString() {
        val uid = "123"
        val numericId = uid.toIntOrNull()
        assertNotNull(numericId)
        assertEquals(123, numericId)
    }

    @Test
    fun testUidParsing_EmailString() {
        val uid = "user@test.com"
        val numericId = uid.toIntOrNull()
        assertNull(numericId)
    }

    @Test
    fun testUidParsing_AlphaNumericString() {
        val uid = "user123"
        val numericId = uid.toIntOrNull()
        assertNull(numericId)
    }
}
