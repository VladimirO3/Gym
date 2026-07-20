package com.business.gym

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GymUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testNavigationTabsExist() {
        // Check if News tab is displayed
        composeTestRule.onNodeWithText("News").assertIsDisplayed()
        
        // Check if Playlist tab is displayed
        composeTestRule.onNodeWithText("Playlist").assertIsDisplayed()
    }

    @Test
    fun testLoginButtonExists() {
        // Check if the Login button is displayed in the top right
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()
    }

    @Test
    fun testRegisterButtonExists() {
        // Check if the Register button is displayed in the top right
        composeTestRule.onNodeWithText("Register").assertIsDisplayed()
    }

    @Test
    fun testAuthOverlayShowsOnLoginClick() {
        // Click the login button
        composeTestRule.onNodeWithText("Login").performClick()
        
        // Check if "User Login" text appears in the AuthScreen
        composeTestRule.onNodeWithText("User Login").assertIsDisplayed()
    }

    @Test
    fun testAuthOverlayShowsOnRegisterClick() {
        // Click the register button
        composeTestRule.onNodeWithText("Register").performClick()
        
        // Check if "Create Account" text appears in the AuthScreen
        composeTestRule.onNodeWithText("Create Account").assertIsDisplayed()
    }
}
