package win.swarsel.shopservation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderThumbTest {
    private fun monitor(
        source: String = "yahooauctions",
        image: String = "https://auc-pctr.c.yimg.jp/i/x.jpg",
        doorzo: String = "",
        buyee: String = "https://buyee.jp/item/yahoo/auction/k1",
    ) = Monitor(
        id = 7, source = source, title = "ending soon",
        url = "https://auctions.yahoo.co.jp/jp/auction/k1",
        price = "JPY 1200", status = "active", saleType = "auction",
        ends = "2026-08-03T22:05:18+09:00", archived = false,
        imageUrl = image, doorzoUrl = doorzo, buyeeUrl = buyee,
    )

    @Test
    fun `a reminder listing keeps the monitor thumbnail`() {
        val l = Listing(
            source = "yahooauctions", searchId = 0, externalId = "reminder0",
            title = "ending soon", price = 0.0, currency = "",
            url = monitor().url, imageUrl = monitor().imageUrl, saleType = "auction",
        )
        assertEquals("https://auc-pctr.c.yimg.jp/i/x.jpg", l.imageUrl)
    }

    @Test
    fun `a server supplied buyee url wins over local rebuilding`() {
        val l = Listing(
            source = "yahooauctions", searchId = 0, externalId = "reminder0",
            title = "t", price = 0.0, currency = "", url = "https://x/y",
            saleType = "auction",
            proxyBuyeeUrl = "https://buyee.jp/item/yahoo/auction/k1",
        )
        assertEquals("https://buyee.jp/item/yahoo/auction/k1", Proxies.buyeeUrl(l))
    }

    @Test
    fun `a server supplied doorzo url wins over local rebuilding`() {
        val l = Listing(
            source = "paypayfleamarket", searchId = 0, externalId = "reminder0",
            title = "t", price = 0.0, currency = "", url = "https://x/y",
            saleType = "auction",
            proxyDoorzoUrl = "https://www.doorzo.com/en/mall/paypay/detail/abcd",
        )
        assertEquals("https://www.doorzo.com/en/mall/paypay/detail/abcd", Proxies.doorzoUrl(l))
    }

    @Test
    fun `a reminder with no external id still resolves its proxy link`() {
        val l = Listing(
            source = "yahooauctions", searchId = 0, externalId = "reminder0",
            title = "t", price = 0.0, currency = "", url = "https://x/y",
            saleType = "auction",
            proxyBuyeeUrl = "https://buyee.jp/item/yahoo/auction/k1",
        )
        assertEquals("https://buyee.jp/item/yahoo/auction/k1", Proxies.buyeeUrl(l))
    }

    @Test
    fun `without a server url a synthetic reminder id yields no buyee link`() {
        val l = Listing(
            source = "yahooauctions", searchId = 0, externalId = "",
            title = "t", price = 0.0, currency = "", url = "https://x/y",
            saleType = "auction",
        )
        assertNull(Proxies.buyeeUrl(l))
    }

    @Test
    fun `local rebuilding still works when no server url is present`() {
        val l = Listing(
            source = "mercari", searchId = 1, externalId = "m1",
            title = "t", price = 0.0, currency = "", url = "https://jp.mercari.com/item/m1",
        )
        val dz = Proxies.doorzoUrl(l)
        assertEquals(true, dz != null && dz.contains("/mall/mercari/detail/"))
    }
}
