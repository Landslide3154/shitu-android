package com.landslide.shitu.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.landslide.shitu.ShituApp
import java.util.concurrent.TimeUnit

/**
 * WorkManager 兜底（规格 §6.7）：每 15 分钟直接在 Worker 里搬一轮，
 * 不需要前台服务，用来兜住"服务被厂商清理"的情况。
 */
class FallbackWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ShituApp ?: return Result.success()
        return runCatching {
            if (app.bridge.state() != com.landslide.shitu.shizuku.ShizukuState.READY) {
                app.bridge.bindWithRetry(1)
            }
            app.runDueRules(force = false)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val NAME = "shitu_fallback"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FallbackWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            runCatching {
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
}
