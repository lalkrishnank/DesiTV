package com.syam.desitv.tv

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.syam.desitv.R
import com.syam.desitv.data.Language
import com.syam.desitv.sync.SyncRunner
import com.syam.desitv.sync.SyncScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Opened by Live Channels when the user adds "Desi TV" as a channel source. */
class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        val message = findViewById<TextView>(R.id.setupMessage)

        lifecycleScope.launch {
            try {
                val channels = SyncRunner.syncPlaylists(this@SetupActivity)
                SyncScheduler.ensureScheduled(this@SetupActivity)
                SyncScheduler.enqueueLogos(this@SetupActivity)
                val counts = Language.values().joinToString("\n") { lang ->
                    "${lang.label}: ${channels.count { it.language == lang }}"
                }
                message.text = getString(R.string.setup_done, channels.size, counts)
                setResult(Activity.RESULT_OK)
                delay(2500)
            } catch (e: Exception) {
                message.text = getString(R.string.sync_failed, e.message ?: "")
                setResult(Activity.RESULT_CANCELED)
                delay(4000)
            }
            finish()
        }
    }
}
