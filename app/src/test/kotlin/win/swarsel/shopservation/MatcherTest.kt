package win.swarsel.shopservation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatcherTest {
    private fun listing(
        title: String,
        price: Double = 0.0,
        currency: String = "",
        source: String = "mercari",
        id: String = "m1",
    ) = Listing(
        source = source, searchId = 1, externalId = id, title = title,
        price = price, currency = currency, url = "https://x/$id", saleType = "",
    )

    @Test
    fun `keywords must all match`() {
        val rule = Rule(id = 1, keywords = "pikachu, psa 10")
        assertTrue(Matcher.matches(rule, listing("Pokemon Pikachu PSA 10 graded")))
        assertFalse(Matcher.matches(rule, listing("Pokemon Pikachu raw card")))
        assertFalse(Matcher.matches(rule, listing("Charizard PSA 10")))
    }

    @Test
    fun `matching is case insensitive`() {
        val rule = Rule(id = 1, keywords = "PIKACHU")
        assertTrue(Matcher.matches(rule, listing("very nice pikachu card")))
    }

    @Test
    fun `blank keywords match any title`() {
        val rule = Rule(id = 1, keywords = "")
        assertTrue(Matcher.matches(rule, listing("anything at all")))
    }

    @Test
    fun `exclude keywords reject`() {
        val rule = Rule(id = 1, keywords = "pikachu", excludeKeywords = "proxy, reprint")
        assertTrue(Matcher.matches(rule, listing("pikachu original")))
        assertFalse(Matcher.matches(rule, listing("pikachu PROXY card")))
        assertFalse(Matcher.matches(rule, listing("pikachu reprint")))
    }

    @Test
    fun `disabled rule never matches`() {
        val rule = Rule(id = 1, enabled = false, keywords = "pikachu")
        assertFalse(Matcher.matches(rule, listing("pikachu")))
    }

    @Test
    fun `max price respects currency and never compares across currencies`() {
        val rule = Rule(id = 1, maxPrice = 50.0, currency = "EUR")
        assertTrue(Matcher.matches(rule, listing("card", price = 30.0, currency = "EUR")))
        assertFalse(Matcher.matches(rule, listing("card", price = 80.0, currency = "EUR")))

        assertFalse(Matcher.matches(rule, listing("card", price = 8100.0, currency = "JPY")))
    }

    @Test
    fun `max price boundary is inclusive`() {
        val rule = Rule(id = 1, maxPrice = 50.0, currency = "EUR")
        assertTrue(Matcher.matches(rule, listing("card", price = 50.0, currency = "EUR")))
    }

    @Test
    fun `max price skips listings with unknown price`() {
        val rule = Rule(id = 1, maxPrice = 50.0, currency = "EUR")

        assertFalse(Matcher.matches(rule, listing("card", price = 0.0, currency = "EUR")))
    }

    @Test
    fun `min price excludes anything cheaper`() {
        val rule = Rule(id = 1, minPrice = 50.0, currency = "EUR")
        assertTrue(Matcher.matches(rule, listing("card", price = 80.0, currency = "EUR")))
        assertFalse(Matcher.matches(rule, listing("card", price = 30.0, currency = "EUR")))
    }

    @Test
    fun `min price boundary is inclusive`() {
        val rule = Rule(id = 1, minPrice = 50.0, currency = "EUR")
        assertTrue(Matcher.matches(rule, listing("card", price = 50.0, currency = "EUR")))
    }

    @Test
    fun `min price respects currency and never compares across currencies`() {
        val rule = Rule(id = 1, minPrice = 50.0, currency = "EUR")
        assertFalse(Matcher.matches(rule, listing("card", price = 8100.0, currency = "JPY")))
    }

    @Test
    fun `min price skips listings with unknown price`() {
        val rule = Rule(id = 1, minPrice = 50.0, currency = "EUR")
        assertFalse(Matcher.matches(rule, listing("card", price = 0.0, currency = "EUR")))
    }

    @Test
    fun `min and max together form an inclusive range`() {
        val rule = Rule(id = 1, minPrice = 50.0, maxPrice = 100.0, currency = "EUR")
        assertTrue(Matcher.matches(rule, listing("card", price = 50.0, currency = "EUR")))
        assertTrue(Matcher.matches(rule, listing("card", price = 75.0, currency = "EUR")))
        assertTrue(Matcher.matches(rule, listing("card", price = 100.0, currency = "EUR")))
        assertFalse(Matcher.matches(rule, listing("card", price = 49.0, currency = "EUR")))
        assertFalse(Matcher.matches(rule, listing("card", price = 101.0, currency = "EUR")))
    }

    @Test
    fun `no min price means cheap listings still match`() {
        val rule = Rule(id = 1, maxPrice = 100.0, currency = "EUR")
        assertTrue(Matcher.matches(rule, listing("card", price = 1.0, currency = "EUR")))
    }

    @Test
    fun `a rule stored before min price existed still loads and matches`() {
        val legacy = org.json.JSONObject(
            """{"id":7,"enabled":true,"keywords":"card","excludeKeywords":"","maxPrice":100.0,"currency":"EUR","sources":""}"""
        )
        val rule = Rule.fromJson(legacy)
        assertEquals(null, rule.minPrice)
        assertTrue(Matcher.matches(rule, listing("card", price = 5.0, currency = "EUR")))
        assertFalse(Matcher.matches(rule, listing("card", price = 500.0, currency = "EUR")))
    }

    @Test
    fun `min price survives a json round trip`() {
        val rule = Rule(id = 3, minPrice = 12.5, maxPrice = 99.0, currency = "EUR")
        val back = Rule.fromJson(org.json.JSONObject(rule.toJson().toString()))
        assertEquals(12.5, back.minPrice)
        assertEquals(99.0, back.maxPrice)
    }

    @Test
    fun `describe reports each price bound shape`() {
        assertTrue(Rule(id = 1, minPrice = 5.0, currency = "EUR").describe().contains("≥ 5 EUR"))
        assertTrue(Rule(id = 1, maxPrice = 5.0, currency = "EUR").describe().contains("≤ 5 EUR"))
        assertTrue(Rule(id = 1, minPrice = 5.0, maxPrice = 9.0, currency = "EUR").describe().contains("5–9 EUR"))
    }

    @Test
    fun `no max price means price is ignored`() {
        val rule = Rule(id = 1, keywords = "card")
        assertTrue(Matcher.matches(rule, listing("card", price = 999999.0, currency = "JPY")))
    }

    @Test
    fun `source filter restricts to listed sources`() {
        val rule = Rule(id = 1, sources = "mercari, ebay")
        assertTrue(Matcher.matches(rule, listing("x", source = "mercari")))
        assertTrue(Matcher.matches(rule, listing("x", source = "ebay")))
        assertFalse(Matcher.matches(rule, listing("x", source = "willhaben")))
    }

    @Test
    fun `selectAlarming skips already seen listings`() {
        val rules = listOf(Rule(id = 1, keywords = "pikachu"))
        val a = listing("pikachu one", id = "a")
        val b = listing("pikachu two", id = "b")
        val hits = Matcher.selectAlarming(rules, listOf(a, b), setOf(a.key))
        assertEquals(listOf(b.key), hits.map { it.key })
    }

    @Test
    fun `selectAlarming with no rules alarms nothing`() {
        val hits = Matcher.selectAlarming(emptyList(), listOf(listing("pikachu")), emptySet())
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `any matching rule is enough`() {
        val rules = listOf(
            Rule(id = 1, keywords = "charizard"),
            Rule(id = 2, keywords = "pikachu"),
        )
        val hits = Matcher.selectAlarming(rules, listOf(listing("shiny pikachu")), emptySet())
        assertEquals(1, hits.size)
    }

    @Test
    fun `listing key is stable across polls and unique per source`() {
        assertEquals("mercari/m1", listing("x", source = "mercari", id = "m1").key)
        assertTrue(
            listing("x", source = "mercari", id = "1").key !=
                listing("x", source = "ebay", id = "1").key
        )
    }
}
