package win.swarsel.shopservation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmHistoryTest {
    private fun l(id: String, title: String = "item $id") = Listing(
        source = "mercari", searchId = 1, externalId = id, title = title,
        price = 100.0, currency = "JPY", url = "https://x/$id",
    )

    @Test
    fun `newest matches are prepended to older ones`() {
        val existing = listOf(l("old1"), l("old2"))
        val merged = Store.mergeHistory(listOf(l("new1")), existing)
        assertEquals(listOf("mercari/new1", "mercari/old1", "mercari/old2"), merged.map { it.key })
    }

    @Test
    fun `an alarm does not overwrite earlier history`() {
        var history = Store.mergeHistory(listOf(l("a")), emptyList())
        history = Store.mergeHistory(listOf(l("b")), history)
        history = Store.mergeHistory(listOf(l("c")), history)
        assertEquals(listOf("mercari/c", "mercari/b", "mercari/a"), history.map { it.key })
    }

    @Test
    fun `re-alarming the same item does not duplicate it`() {
        val existing = listOf(l("dup"), l("other"))
        val merged = Store.mergeHistory(listOf(l("dup")), existing)
        assertEquals(2, merged.size)
        assertEquals(1, merged.count { it.key == "mercari/dup" })
    }

    @Test
    fun `history is capped and drops the oldest entries`() {
        val existing = (1..Store.ALARM_HISTORY_MAX).map { l("e$it") }
        val merged = Store.mergeHistory(listOf(l("fresh")), existing)
        assertEquals(Store.ALARM_HISTORY_MAX, merged.size)
        assertEquals("mercari/fresh", merged.first().key)
        assertTrue(merged.none { it.key == "mercari/e${Store.ALARM_HISTORY_MAX}" })
    }

    @Test
    fun `an empty batch leaves history untouched`() {
        val existing = listOf(l("a"), l("b"))
        assertEquals(existing.map { it.key }, Store.mergeHistory(emptyList(), existing).map { it.key })
    }

    @Test
    fun `a batch of several matches keeps their order`() {
        val merged = Store.mergeHistory(listOf(l("m1"), l("m2"), l("m3")), listOf(l("old")))
        assertEquals(listOf("mercari/m1", "mercari/m2", "mercari/m3", "mercari/old"), merged.map { it.key })
    }
}
