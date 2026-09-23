package com.business.gym_app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GymApplicationTest {

    private fun isSystemDeathNotificationException(throwable: Throwable?): Boolean {
        var current = throwable
        while (current != null) {
            if (current is SecurityException) {
                val msg = current.message ?: ""
                if (msg.contains("com.google.android.googlequicksearchbox") ||
                    msg.contains("under uid 1000") ||
                    msg.contains("stopWatchingAsyncNoted") ||
                    msg.contains("verifyAndGetBypass")
                ) {
                    return true
                }
            }
            val hasAppOpsInStack = current.stackTrace.any { element ->
                element.className.contains("AppOpsService") ||
                element.methodName.contains("stopWatchingAsyncNoted") ||
                element.methodName.contains("verifyAndGetBypass")
            }
            if (hasAppOpsInStack) {
                return true
            }
            current = current.cause
        }
        return false
    }

    @Test
    fun testAppOpsDeathNotificationSecurityExceptionIsDetected() {
        val ex = SecurityException("Specified package \"com.google.android.googlequicksearchbox\" under uid 1000 but it is not")
        ex.stackTrace = arrayOf(
            StackTraceElement("com.android.server.appop.AppOpsService", "verifyAndGetBypass", "AppOpsService.java", 63),
            StackTraceElement("com.android.server.appop.AppOpsService", "stopWatchingAsyncNoted", "AppOpsService.java", 21)
        )

        assertTrue(isSystemDeathNotificationException(ex))
    }

    @Test
    fun testAppOpsDeathNotificationExceptionAsCauseIsDetected() {
        val rootCause = SecurityException("Specified package \"com.google.android.googlequicksearchbox\" under uid 1000 but it is not")
        val wrapper = RuntimeException("Uncaught exception from death notification", rootCause)

        assertTrue(isSystemDeathNotificationException(wrapper))
    }

    @Test
    fun testNormalExceptionIsNotDetected() {
        val ex = NullPointerException("Something was null")
        assertFalse(isSystemDeathNotificationException(ex))
    }
}
