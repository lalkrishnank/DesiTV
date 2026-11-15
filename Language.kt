package com.syam.desitv.data

/**
 * Languages kept from the iptv-org playlist, in display order.
 * Malayalam is first, so it is the default and gets the lowest channel numbers.
 */
enum class Language(val code: String, val label: String, val firstNumber: Int) {
    MALAYALAM("mal", "Malayalam", 101),
    TAMIL("tam", "Tamil", 1001),
    HINDI("hin", "Hindi", 2001),
    ENGLISH("eng", "English", 3001),
    RADIO("radio", "Radio", 9001);

    /** Radio stations come from the playlist bundled in the app (assets/radio.m3u). */
    val isRadio: Boolean get() = this == RADIO

    /**
     * Same iptv-org playlist from three places. If one address is blocked by the
     * internet provider, the next one is tried.
     */
    val playlistUrls: List<String>
        get() = listOf(
            "${Config.PLAYLIST_BASE}/languages/$code.m3u",
            "https://raw.githubusercontent.com/iptv-org/iptv/gh-pages/languages/$code.m3u",
            "https://cdn.jsdelivr.net/gh/iptv-org/iptv@gh-pages/languages/$code.m3u",
        )

    companion object {
        fun fromCode(code: String): Language? = values().firstOrNull { it.code == code }
    }
}
