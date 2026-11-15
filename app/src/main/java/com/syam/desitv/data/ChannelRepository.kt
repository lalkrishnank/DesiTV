package com.syam.desitv.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Downloads, filters and caches the channel list. */
class ChannelRepository(context: Context) {
    private val ctx = context.applicationContext
    private val file = File(ctx.filesDir, "channels.json")
    private val prefs = ctx.getSharedPreferences("sync", Context.MODE_PRIVATE)

    fun load(): List<Channel> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { Channel.fromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Cache unreadable", e)
        emptyList()
    }

    val lastSyncMillis: Long get() = prefs.getLong(KEY_LAST_SYNC, 0L)

    fun isStale(): Boolean =
        System.currentTimeMillis() - lastSyncMillis > TimeUnit.HOURS.toMillis(Config.SYNC_INTERVAL_HOURS)

    /**
     * Fetches the Malayalam, Tamil, Hindi and English playlists. If one language fails to
     * download, the previously cached channels for that language are kept.
     */
    suspend fun refresh(): List<Channel> = withContext(Dispatchers.IO) {
        val previous = load()
        val byUrl = LinkedHashMap<String, Channel>()
        var downloaded = 0
        for (lang in Language.values()) {
            val channels = try {
                val text = String(Http.get(lang.playlistUrl), Charsets.UTF_8)
                M3uParser.parse(text, lang).also { downloaded++ }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to download ${lang.label}", e)
                previous.filter { it.language == lang }
            }
            // A stream listed under two languages stays in the first one (Malayalam wins).
            channels.filter(Config::accept).forEach { byUrl.putIfAbsent(it.url, it) }
        }
        if (downloaded == 0) throw IOException("Could not download the playlist. Check the internet connection.")

        val numbered = assignNumbers(byUrl.values.toList())
        save(numbered)
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
        numbered
    }

    private fun assignNumbers(channels: List<Channel>): List<Channel> =
        Language.values().flatMap { lang ->
            channels.filter { it.language == lang }
                .mapIndexed { i, ch -> ch.copy(number = lang.firstNumber + i) }
        }

    private fun save(channels: List<Channel>) {
        val arr = JSONArray()
        channels.forEach { arr.put(it.toJson()) }
        val tmp = File(ctx.filesDir, "channels.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }

    companion object {
        private const val TAG = "ChannelRepository"
        private const val KEY_LAST_SYNC = "last_sync_ms"
    }
}
