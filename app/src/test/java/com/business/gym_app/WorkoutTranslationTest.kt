package com.business.gym_app

import com.business.gym_app.ui.viewmodel.DailyWorkout
import com.business.gym_app.ui.viewmodel.Exercise
import com.business.gym_app.util.GoogleTranslate
import com.business.gym_app.util.WorkoutDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Перевод карточек программ тренировок: встроенный словарь и эвристики,
 * по которым Google Cloud Translation API понимает, что текст ещё не переведён.
 */
class WorkoutTranslationTest {

    @Test
    fun testCyrillicTitleNeedsEnglishTranslation() {
        assertTrue(GoogleTranslate.needsTranslation("Кардио и Выносливость", "en"))
    }

    @Test
    fun testEnglishTitleDoesNotNeedEnglishTranslation() {
        assertFalse(GoogleTranslate.needsTranslation("Cardio and Endurance", "en"))
    }

    @Test
    fun testLatinTitleNeedsRussianTranslation() {
        assertTrue(GoogleTranslate.needsTranslation("Cardio and Endurance", "ru"))
    }

    @Test
    fun testCyrillicTitleDoesNotNeedRussianTranslation() {
        assertFalse(GoogleTranslate.needsTranslation("Кардио и Выносливость", "ru"))
    }

    @Test
    fun testBlankTextAndUnsupportedTargetAreIgnored() {
        assertFalse(GoogleTranslate.needsTranslation("", "en"))
        assertFalse(GoogleTranslate.needsTranslation("   ", "en"))
        assertFalse(GoogleTranslate.needsTranslation("Кардио", "de"))
    }

    @Test
    fun testLanguageCodeIsNormalized() {
        assertEquals("ru", GoogleTranslate.normalize("ru-RU"))
        assertEquals("en", GoogleTranslate.normalize("EN"))
    }

    @Test
    fun testBuiltinTitleIsTranslatedFromDictionary() {
        assertEquals("Cardio and Endurance", WorkoutDictionary.localizeTitle("Кардио и Выносливость", true))
        assertEquals("Пресс и Кор", WorkoutDictionary.localizeTitle("Пресс и Кор", false))
    }

    @Test
    fun testUnknownTitleIsKeptAsIs() {
        assertEquals("Моя программа", WorkoutDictionary.localizeTitle("Моя программа", true))
    }

    @Test
    fun testDictionaryLocalizesExercisesAndDescriptions() {
        val workout = DailyWorkout(
            title = "Пресс и Кор",
            exercises = listOf(Exercise(name = "Планка", iconUrl = "", desc = "3 по 1 мин"))
        )

        val localized = WorkoutDictionary.localize(workout, english = true)

        assertEquals("Abs and Core", localized.title)
        assertEquals("Plank", localized.exercises.first().name)
        assertEquals("3 for 1 min", localized.exercises.first().desc)
    }

    @Test
    fun testRussianModeKeepsWorkoutUntouched() {
        val workout = DailyWorkout(
            title = "Пресс и Кор",
            exercises = listOf(Exercise(name = "Планка", iconUrl = "", desc = "3 по 1 мин"))
        )

        assertEquals(workout, WorkoutDictionary.localize(workout, english = false))
    }

    @Test
    fun testTranslatedTextNeedsNoFurtherTranslation() {
        val localized = WorkoutDictionary.localizeTitle("Кардио и Выносливость", true)
        assertFalse(GoogleTranslate.needsTranslation(localized, "en"))
    }
}
