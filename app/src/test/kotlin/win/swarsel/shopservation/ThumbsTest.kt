package win.swarsel.shopservation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbsTest {
    @Test
    fun `yahoo cdn hosts are proxied`() {
        assertTrue(Thumbs.needsProxy("https://auc-pctr.c.yimg.jp/i/x.jpg"))
        assertTrue(Thumbs.needsProxy("https://yimg.jp/x.jpg"))
        assertTrue(Thumbs.needsProxy("https://item-shopping.c.yimg.jp/i/x.jpg"))
        assertTrue(Thumbs.needsProxy("https://auctions.yahoo.co.jp/img/x.jpg"))
    }

    @Test
    fun `other hosts are fetched directly`() {
        assertFalse(Thumbs.needsProxy("https://static.mercdn.net/item/x.jpg"))
        assertFalse(Thumbs.needsProxy("https://img.fril.jp/x.jpg"))
        assertFalse(Thumbs.needsProxy("https://i.ebayimg.com/x.jpg"))
        assertFalse(Thumbs.needsProxy(""))
        assertFalse(Thumbs.needsProxy("not a url"))
    }

    @Test
    fun `a lookalike host must not be proxied`() {
        assertFalse(Thumbs.needsProxy("https://notyimg.jp.evil.com/x.jpg"))
        assertFalse(Thumbs.needsProxy("https://yahoo.co.jp.attacker.net/x.jpg"))
    }

    @Test
    fun `proxied url targets the api endpoint and encodes the target`() {
        val got = Thumbs.proxiedUrl("https://shop.example.com", "https://auc-pctr.c.yimg.jp/i/a b.jpg?w=1&h=2")
        assertEquals(
            "https://shop.example.com/api/v1/img?u=" +
                "https%3A%2F%2Fauc-pctr.c.yimg.jp%2Fi%2Fa+b.jpg%3Fw%3D1%26h%3D2",
            got,
        )
    }

    @Test
    fun `proxied url tolerates a trailing slash and a bare host`() {
        assertEquals(
            "https://shop.example.com/api/v1/img?u=https%3A%2F%2Fyimg.jp%2Fa.jpg",
            Thumbs.proxiedUrl("https://shop.example.com/", "https://yimg.jp/a.jpg"),
        )
        assertEquals(
            "https://shop.example.com/api/v1/img?u=https%3A%2F%2Fyimg.jp%2Fa.jpg",
            Thumbs.proxiedUrl("shop.example.com", "https://yimg.jp/a.jpg"),
        )
    }

    @Test
    fun `an http server url is kept as http`() {
        val got = Thumbs.proxiedUrl("http://192.168.1.5:8480", "https://yimg.jp/a.jpg")
        assertTrue(got!!.startsWith("http://192.168.1.5:8480/api/v1/img?u="))
    }

    @Test
    fun `no server url means no proxying is possible`() {
        assertNull(Thumbs.proxiedUrl("", "https://yimg.jp/a.jpg"))
        assertNull(Thumbs.proxiedUrl("   ", "https://yimg.jp/a.jpg"))
    }
}
