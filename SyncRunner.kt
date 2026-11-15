package com.syam.desitv.sync

import android.content.Context
import android.util.Log
import com.syam.desitv.data.Channel
import com.syam.desitv.data.ChannelRepository
import com.syam.desitv.tv.TvChannelSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object SyncRunner {
    private const val TAG = "SyncRunner"
    private val mutex = Mutex()

    /** Downloads the playlist and pushes it into Live Channels. */
    suspend fun syncPlaylists(context: Context): List<Channel> = mutex.withLock {
        val app = context.applicationContext
        val channels = ChannelRepository(app).refresh()
        withContext(Dispatchers.IO) {
            try {
                TvChannelSync.syncChannels(app, channels)
            } catch (e: Exception) {
                // Devices without the TV provider still get the in-app player.
                Log.w(TAG, "Live Channels sync skipped", e)
            }
        }
        channels
    }

    suspend fun syncLogos(context: Context, budgetMs: Long) {
        try {
            TvChannelSync.syncLogos(context.applicationContext, budgetMs)
        } catch (e: Exception) {
            Log.w(TAG, "Logo sync skipped", e)
        }
    }
}
