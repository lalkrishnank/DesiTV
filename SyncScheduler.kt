package com.syam.desitv.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.syam.desitv.data.Config
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Daily sync; survives reboots (WorkManager persists it). */
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(Config.SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("daily-playlist-sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun enqueueLogos(context: Context) {
        val request = OneTimeWorkRequestBuilder<LogoWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork("logo-sync", ExistingWorkPolicy.REPLACE, request)
    }
}
