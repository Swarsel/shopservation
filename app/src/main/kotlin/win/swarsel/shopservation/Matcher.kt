package win.swarsel.shopservation

object Matcher {
    fun terms(raw: String): List<String> =
        raw.split(',', '\n')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

    fun matches(rule: Rule, listing: Listing): Boolean {
        if (!rule.enabled) return false

        val title = listing.title.lowercase()
        val required = terms(rule.keywords)
        if (required.isNotEmpty() && !required.all { title.contains(it) }) return false

        val excluded = terms(rule.excludeKeywords)
        if (excluded.any { title.contains(it) }) return false

        val srcs = terms(rule.sources)
        if (srcs.isNotEmpty() && !srcs.contains(listing.source.lowercase())) return false

        val min = rule.minPrice
        val max = rule.maxPrice
        if (min != null || max != null) {
            if (listing.price <= 0.0) return false
            if (rule.currency.isNotBlank() && !rule.currency.equals(listing.currency, ignoreCase = true)) return false
            if (min != null && listing.price < min) return false
            if (max != null && listing.price > max) return false
        }
        return true
    }

    fun firstMatch(rules: List<Rule>, listing: Listing): Rule? = rules.firstOrNull { matches(it, listing) }

    fun isAuction(listing: Listing): Boolean = listing.saleType.equals("auction", ignoreCase = true)

    fun selectAlarming(rules: List<Rule>, listings: List<Listing>, seen: Set<String>): List<Listing> =
        listings.filter { it.key !in seen && !isAuction(it) && firstMatch(rules, it) != null }
}
