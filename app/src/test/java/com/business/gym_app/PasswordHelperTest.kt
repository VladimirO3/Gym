package com.business.gym_app

import android.app.Application
import com.business.gym_app.util.PasswordHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Тесты обязательной смены пароля каждые 21 день.
 * PasswordHelper работает только с SharedPreferences, поэтому тестируется в JVM.
 */
class PasswordHelperTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = mock(Application::class.java)
        val prefs = InMemorySharedPreferences()
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
    }

    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun testNoMarkMeansExpired() {
        // Метки нет (первый вход после обновления) — пароль считается просроченным.
        assertTrue(PasswordHelper.isPasswordExpired(context, "user@test.com"))
    }

    @Test
    fun testFreshPasswordNotExpired() {
        PasswordHelper.markPasswordChanged(context, "user@test.com")
        assertFalse(PasswordHelper.isPasswordExpired(context, "user@test.com"))
        assertEquals(21L, PasswordHelper.passwordDaysLeft(context))
    }

    @Test
    fun testExpiredAfter21Days() {
        val now = 1_700_000_000_000L
        PasswordHelper.markPasswordChanged(context, "user@test.com", now)
        // Чуть меньше 21 дня — ещё действует.
        assertFalse(PasswordHelper.isPasswordExpired(context, "user@test.com", now + 21 * day - 1))
        // Ровно 21 день — срок вышел, нужна обязательная смена.
        assertTrue(PasswordHelper.isPasswordExpired(context, "user@test.com", now + 21 * day))
    }

    @Test
    fun testAnotherAccountExpired() {
        // Метка от другого аккаунта — тоже просрочено.
        PasswordHelper.markPasswordChanged(context, "other@test.com")
        assertTrue(PasswordHelper.isPasswordExpired(context, "user@test.com"))
    }

    @Test
    fun testAccountComparisonIgnoresCase() {
        PasswordHelper.markPasswordChanged(context, "User@Test.com")
        assertFalse(PasswordHelper.isPasswordExpired(context, "user@test.com"))
    }

    @Test
    fun testDaysLeftRoundsUp() {
        val now = 1_700_000_000_000L
        PasswordHelper.markPasswordChanged(context, "user@test.com", now)
        // Осталось чуть меньше суток — показываем «ещё 1 день».
        assertEquals(1L, PasswordHelper.passwordDaysLeft(context, now + 20 * day + 1))
        assertEquals(0L, PasswordHelper.passwordDaysLeft(context, now + 21 * day))
    }

    @Test
    fun testDaysSinceChange() {
        val now = 1_700_000_000_000L
        PasswordHelper.markPasswordChanged(context, "user@test.com", now)
        assertEquals(5L, PasswordHelper.daysSinceChange(context, now + 5 * day))
    }

    @Test
    fun testClearTracking() {
        PasswordHelper.markPasswordChanged(context, "user@test.com")
        PasswordHelper.clearPasswordTracking(context)
        assertTrue(PasswordHelper.isPasswordExpired(context, "user@test.com"))
    }

    @Test
    fun testMaxAgeIs21Days() {
        assertEquals(21L, PasswordHelper.PASSWORD_MAX_AGE_DAYS)
        assertEquals(21L * day, PasswordHelper.PASSWORD_MAX_AGE_MS)
    }
}