package com.syam.desitv.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Runs once a day: refresh playlist, update Live Channels, then logos. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        SyncRunner.syncPlaylists(applicationContext)
        SyncRunner.syncLogos(applicationContext, 6 * 60_000L)
        Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

/** One-off logo download after a manual or first-time sync. */
class LogoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        SyncRunner.syncLogos(applicationContext, 8 * 60_000L)
        return Result.success()
    }
}
