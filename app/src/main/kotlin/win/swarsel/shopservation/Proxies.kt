package win.swarsel.shopservation

object Proxies {

    private val doorzoMalls = mapOf(
        "mercari" to "mercari",
        "surugaya" to "surugaya",
        "paypayfleamarket" to "paypay",
        "rakuma" to "rakuma",
        "snkrdunk" to "snkrdunk",
    )

    private val buyeeItemPaths = mapOf(
        "yahooauctions" to "item/yahoo/auction/",
    )

    fun supports(source: String): Boolean =
        doorzoMalls.containsKey(source.lowercase()) || buyeeItemPaths.containsKey(source.lowercase())

    fun buyeeUrl(listing: Listing): String? {
        if (listing.proxyBuyeeUrl.isNotBlank()) return listing.proxyBuyeeUrl
        val path = buyeeItemPaths[listing.source.lowercase()] ?: return null
        val id = listing.externalId.trim()
        if (id.isEmpty()) return null
        return "https://buyee.jp/$path$id"
    }

    fun doorzoUrl(listing: Listing): String? {
        if (listing.proxyDoorzoUrl.isNotBlank()) return listing.proxyDoorzoUrl
        val mall = doorzoMalls[listing.source.lowercase()] ?: return null
        val native = nativeUrl(listing) ?: return null
        return "https://www.doorzo.com/en/mall/$mall/detail/" + hex(native)
    }

    fun nativeUrl(listing: Listing): String? {
        val url = listing.url.trim()
        if (url.isEmpty()) return null
        val id = listing.externalId.trim()
        return when (listing.source.lowercase()) {
            "paypayfleamarket" ->
                if (id.isEmpty()) null else "https://paypayfleamarket.yahoo.co.jp/item/$id"
            "yahooauctions" ->
                if (id.isEmpty()) null else "https://page.auctions.yahoo.co.jp/jp/auction/$id"
            else -> stripQuery(url)
        }
    }

    private fun stripQuery(url: String): String = url.substringBefore('?').substringBefore('#')

    private fun hex(s: String): String {
        val sb = StringBuilder(s.length * 2)
        for (b in s.toByteArray(Charsets.UTF_8)) {
            sb.append("0123456789abcdef"[(b.toInt() shr 4) and 0xf])
            sb.append("0123456789abcdef"[b.toInt() and 0xf])
        }
        return sb.toString()
    }
}
