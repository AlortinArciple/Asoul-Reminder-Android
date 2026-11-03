package com.alortinarciple.asoulreminder.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alortinarciple.asoulreminder.AsoulApplication

class ScheduleSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        applicationContext.container().repository.refreshSchedule()
        Result.success()
    }.getOrElse { Result.retry() }
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
