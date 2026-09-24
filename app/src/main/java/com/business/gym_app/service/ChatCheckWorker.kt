package com.business.gym_app.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.business.gym_app.util.ChatUnreadNotifier
import java.util.concurrent.TimeUnit

/**
 * Резервная проверка новых сообщений раз в ~15 минут (минимум WorkManager).
 * Работает, даже если ChatForegroundService был остановлен системой
 * (лимит dataSync на Android 15+, агрессивное энергосбережение производителя и т.п.).
 */
class ChatCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (ChatUnreadNotifier.sessionToken(applicationContext) == null) return Result.success()
        // Планируем следующую проверку и из воркера: цепочка alarm могла оборваться
        // (процесс убит, разрешение на точные будильники отозвано).
        com.business.gym_app.receiver.ChatAlarmReceiver.schedule(applicationContext)
        return try {
            ChatUnreadNotifier.check(applicationContext)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("ChatCheckWorker", "Unread check failed, will retry: ${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "chat_unread_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChatCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
