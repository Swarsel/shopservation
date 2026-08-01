package win.swarsel.shopservation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxiesTest {

    private fun listing(
        source: String,
        url: String,
        id: String = "x1",
    ) = Listing(
        source = source, searchId = 1, externalId = id, title = "t",
        price = 0.0, currency = "JPY", url = url,
    )

    @Test
    fun `mercari url matches the known-good example`() {
        val l = listing("mercari", "https://jp.mercari.com/item/m99996350472", "m99996350472")
        assertEquals(
            "https://www.doorzo.com/en/mall/mercari/detail/" +
                "68747470733a2f2f6a702e6d6572636172692e636f6d2f6974656d2f6d3939393936333530343732",
            Proxies.doorzoUrl(l),
        )
    }

    @Test
    fun `hex payload decodes back to the item url`() {
        val url = "https://jp.mercari.com/item/m1"
        val dz = Proxies.doorzoUrl(listing("mercari", url, "m1"))!!
        val hex = dz.substringAfterLast('/')
        val decoded = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(Charsets.UTF_8)
        assertEquals(url, decoded)
    }

    @Test
    fun `each supported source maps to its own mall path`() {
        val expected = mapOf(
            "mercari" to "mercari",
            "surugaya" to "surugaya",
            "paypayfleamarket" to "paypay",
            "rakuma" to "rakuma",
            "snkrdunk" to "snkrdunk",
            "yahooauctions" to "yahoo",
        )
        expected.forEach { (source, mall) ->
            val dz = Proxies.doorzoUrl(listing(source, "https://example.com/item/1"))
            assertTrue("$source should be supported", dz != null)
            assertTrue("$source -> /mall/$mall/", dz!!.contains("/mall/$mall/detail/"))
        }
    }

    @Test
    fun `unsupported sources get no doorzo link`() {
        listOf("ebay", "willhaben", "vinted", "kleinanzeigen", "jmty", "magi").forEach { s ->
            assertFalse(s, Proxies.supports(s))
            assertNull(s, Proxies.doorzoUrl(listing(s, "https://example.com/x")))
        }
    }

    @Test
    fun `paypay uses the native japanese url not the buyee proxy url`() {
        val l = listing(
            "paypayfleamarket",
            "https://buyee.jp/paypayfleamarket/item/z651582616?conversionType=service_page_search",
            "z651582616",
        )
        assertEquals(
            "https://paypayfleamarket.yahoo.co.jp/item/z651582616",
            Proxies.nativeUrl(l),
        )
        assertFalse(Proxies.doorzoUrl(l)!!.contains("buyee"))
    }

    @Test
    fun `yahoo auctions uses the native url not the zenmarket proxy url`() {
        val l = listing("yahooauctions", "https://zenmarket.jp/en/auction.aspx?itemCode=k123", "k123")
        assertEquals(
            "https://page.auctions.yahoo.co.jp/jp/auction/k123",
            Proxies.nativeUrl(l),
        )
        assertFalse(Proxies.doorzoUrl(l)!!.contains("zenmarket"))
    }

    @Test
    fun `tracking query parameters are stripped so the encoded url stays canonical`() {
        val l = listing("mercari", "https://jp.mercari.com/item/m1?utm_source=x#frag", "m1")
        assertEquals("https://jp.mercari.com/item/m1", Proxies.nativeUrl(l))
    }

    @Test
    fun `proxied sources without an external id get no link rather than a broken one`() {
        val l = listing("paypayfleamarket", "https://buyee.jp/paypayfleamarket/item/z1", "")
        assertNull(Proxies.nativeUrl(l))
        assertNull(Proxies.doorzoUrl(l))
    }

    @Test
    fun `blank url yields no link`() {
        assertNull(Proxies.doorzoUrl(listing("mercari", "")))
    }

    @Test
    fun `source matching is case insensitive`() {
        assertTrue(Proxies.supports("Mercari"))
        assertTrue(Proxies.doorzoUrl(listing("MERCARI", "https://jp.mercari.com/item/m1", "m1")) != null)
    }
}
