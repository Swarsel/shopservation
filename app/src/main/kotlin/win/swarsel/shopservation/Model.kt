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
    val saleType: String,
) {
    val key: String get() = "$source/$externalId"

    companion object {
        fun fromJson(o: JSONObject) = Listing(
            source = o.optString("source"),
            searchId = o.optLong("searchId"),
            externalId = o.optString("externalId"),
            title = o.optString("title"),
            price = o.optDouble("priceValue", 0.0),
            currency = o.optString("currency"),
            url = o.optString("url"),
            saleType = o.optString("saleType"),
        )
    }
}

data class Rule(
    val id: Long,
    val enabled: Boolean = true,
    val keywords: String = "",
    val excludeKeywords: String = "",
    val maxPrice: Double? = null,
    val currency: String = "",
    val sources: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("enabled", enabled)
        put("keywords", keywords)
        put("excludeKeywords", excludeKeywords)
        if (maxPrice != null) put("maxPrice", maxPrice) else put("maxPrice", JSONObject.NULL)
        put("currency", currency)
        put("sources", sources)
    }

    fun describe(): String {
        val bits = mutableListOf<String>()
        bits += if (keywords.isBlank()) "any title" else "“$keywords”"
        if (excludeKeywords.isNotBlank()) bits += "not “$excludeKeywords”"
        if (maxPrice != null) bits += "≤ ${fmtPrice(maxPrice)}${if (currency.isNotBlank()) " $currency" else ""}"
        if (sources.isNotBlank()) bits += "on $sources"
        return bits.joinToString(" · ")
    }

    companion object {
        fun fromJson(o: JSONObject) = Rule(
            id = o.optLong("id"),
            enabled = o.optBoolean("enabled", true),
            keywords = o.optString("keywords"),
            excludeKeywords = o.optString("excludeKeywords"),
            maxPrice = if (o.isNull("maxPrice")) null else o.optDouble("maxPrice"),
            currency = o.optString("currency"),
            sources = o.optString("sources"),
        )

        fun fmtPrice(v: Double): String =
            if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
    }
}
