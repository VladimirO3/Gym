package com.business.gym

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GymUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun getResourceString(id: Int): String {
        return composeTestRule.activity.getString(id)
    }

    @Test
    fun testNavigationTabsExist() {
        // Use hasText() specifically to avoid matching icons with the same contentDescription
        // and assertExists() in case they are off-screen in the ScrollableTabRow
        composeTestRule.onNode(hasText(getResourceString(R.string.tab_news))).assertExists()
        composeTestRule.onNode(hasText(getResourceString(R.string.tab_playlist))).assertExists()
        composeTestRule.onNode(hasText(getResourceString(R.string.tab_chat))).assertExists()
        composeTestRule.onNode(hasText(getResourceString(R.string.tab_settings))).assertExists()
        composeTestRule.onNode(hasText(getResourceString(R.string.tab_about))).assertExists()
    }

    @Test
    fun testAuthButtonExists() {
        composeTestRule.onNode(hasText(getResourceString(R.string.auth_login_reg))).assertExists()
    }

    @Test
    fun testAuthOverlayShowsOnAuthClick() {
        // performClick() will try to click the node, usually scrolling it into view if needed
        composeTestRule.onNode(hasText(getResourceString(R.string.auth_login_reg))).performClick()
        
        // Check if AuthScreen is shown by looking for the "Email" label
        composeTestRule.onNode(hasText(getResourceString(R.string.auth_email_label))).assertIsDisplayed()
    }

    @Test
    fun testExitButtonExists() {
        composeTestRule.onNode(hasText(getResourceString(R.string.auth_exit))).assertExists()
    }

    @Test
    fun testNavigationToSettings() {
        // Navigate to settings
        composeTestRule.onNode(hasText(getResourceString(R.string.tab_settings))).performClick()
        
        // Verify we are on Settings screen by checking for specific labels
        composeTestRule.onNode(hasText(getResourceString(R.string.settings_theme_mode))).assertIsDisplayed()
        composeTestRule.onNode(hasText(getResourceString(R.string.settings_language))).assertIsDisplayed()
    }
}
