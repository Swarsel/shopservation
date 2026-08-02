package win.swarsel.shopservation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorModelTest {
    @Test
    fun `monitor json carries image and doorzo url`() {
        val o = JSONObject(
            """{"id":7,"source":"yahooauctions","title":"card","url":"https://auctions.yahoo.co.jp/jp/auction/k1",
                "imageUrl":"https://auctions.c.yimg.jp/x.jpg",
                "doorzoUrl":"https://www.doorzo.com/en/mall/yahoo/detail/abcd",
                "price":"JPY 1200","status":"active","saleType":"auction",
                "ends":"2026-08-03T22:05:18+09:00","archived":false}"""
        )
        val m = Monitor.fromJson(o)
        assertEquals("https://auctions.c.yimg.jp/x.jpg", m.imageUrl)
        assertEquals("https://www.doorzo.com/en/mall/yahoo/detail/abcd", m.doorzoUrl)
        assertEquals("active", m.status)
        assertTrue(m.endsAtMillis() != null)
    }

    @Test
    fun `a monitor without the new fields still parses`() {
        val o = JSONObject(
            """{"id":1,"source":"mercari","title":"t","url":"https://jp.mercari.com/item/m1",
                "price":"","status":"active","saleType":"","ends":"","archived":false}"""
        )
        val m = Monitor.fromJson(o)
        assertEquals("", m.imageUrl)
        assertEquals("", m.doorzoUrl)
        assertEquals(null, m.endsAtMillis())
    }

    @Test
    fun `archived flag round trips`() {
        val o = JSONObject(
            """{"id":2,"source":"ebay","title":"t","url":"u","price":"","status":"sold",
                "saleType":"","ends":"","archived":true}"""
        )
        assertTrue(Monitor.fromJson(o).archived)
    }
}
