package com.syam.desitv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.syam.desitv.data.Channel
import com.syam.desitv.data.Config

@OptIn(UnstableApi::class)
object PlayerFactory {

    fun create(context: Context): ExoPlayer = ExoPlayer.Builder(context).build()

    fun play(context: Context, player: ExoPlayer, channel: Channel) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(channel.userAgent ?: Config.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        channel.referrer?.let { http.setDefaultRequestProperties(mapOf("Referer" to it)) }

        val item = MediaItem.Builder().setUri(channel.url).apply {
            if (channel.url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()

        val source = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
            .createMediaSource(item)
        player.stop()
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }
}
