package com.business.gym_app.ui.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.business.gym_app.ui.viewmodel.DailyWorkout
import com.business.gym_app.util.AppLanguage
import com.business.gym_app.util.GoogleTranslate
import com.business.gym_app.util.WorkoutDictionary

/**
 * Локализация карточек программ тренировок.
 *
 * Названия программ и упражнений хранятся одним языком (русским), поэтому при смене языка
 * перевод выполняется в два шага:
 *  1. встроенный словарь [WorkoutDictionary] — мгновенно и без сети (стандартные программы);
 *  2. всё, чего в словаре нет (программы администратора), допереводится через
 *     Google Cloud Translation API ([GoogleTranslate]) и подставляется после ответа.
 *
 * Пока идёт запрос, показывается исходный текст или перевод из кэша — UI не мигает и не блокируется.
 */

/** Заголовок программы тренировок на языке приложения. */
@Composable
fun rememberTranslatedTitle(title: String): String {
    val context = LocalContext.current
    val target = AppLanguage.current(context)
    val base = remember(title, target) { WorkoutDictionary.localizeTitle(title, target == "en") }
    var result by remember(base, target) {
        mutableStateOf(GoogleTranslate.cached(context, base, target) ?: base)
    }

    LaunchedEffect(base, target) {
        if (!GoogleTranslate.needsTranslation(base, target)) return@LaunchedEffect
        val translated = GoogleTranslate.translate(context, listOf(base), target)[base.trim()]
        if (!translated.isNullOrBlank()) result = translated
    }

    return result
}

/**
 * Программа тренировок (заголовок, названия и описания упражнений) на языке приложения.
 * [workout] может быть null — тогда и результат null.
 */
@Composable
fun rememberLocalizedWorkout(workout: DailyWorkout?): DailyWorkout? {
    val context = LocalContext.current
    val target = AppLanguage.current(context)
    val base = remember(workout, target) {
        workout?.let { WorkoutDictionary.localize(it, target == "en") }
    }
    var result by remember(base, target) {
        mutableStateOf(base?.let { applyTranslations(context, it, target, emptyMap()) })
    }

    LaunchedEffect(base, target) {
        val source = base ?: return@LaunchedEffect
        val texts = workoutTexts(source).filter { GoogleTranslate.needsTranslation(it, target) }
        if (texts.isEmpty()) return@LaunchedEffect
        val fresh = GoogleTranslate.translate(context, texts, target)
        if (fresh.isNotEmpty()) result = applyTranslations(context, source, target, fresh)
    }

    return result
}

/** Все тексты программы, которые могут требовать перевода. */
private fun workoutTexts(workout: DailyWorkout): List<String> =
    (listOf(workout.title) + workout.exercises.flatMap { listOf(it.name, it.desc) })
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/** Подставляет переводы ([fresh] — свежие из сети, затем кэш) в программу. */
private fun applyTranslations(
    context: Context,
    workout: DailyWorkout,
    target: String,
    fresh: Map<String, String>
): DailyWorkout {
    fun translate(text: String): String {
        val key = text.trim()
        if (key.isEmpty()) return text
        fresh[key]?.let { return it }
        return GoogleTranslate.cached(context, key, target) ?: text
    }

    return workout.copy(
        title = translate(workout.title),
        exercises = workout.exercises.map { exercise ->
            exercise.copy(name = translate(exercise.name), desc = translate(exercise.desc))
        }
    )
}
