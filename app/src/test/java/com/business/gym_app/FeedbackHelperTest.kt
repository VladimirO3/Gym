package com.business.gym_app

import android.app.Application
import com.business.gym_app.util.FeedbackHelper
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
 * Тесты периодического запроса отзыва (раз в 10 дней) и письма разработчику.
 * FeedbackHelper работает только с SharedPreferences и текстом — тестируется в JVM.
 */
class FeedbackHelperTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = mock(Application::class.java)
        val prefs = InMemorySharedPreferences()
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
    }

    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_700_000_000_000L
    private val account = "user@test.com"

    @Test
    fun testIntervalIsTenDays() {
        assertEquals(10L, FeedbackHelper.FEEDBACK_INTERVAL_DAYS)
    }

    @Test
    fun testDeveloperEmail() {
        assertEquals("veerso0100@gmail.com", FeedbackHelper.DEVELOPER_EMAIL)
    }

    @Test
    fun testFirstTimeAsksImmediately() {
        assertTrue(FeedbackHelper.shouldAskForFeedback(context, account, now))
    }

    @Test
    fun testGuestAndEmptyAccountAreNotAsked() {
        assertFalse(FeedbackHelper.shouldAskForFeedback(context, null, now))
        assertFalse(FeedbackHelper.shouldAskForFeedback(context, "  ", now))
    }

    @Test
    fun testNotAskedAgainWithin10Days() {
        FeedbackHelper.markShown(context, account, now)
        assertFalse(FeedbackHelper.shouldAskForFeedback(context, account, now))
        assertFalse(FeedbackHelper.shouldAskForFeedback(context, account, now + 9 * day))
        // Из 10 дней прошло 9 — остался ровно 1 день до следующего окна.
        assertEquals(1L, FeedbackHelper.daysUntilNextRequest(context, now + 9 * day))
    }

    @Test
    fun testAskedAgainAfter10Days() {
        FeedbackHelper.markShown(context, account, now)
        assertTrue(FeedbackHelper.shouldAskForFeedback(context, account, now + 10 * day))
        assertEquals(10L, FeedbackHelper.daysSinceLastShown(context, now + 10 * day))
        assertEquals(0L, FeedbackHelper.daysUntilNextRequest(context, now + 10 * day))
    }

    @Test
    fun testAccountComparisonIgnoresCase() {
        // Смена регистра в логине не должна сбрасывать счётчик.
        FeedbackHelper.markShown(context, "User@Test.com", now)
        assertFalse(FeedbackHelper.shouldAskForFeedback(context, "USER@test.com", now + day))
    }

    @Test
    fun testOtherAccountAsksRightAway() {
        FeedbackHelper.markShown(context, "other@test.com", now)
        assertTrue(FeedbackHelper.shouldAskForFeedback(context, account, now))
    }

    @Test
    fun testClearTracking() {
        FeedbackHelper.markShown(context, account, now)
        FeedbackHelper.clearTracking(context)
        assertTrue(FeedbackHelper.shouldAskForFeedback(context, account, now))
    }

    @Test
    fun testBodyContainsRatingCommentAccountAndVersion() {
        val body = FeedbackHelper.buildFeedbackBody(4, "Отличное приложение", account, "2.1.0")
        assertTrue(body.contains("4/5"))
        assertTrue(body.contains("★★★★"))   // 4 заполненные звезды
        assertTrue(body.contains("Отличное приложение"))
        assertTrue(body.contains(account))
        assertTrue(body.contains("2.1.0"))
    }

    @Test
    fun testBodyWithEmptyCommentStillValid() {
        val body = FeedbackHelper.buildFeedbackBody(1, "   ", null, "1.0")
        assertTrue(body.contains("1/5"))
        assertTrue(body.contains("★"))       // есть хотя бы одна звезда
        assertTrue(body.contains("не указаны"))
        assertTrue(body.contains("неизвестно"))
    }
}