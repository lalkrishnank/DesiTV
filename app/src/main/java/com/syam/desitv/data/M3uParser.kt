package com.syam.desitv.data

object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")
    private val resolutionTag = Regex("""\s*\(\d{3,4}[pi]\)""")
    private val bracketTag = Regex("""\s*\[[^\]]*]""")

    fun parse(text: String, language: Language): List<Channel> {
        val out = ArrayList<Channel>()
        var name: String? = null
        var logo = ""
        var tvgId = ""
        var group = ""
        var ua: String? = null
        var ref: String? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line)
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = displayName(line)
                    logo = attrs["tvg-logo"].orEmpty()
                    tvgId = attrs["tvg-id"].orEmpty()
                    group = attrs["group-title"].orEmpty().replace(";", ", ")
                    ua = attrs["http-user-agent"]?.ifBlank { null }
                    ref = attrs["http-referrer"]?.ifBlank { null }
                }
                line.startsWith("#EXTVLCOPT:http-user-agent=", ignoreCase = true) ->
                    ua = line.substringAfter('=').ifBlank { null }
                line.startsWith("#EXTVLCOPT:http-referrer=", ignoreCase = true) ->
                    ref = line.substringAfter('=').ifBlank { null }
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    val n = name
                    if (n != null) {
                        out += Channel(n, line, logo, tvgId, group, language, ua, ref)
                    }
                    name = null; logo = ""; tvgId = ""; group = ""; ua = null; ref = null
                }
            }
        }
        return out
    }

    /** Channel name is the text after the comma that follows the last attribute. */
    private fun displayName(extinf: String): String {
        val lastQuote = extinf.lastIndexOf('"')
        val comma = extinf.indexOf(',', if (lastQuote < 0) 0 else lastQuote)
        val rawName = if (comma >= 0) extinf.substring(comma + 1) else extinf
        return rawName.replace(resolutionTag, "").replace(bracketTag, "").trim().ifEmpty { rawName.trim() }
    }
}
