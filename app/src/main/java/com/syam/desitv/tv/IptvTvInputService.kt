package com.syam.desitv.tv

import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvInputService
import android.net.Uri
import android.view.Surface
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.syam.desitv.data.Channel
import com.syam.desitv.player.PlayerFactory

/**
 * TV input that the Live Channels app (and any TIF-based TV app) uses to play our channels.
 */
class IptvTvInputService : TvInputService() {

    override fun onCreateSession(inputId: String): Session = IptvSession()

    inner class IptvSession : Session(this@IptvTvInputService) {
        private val player = PlayerFactory.create(this@IptvTvInputService)

        init {
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> notifyVideoAvailable()
                        Player.STATE_BUFFERING ->
                            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING)
                        else -> Unit
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN)
                }
            })
        }

        override fun onTune(channelUri: Uri): Boolean {
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING)
            val channel = loadChannel(channelUri) ?: return false
            PlayerFactory.play(this@IptvTvInputService, player, channel)
            return true
        }

        override fun onSetSurface(surface: Surface?): Boolean {
            player.setVideoSurface(surface)
            return true
        }

        override fun onSetStreamVolume(volume: Float) {
            player.volume = volume
        }

        override fun onSetCaptionEnabled(enabled: Boolean) = Unit

        override fun onRelease() {
            player.release()
        }

        private fun loadChannel(uri: Uri): Channel? = try {
            contentResolver.query(
                uri, arrayOf(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getBlob(0)?.let { Channel.fromBytes(it) } else null }
        } catch (e: Exception) {
            null
        }
    }
}
