package com.syam.desitv.data

import org.json.JSONObject

data class Channel(
    val name: String,
    val url: String,
    val logo: String,
    val tvgId: String,
    val group: String,
    val language: Language,
    val userAgent: String? = null,
    val referrer: String? = null,
    val number: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("url", url)
        put("logo", logo)
        put("tvgId", tvgId)
        put("group", group)
        put("lang", language.code)
        put("ua", userAgent ?: "")
        put("ref", referrer ?: "")
        put("num", number)
    }

    fun toBytes(): ByteArray = toJson().toString().toByteArray(Charsets.UTF_8)

    companion object {
        fun fromJson(o: JSONObject): Channel? {
            val lang = Language.fromCode(o.optString("lang")) ?: return null
            val url = o.optString("url")
            if (url.isBlank()) return null
            return Channel(
                name = o.optString("name"),
                url = url,
                logo = o.optString("logo"),
                tvgId = o.optString("tvgId"),
                group = o.optString("group"),
                language = lang,
                userAgent = o.optString("ua").ifEmpty { null },
                referrer = o.optString("ref").ifEmpty { null },
                number = o.optInt("num"),
            )
        }

        fun fromBytes(bytes: ByteArray): Channel? =
            try { fromJson(JSONObject(String(bytes, Charsets.UTF_8))) } catch (e: Exception) { null }
    }
}
