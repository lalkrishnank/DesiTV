package com.syam.desitv.tv

import android.content.ComponentName
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.media.tv.TvContract.Channels
import android.media.tv.TvContract.Programs
import android.os.SystemClock
import com.syam.desitv.data.Channel
import com.syam.desitv.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.ByteArrayOutputStream
import java.util.Collections

/** Mirrors the cached channel list into the Android TV channel database (Live Channels). */
object TvChannelSync {
    private const val HOUR = 60L * 60L * 1000L

    fun inputId(ctx: Context): String =
        TvContract.buildInputId(ComponentName(ctx, IptvTvInputService::class.java))

    private fun queryChannels(ctx: Context): List<Pair<Long, Channel?>> {
        val out = ArrayList<Pair<Long, Channel?>>()
        ctx.contentResolver.query(
            TvContract.buildChannelsUriForInput(inputId(ctx)),
            arrayOf(Channels._ID, Channels.COLUMN_INTERNAL_PROVIDER_DATA),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                out += c.getLong(0) to c.getBlob(1)?.let { Channel.fromBytes(it) }
            }
        }
        return out
    }

    /** Updates existing rows in place (keeps Live Channels' favourites/last channel), adds new, removes gone. */
    fun syncChannels(ctx: Context, channels: List<Channel>) {
        val inputId = inputId(ctx)
        val existing = HashMap<String, Long>()
        val stale = ArrayList<Long>()
        for ((id, ch) in queryChannels(ctx)) {
            if (ch == null || existing.containsKey(ch.url)) stale += id else existing[ch.url] = id
        }

        val ops = ArrayList<ContentProviderOperation>()
        for (ch in channels) {
            val values = channelValues(inputId, ch)
            val id = existing.remove(ch.url)
            ops += if (id != null) {
                ContentProviderOperation.newUpdate(TvContract.buildChannelUri(id)).withValues(values).build()
            } else {
                ContentProviderOperation.newInsert(Channels.CONTENT_URI).withValues(values).build()
            }
        }
        (stale + existing.values).forEach {
            ops += ContentProviderOperation.newDelete(TvContract.buildChannelUri(it)).build()
        }
        applyInChunks(ctx.contentResolver, ops)
        refreshPrograms(ctx)
    }

    private fun channelValues(inputId: String, ch: Channel) = ContentValues().apply {
        put(Channels.COLUMN_INPUT_ID, inputId)
        put(Channels.COLUMN_DISPLAY_NUMBER, ch.number.toString())
        put(Channels.COLUMN_DISPLAY_NAME, ch.name)
        put(Channels.COLUMN_DESCRIPTION, listOf(ch.language.label, ch.group).filter { it.isNotBlank() }.joinToString(" · "))
        put(Channels.COLUMN_TYPE, Channels.TYPE_OTHER)
        put(
            Channels.COLUMN_SERVICE_TYPE,
            if (ch.language.isRadio) Channels.SERVICE_TYPE_AUDIO else Channels.SERVICE_TYPE_AUDIO_VIDEO
        )
        put(Channels.COLUMN_SEARCHABLE, 1)
        put(Channels.COLUMN_INTERNAL_PROVIDER_DATA, ch.toBytes())
    }

    /** The playlist has no EPG, so each channel gets one "Live" block covering the next 2 days. */
    private fun refreshPrograms(ctx: Context) {
        val now = System.currentTimeMillis()
        val ops = ArrayList<ContentProviderOperation>()
        for ((id, ch) in queryChannels(ctx)) {
            if (ch == null) continue
            ops += ContentProviderOperation.newDelete(TvContract.buildProgramsUriForChannel(id)).build()
            val values = ContentValues().apply {
                put(Programs.COLUMN_CHANNEL_ID, id)
                put(Programs.COLUMN_TITLE, ch.name)
                put(Programs.COLUMN_SHORT_DESCRIPTION, "Live · ${ch.language.label}" + if (ch.group.isNotBlank()) " · ${ch.group}" else "")
                put(Programs.COLUMN_START_TIME_UTC_MILLIS, now - HOUR)
                put(Programs.COLUMN_END_TIME_UTC_MILLIS, now + 48 * HOUR)
                if (ch.logo.isNotBlank()) put(Programs.COLUMN_POSTER_ART_URI, ch.logo)
            }
            ops += ContentProviderOperation.newInsert(Programs.CONTENT_URI).withValues(values).build()
        }
        applyInChunks(ctx.contentResolver, ops)
    }

    private fun applyInChunks(cr: ContentResolver, ops: List<ContentProviderOperation>) {
        ops.chunked(250).forEach { cr.applyBatch(TvContract.AUTHORITY, ArrayList(it)) }
    }

    /** Downloads channel logos into Live Channels, a few at a time, within a time budget. */
    suspend fun syncLogos(ctx: Context, budgetMs: Long) = coroutineScope {
        val prefs = ctx.getSharedPreferences("logos", Context.MODE_PRIVATE)
        val rows = queryChannels(ctx).mapNotNull { (id, ch) -> ch?.let { id to it } }
        val validKeys = rows.map { (id, ch) -> "$id|${ch.logo}" }.toSet()
        val done: MutableSet<String> = Collections.synchronizedSet(
            HashSet(prefs.getStringSet("done", emptySet()).orEmpty().filter { it in validKeys })
        )
        val todo = rows.filter { (id, ch) -> ch.logo.isNotBlank() && "$id|${ch.logo}" !in done }
        val deadline = SystemClock.elapsedRealtime() + budgetMs
        val permits = Semaphore(6)

        todo.map { (id, ch) ->
            async(Dispatchers.IO) {
                permits.withPermit {
                    if (SystemClock.elapsedRealtime() < deadline) {
                        try {
                            val png = toPng(Http.get(ch.logo, 10_000))
                            if (png != null) {
                                ctx.contentResolver.openOutputStream(TvContract.buildChannelLogoUri(id))
                                    ?.use { it.write(png) }
                                done.add("$id|${ch.logo}")
                            }
                        } catch (_: Exception) {
                            // Missing logos are not important; try again next sync.
                        }
                    }
                }
            }
        }.awaitAll()

        prefs.edit().putStringSet("done", HashSet(done)).apply()
    }

    private fun toPng(bytes: ByteArray): ByteArray? {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scale = minOf(1f, 320f / maxOf(bmp.width, bmp.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true
            )
        } else bmp
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
