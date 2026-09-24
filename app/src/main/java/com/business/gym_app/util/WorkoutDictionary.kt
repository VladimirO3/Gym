package com.business.gym_app.util

import com.business.gym_app.ui.viewmodel.DailyWorkout

/**
 * Встроенный словарь переводов стандартных программ тренировок.
 *
 * Работает офлайн и мгновенно, поэтому применяется первым; всё, чего в словаре нет
 * (программы, созданные администратором), допереводится через [GoogleTranslate].
 */
object WorkoutDictionary {
    private val titles = mapOf(
        "Силовая: Ноги и ягодицы" to "Strength: Legs and Glutes",
        "Верх тела: Грудь и Спина" to "Upper Body: Chest and Back",
        "Кардио и Выносливость" to "Cardio and Endurance",
        "Пресс и Кор" to "Abs and Core",
        "Руки: Бицепс и Трицепс" to "Arms: Biceps and Triceps"
    )

    private val names = mapOf(
        "Приседания" to "Squats", "Жим ногами" to "Leg Press", "Выпады" to "Lunges",
        "Разгибание ног" to "Leg Extensions", "Сгибание ног" to "Leg Curls",
        "Подъем на носки" to "Calf Raises", "Жим лежа" to "Bench Press",
        "Тяга блока" to "Lat Pulldown", "Отжимания" to "Push-ups",
        "Разводка гантелей" to "Dumbbell Flyes", "Тяга гантели" to "Dumbbell Row",
        "Гиперэкстензия" to "Hyperextensions", "Бег" to "Running", "Берпи" to "Burpees",
        "Скакалка" to "Jump Rope", "Джампинг Джек" to "Jumping Jacks",
        "Альпинист" to "Mountain Climbers", "Прыжки на бокс" to "Box Jumps",
        "Скручивания" to "Crunches", "Планка" to "Plank", "Велосипед" to "Bicycle Crunches",
        "Боковая планка" to "Side Plank", "Подъем ног" to "Leg Raises",
        "Русский твист" to "Russian Twist", "Подъем гантелей" to "Shoulder Press",
        "Обратные отжимания" to "Bench Dips", "Молотки" to "Hammer Curls",
        "Франц. жим" to "Skull Crushers", "Конц. подъем" to "Concentration Curls",
        "Разгибания рук" to "Triceps Pushdown"
    )

    private val descriptions = mapOf(
        "до отказа" to "to failure", "минут" to "minutes", "интенсивно" to "intense",
        "пульс" to "heart rate", "раз" to "reps", "подх." to "sets", "сек" to "sec",
        "по 1 мин" to "for 1 min",
        " по " to " of "
    )

    /** Заголовок программы из словаря (или исходный, если перевода нет / язык не английский). */
    fun localizeTitle(title: String, english: Boolean): String =
        if (english) titles[title] ?: title else title

    /** Программа целиком: заголовок, названия упражнений и их описания из словаря. */
    fun localize(workout: DailyWorkout, english: Boolean): DailyWorkout {
        if (!english) return workout
        return workout.copy(
            title = localizeTitle(workout.title, english),
            exercises = workout.exercises.map { exercise ->
                var description = exercise.desc
                descriptions.forEach { (russian, englishText) ->
                    description = description.replace(russian, englishText)
                }
                exercise.copy(name = names[exercise.name] ?: exercise.name, desc = description)
            }
        )
    }
}
