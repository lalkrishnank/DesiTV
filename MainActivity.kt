package com.syam.desitv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.syam.desitv.data.Channel
import com.syam.desitv.data.ChannelRepository
import com.syam.desitv.data.Config
import com.syam.desitv.data.Language
import com.syam.desitv.player.PlayerFactory
import com.syam.desitv.sync.SyncRunner
import com.syam.desitv.sync.SyncScheduler
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var overlay: View
    private lateinit var tabs: LinearLayout
    private lateinit var list: RecyclerView
    private lateinit var status: TextView
    private lateinit var osd: TextView
    private lateinit var syncInfo: TextView
    private lateinit var syncButton: Button
    private lateinit var radioPanel: View
    private lateinit var radioLogo: ImageView
    private lateinit var radioName: TextView

    private lateinit var repo: ChannelRepository
    private var channels: List<Channel> = emptyList()
    private var current = -1
    private var selectedLanguage = Language.MALAYALAM
    private val tabButtons = HashMap<Language, Button>()
    private var syncing = false
    private var wasStopped = false

    private val handler = Handler(Looper.getMainLooper())
    private val hideOsd = Runnable { osd.visibility = View.GONE }
    private val startAmbient = Runnable { launchAmbientMode() }
    private var keepPlayingInAmbient = false
    private val digits = StringBuilder()
    private val jumpToNumber = Runnable {
        val n = digits.toString().toIntOrNull()
        digits.setLength(0)
        val i = channels.indexOfFirst { it.number == n }
        if (i >= 0) playChannel(i) else showOsdText(getString(R.string.no_channel, n ?: 0))
    }

    private val adapter = ChannelAdapter { ch ->
        playChannel(channels.indexOfFirst { it.url == ch.url })
        hideOverlay()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        playerView = findViewById(R.id.playerView)
        overlay = findViewById(R.id.overlay)
        tabs = findViewById(R.id.tabs)
        list = findViewById(R.id.channelList)
        status = findViewById(R.id.status)
        osd = findViewById(R.id.osd)
        syncInfo = findViewById(R.id.syncInfo)
        syncButton = findViewById(R.id.syncButton)
        radioPanel = findViewById(R.id.radioPanel)
        radioLogo = findViewById(R.id.radioLogo)
        radioName = findViewById(R.id.radioName)

        repo = ChannelRepository(this)
        player = PlayerFactory.create(this)
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) status.visibility = View.GONE
            }

            override fun onPlayerError(error: PlaybackException) {
                val name = channels.getOrNull(current)?.name ?: ""
                showStatus(getString(R.string.channel_unavailable, name))
            }
        })

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        buildTabs()
        syncButton.setOnClickListener { syncNow() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (overlay.visibility == View.VISIBLE) {
                    hideOverlay()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        SyncScheduler.ensureScheduled(this)
        channels = repo.load()
        if (channels.isEmpty()) {
            syncNow()
        } else {
            onChannelsLoaded()
            // Also sync once after updating to the version with radio.
            if (repo.isStale() || channels.none { it.language.isRadio }) syncNow()
        }
    }

    // ---------- channels ----------

    private fun onChannelsLoaded() {
        updateTabs()
        updateSyncInfo()
        if (current < 0 && channels.isNotEmpty()) {
            // Default: first Malayalam channel.
            val firstMalayalam = channels.indexOfFirst { it.language == Language.MALAYALAM }
            playChannel(if (firstMalayalam >= 0) firstMalayalam else 0)
        }
        if (overlay.visibility == View.VISIBLE) showLanguage(selectedLanguage)
    }

    private fun syncNow() {
        if (syncing) return
        syncing = true
        syncButton.isEnabled = false
        if (channels.isEmpty()) showStatus(getString(R.string.syncing))
        syncInfo.text = getString(R.string.syncing)
        lifecycleScope.launch {
            try {
                val playingUrl = channels.getOrNull(current)?.url
                val fresh = SyncRunner.syncPlaylists(applicationContext)
                SyncScheduler.enqueueLogos(applicationContext)
                channels = fresh
                current = fresh.indexOfFirst { it.url == playingUrl }
                if (current >= 0 || fresh.isEmpty()) status.visibility = View.GONE
                onChannelsLoaded()
            } catch (e: Exception) {
                if (channels.isEmpty()) showStatus(getString(R.string.sync_failed, e.message ?: ""))
                updateSyncInfo(failed = true)
            } finally {
                syncing = false
                syncButton.isEnabled = true
            }
        }
    }

    private fun playChannel(index: Int) {
        if (channels.isEmpty()) return
        current = index.mod(channels.size)
        val ch = channels[current]
        status.visibility = View.GONE
        PlayerFactory.play(this, player, ch)
        setKeepScreenOn(!ch.language.isRadio)
        scheduleAmbient()
        if (ch.language.isRadio) {
            radioName.text = ch.name
            radioLogo.load(ch.logo.ifBlank { null }) {
                crossfade(true)
                placeholder(R.drawable.ic_radio)
                error(R.drawable.ic_radio)
                fallback(R.drawable.ic_radio)
            }
            radioPanel.visibility = View.VISIBLE
        } else {
            radioPanel.visibility = View.GONE
        }
        showOsdText("${ch.number}  ${ch.name}\n${ch.language.label}" + if (ch.group.isNotBlank()) " · ${ch.group}" else "")
    }

    // ---------- overlay ----------

    private fun buildTabs() {
        tabs.removeAllViews()
        for (lang in Language.values()) {
            val b = Button(this).apply {
                isAllCaps = false
                textSize = 12f
                setBackgroundResource(R.drawable.tab_bg)
                setTextColor(ContextCompat.getColorStateList(this@MainActivity, R.color.tab_text)!!)
                setPadding(8, 8, 8, 8)
                setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showLanguage(lang) }
                setOnClickListener { showLanguage(lang); focusList(0) }
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = 6
            tabs.addView(b, lp)
            tabButtons[lang] = b
        }
        updateTabs()
    }

    private fun updateTabs() {
        for ((lang, b) in tabButtons) {
            val n = channels.count { it.language == lang }
            b.text = "${lang.label}\n$n"
            b.isSelected = lang == selectedLanguage
        }
    }

    private fun showLanguage(lang: Language) {
        selectedLanguage = lang
        tabButtons.forEach { (l, b) -> b.isSelected = l == lang }
        adapter.submit(channels.filter { it.language == lang }, channels.getOrNull(current)?.url)
    }

    private fun showOverlay() {
        overlay.visibility = View.VISIBLE
        val playing = channels.getOrNull(current)
        showLanguage(playing?.language ?: Language.MALAYALAM)
        focusList(adapter.indexOf(playing?.url).coerceAtLeast(0))
    }

    private fun focusList(position: Int) {
        if (adapter.itemCount == 0) {
            syncButton.requestFocus()
            return
        }
        list.scrollToPosition(position)
        list.post {
            list.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus() ?: list.requestFocus()
        }
    }

    private fun hideOverlay() {
        overlay.visibility = View.GONE
        playerView.requestFocus()
    }

    private fun updateSyncInfo(failed: Boolean = false) {
        val last = repo.lastSyncMillis
        val whenText = if (last == 0L) getString(R.string.never) else
            DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        syncInfo.text = getString(if (failed) R.string.sync_info_failed else R.string.sync_info, channels.size, whenText)
    }

    // ---------- on-screen text ----------

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }

    private fun showOsdText(text: String) {
        osd.text = text
        osd.visibility = View.VISIBLE
        handler.removeCallbacks(hideOsd)
        handler.postDelayed(hideOsd, 4000)
    }

    // ---------- ambient mode for radio ----------

    /** TV channels keep the screen on; radio lets the TV go to ambient mode. */
    private fun setKeepScreenOn(on: Boolean) {
        playerView.keepScreenOn = on
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** (Re)starts the idle timer; any remote key press resets it. */
    private fun scheduleAmbient() {
        handler.removeCallbacks(startAmbient)
        if (channels.getOrNull(current)?.language?.isRadio == true) {
            handler.postDelayed(startAmbient, Config.RADIO_AMBIENT_DELAY_MS)
        }
    }

    private fun launchAmbientMode() {
        if (channels.getOrNull(current)?.language?.isRadio != true) return
        keepPlayingInAmbient = true
        try {
            // Starts the TV's screensaver (Ambient mode on Google TV) right away.
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
            )
        } catch (e: Exception) {
            // Not available on this TV: the screen is not kept on during radio,
            // so the TV's own screensaver timer still applies.
            keepPlayingInAmbient = false
        }
    }

    // ---------- remote control ----------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        scheduleAmbient()
        if (event.action == KeyEvent.ACTION_DOWN) {
            val code = event.keyCode
            if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                digits.append(code - KeyEvent.KEYCODE_0)
                showOsdText(digits.toString())
                handler.removeCallbacks(jumpToNumber)
                handler.postDelayed(jumpToNumber, 1500)
                return true
            }
            if (overlay.visibility != View.VISIBLE) {
                when (code) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { playChannel(current + 1); return true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { playChannel(current - 1); return true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_GUIDE -> { showOverlay(); return true }
                    KeyEvent.KEYCODE_INFO -> {
                        channels.getOrNull(current)?.let { showOsdText("${it.number}  ${it.name}") }
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ---------- lifecycle ----------

    override fun onStart() {
        super.onStart()
        keepPlayingInAmbient = false
        scheduleAmbient()
        if (wasStopped && player.isPlaying) {
            wasStopped = false
        } else if (wasStopped) {
            wasStopped = false
            channels.getOrNull(current)?.let { PlayerFactory.play(this, player, it) }
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(startAmbient)
        wasStopped = true
        // Radio keeps playing under the ambient screen; everything else stops.
        if (!(keepPlayingInAmbient && channels.getOrNull(current)?.language?.isRadio == true)) {
            player.stop()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.release()
        super.onDestroy()
    }
}
