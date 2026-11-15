package com.syam.desitv.data

/**
 * Languages kept from the iptv-org playlist, in display order.
 * Malayalam is first, so it is the default and gets the lowest channel numbers.
 */
enum class Language(val code: String, val label: String, val firstNumber: Int) {
    MALAYALAM("mal", "Malayalam", 101),
    TAMIL("tam", "Tamil", 1001),
    HINDI("hin", "Hindi", 2001),
    ENGLISH("eng", "English", 3001);

    /** iptv-org publishes the same index.m3u channels split by broadcast language. */
    val playlistUrl: String get() = "${Config.PLAYLIST_BASE}/languages/$code.m3u"

    companion object {
        fun fromCode(code: String): Language? = values().firstOrNull { it.code == code }
    }
}
