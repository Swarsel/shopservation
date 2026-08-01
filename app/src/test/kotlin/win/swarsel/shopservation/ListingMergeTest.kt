package win.swarsel.shopservation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingMergeTest {

    private fun listing(id: String, title: String = "t$id") = Listing(
        source = "mercari", searchId = 1, externalId = id, title = title,
        price = 0.0, currency = "JPY", url = "https://x/$id",
    )

    private fun merge(page1: List<Listing>, existing: List<Listing>, cap: Int): List<Listing> {
        val byKey = LinkedHashMap<String, Listing>()
        page1.forEach { byKey[it.key] = it }
        existing.forEach { byKey.putIfAbsent(it.key, it) }
        val merged = byKey.values.toList()
        return if (cap > 0 && merged.size > cap) merged.subList(0, cap) else merged
    }

    @Test
    fun `new page is prepended and old entries retained`() {
        val got = merge(listOf(listing("c"), listing("b")), listOf(listing("b"), listing("a")), 0)
        assertEquals(listOf("mercari/c", "mercari/b", "mercari/a"), got.map { it.key })
    }

    @Test
    fun `duplicates collapse and the fresh copy wins`() {
        val got = merge(
            listOf(listing("a", title = "updated")),
            listOf(listing("a", title = "stale")),
            0,
        )
        assertEquals(1, got.size)
        assertEquals("updated", got[0].title)
    }

    @Test
    fun `cap keeps the newest entries not the oldest`() {
        val fresh = listOf(listing("d"), listing("c"))
        val old = listOf(listing("b"), listing("a"))
        val got = merge(fresh, old, 3)
        assertEquals(3, got.size)
        assertEquals(listOf("mercari/d", "mercari/c", "mercari/b"), got.map { it.key })
    }

    @Test
    fun `cap of zero means unlimited`() {
        val got = merge(listOf(listing("b")), listOf(listing("a")), 0)
        assertEquals(2, got.size)
    }

    @Test
    fun `empty existing cache just takes the page`() {
        val got = merge(listOf(listing("a"), listing("b")), emptyList(), 0)
        assertEquals(2, got.size)
    }

    @Test
    fun `keys distinguish sources so ids never collide across marketplaces`() {
        val a = listing("1").copy(source = "mercari")
        val b = listing("1").copy(source = "ebay")
        val got = merge(listOf(a), listOf(b), 0)
        assertEquals(2, got.size)
        assertTrue(got.map { it.key }.containsAll(listOf("mercari/1", "ebay/1")))
    }
}
