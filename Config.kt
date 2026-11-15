package com.syam.desitv.data

object Config {
    /** Source: https://iptv-org.github.io/iptv/index.m3u (fetched via its per-language playlists). */
    const val PLAYLIST_BASE = "https://iptv-org.github.io/iptv"

    /**
     * English has 2,000+ channels worldwide. Set to true to keep only Indian English
     * channels (tvg-id ending in ".in"), e.g. NDTV 24x7, Times Now, India Today.
     */
    const val ENGLISH_INDIA_ONLY = false

    /** While radio plays, start the TV's ambient mode / screensaver after this much idle time. */
    const val RADIO_AMBIENT_DELAY_MS = 30_000L

    /** Hours between automatic playlist syncs. */
    const val SYNC_INTERVAL_HOURS = 24L

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    fun accept(channel: Channel): Boolean {
        if (channel.url.isBlank()) return false
        if (channel.language == Language.ENGLISH && ENGLISH_INDIA_ONLY) {
            return channel.tvgId.substringBefore('@').lowercase().endsWith(".in")
        }
        return true
    }
}
