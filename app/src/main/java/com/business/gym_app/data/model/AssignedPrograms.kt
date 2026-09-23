package com.business.gym_app.data.model

import android.content.Context
import androidx.annotation.Keep
import com.business.gym_app.ui.viewmodel.DailyWorkout
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Программы тренировок, назначенные пользователю администратором.
 *
 * Хранится на сервере в поле users.daily_plan: админ записывает его через
 * PUT /admin/users/{id}/update {"daily_plan": "<json>"}, пользователь получает
 * через GET /profile. Пустая строка в daily_plan означает «назначений нет» —
 * тогда используется автоматический план.
 */
@Keep
data class AssignedPrograms(
    @SerializedName("assigned_by_admin") val assignedByAdmin: Boolean = true,
    @SerializedName("assigned_at") val assignedAt: Long = System.currentTimeMillis(),
    @SerializedName("programs") val programs: List<DailyWorkout> = emptyList()
) {
    /** Программа на указанный день: назначенные программы чередуются по дням. */
    fun programForDay(epochDay: Long): DailyWorkout? =
        programs.takeIf { it.isNotEmpty() }?.let { it[Math.floorMod(epochDay, it.size.toLong()).toInt()] }

    companion object {
        private const val PREFS = "assigned_programs_prefs"
        private val gson = Gson()

        /** Разбирает daily_plan с сервера; null, если назначений нет или формат чужой. */
        fun parse(json: String?): AssignedPrograms? {
            if (json.isNullOrBlank() || !json.trimStart().startsWith("{")) return null
            return try {
                gson.fromJson(json, AssignedPrograms::class.java)
                    ?.takeIf { it.assignedByAdmin && !it.programs.isNullOrEmpty() }
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Готовит программы к отправке на другое устройство: фото, выбранные админом из галереи
         * (content://, file://), на телефоне пользователя недоступны, поэтому оставляем только http(s).
         */
        fun forUpload(programs: List<DailyWorkout>): AssignedPrograms {
            fun remote(url: String?) = url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            return AssignedPrograms(
                programs = programs.map { workout ->
                    workout.copy(
                        coverUrl = remote(workout.coverUrl),
                        exercises = workout.exercises.map { ex ->
                            ex.copy(iconUrl = remote(ex.iconUrl) ?: "", tutorialImageUrl = remote(ex.tutorialImageUrl))
                        }
                    )
                }
            )
        }

        fun toJson(value: AssignedPrograms): String = gson.toJson(value)

        fun save(context: Context, uid: String, value: AssignedPrograms?) {
            val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            if (value == null) editor.remove(uid) else editor.putString(uid, toJson(value))
            editor.apply()
        }

        fun load(context: Context, uid: String): AssignedPrograms? =
            parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(uid, null))
    }
}
