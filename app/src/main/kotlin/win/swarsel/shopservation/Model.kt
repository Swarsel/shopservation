package win.swarsel.shopservation

import org.json.JSONObject

data class Listing(
    val source: String,
    val searchId: Long,
    val externalId: String,
    val title: String,
    val price: Double,
    val currency: String,
    val url: String,
    val imageUrl: String = "",
    val saleType: String = "",
) {
    val key: String get() = "$source/$externalId"

    val priceLabel: String
        get() = if (price > 0) "${Rule.fmtPrice(price)} $currency".trim() else ""

    companion object {
        fun fromJson(o: JSONObject) = Listing(
            source = o.optString("source"),
            searchId = o.optLong("searchId"),
            externalId = o.optString("externalId"),
            title = o.optString("title"),
            price = if (o.has("priceValue")) o.optDouble("priceValue", 0.0) else o.optDouble("price", 0.0),
            currency = o.optString("currency"),
            url = o.optString("url"),
            imageUrl = o.optString("imageUrl"),
            saleType = o.optString("saleType"),
        )
    }
}

data class Monitor(
    val id: Long,
    val source: String,
    val title: String,
    val url: String,
    val price: String,
    val status: String,
    val saleType: String,
    val ends: String,
    val archived: Boolean,
    val imageUrl: String = "",
    val doorzoUrl: String = "",
) {
    fun endsAtMillis(): Long? {
        if (ends.isBlank()) return null
        return runCatching {
            java.time.Instant.parse(ends).toEpochMilli()
        }.getOrNull()
    }

    companion object {
        fun fromJson(o: JSONObject) = Monitor(
            id = o.optLong("id"),
            source = o.optString("source"),
            title = o.optString("title"),
            url = o.optString("url"),
            price = o.optString("price"),
            status = o.optString("status"),
            saleType = o.optString("saleType"),
            ends = o.optString("ends"),
            archived = o.optBoolean("archived"),
            imageUrl = o.optString("imageUrl"),
            doorzoUrl = o.optString("doorzoUrl"),
        )
    }
}

data class Rule(
    val id: Long,
    val enabled: Boolean = true,
    val keywords: String = "",
    val excludeKeywords: String = "",
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val currency: String = "",
    val sources: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("enabled", enabled)
        put("keywords", keywords)
        put("excludeKeywords", excludeKeywords)
        if (minPrice != null) put("minPrice", minPrice) else put("minPrice", JSONObject.NULL)
        if (maxPrice != null) put("maxPrice", maxPrice) else put("maxPrice", JSONObject.NULL)
        put("currency", currency)
        put("sources", sources)
    }

    fun describe(): String {
        val bits = mutableListOf<String>()
        bits += if (keywords.isBlank()) "any title" else "“$keywords”"
        if (excludeKeywords.isNotBlank()) bits += "not “$excludeKeywords”"
        val cur = if (currency.isNotBlank()) " $currency" else ""
        if (minPrice != null && maxPrice != null) {
            bits += "${fmtPrice(minPrice)}–${fmtPrice(maxPrice)}$cur"
        } else if (minPrice != null) {
            bits += "≥ ${fmtPrice(minPrice)}$cur"
        } else if (maxPrice != null) {
            bits += "≤ ${fmtPrice(maxPrice)}$cur"
        }
        if (sources.isNotBlank()) bits += "on $sources"
        return bits.joinToString(" · ")
    }

    companion object {
        fun fromJson(o: JSONObject) = Rule(
            id = o.optLong("id"),
            enabled = o.optBoolean("enabled", true),
            keywords = o.optString("keywords"),
            excludeKeywords = o.optString("excludeKeywords"),
            minPrice = if (o.isNull("minPrice")) null else o.optDouble("minPrice"),
            maxPrice = if (o.isNull("maxPrice")) null else o.optDouble("maxPrice"),
            currency = o.optString("currency"),
            sources = o.optString("sources"),
        )

        fun fmtPrice(v: Double): String =
            if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
    }
}
