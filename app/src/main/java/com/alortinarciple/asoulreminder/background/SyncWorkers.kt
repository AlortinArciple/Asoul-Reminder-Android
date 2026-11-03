package com.alortinarciple.asoulreminder.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alortinarciple.asoulreminder.AsoulApplication

class ScheduleSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    /**
     * `refreshSchedule()` 内部已经收掉异常并记录失败退避，这里不再返回 Result.retry()：
     * WorkManager 的重试是指数退避且不受我们的 30 分钟下限约束，失败时会反复打站点。
     */
    override suspend fun doWork(): Result {
        applicationContext.container().repository.refreshSchedule()
        return Result.success()
    }
}

class ContentSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val repository = applicationContext.container().repository
        repository.refreshDynamics()
        repository.refreshLiveStatuses()
        Result.success()
    }.getOrElse { Result.retry() }
}

class LiveWindowWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val eventId = inputData.getString(LiveMonitorService.EXTRA_EVENT_ID) ?: return Result.failure()
        return runCatching {
            LiveMonitorService.start(applicationContext, eventId)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

internal fun Context.container() = (applicationContext as AsoulApplication).container
