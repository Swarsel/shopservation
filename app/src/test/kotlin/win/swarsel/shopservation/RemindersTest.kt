package win.swarsel.shopservation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {

    private val now = 1_700_000_000_000L

    private fun monitor(
        id: Long,
        minutesLeft: Long?,
        status: String = "active",
        archived: Boolean = false,
    ) = Monitor(
        id = id,
        source = "mercari",
        title = "item $id",
        url = "https://x/$id",
        price = "JPY 1000",
        status = status,
        saleType = "auction",
        ends = if (minutesLeft == null) "" else
            java.time.Instant.ofEpochMilli(now + minutesLeft * 60_000).toString(),
        archived = archived,
    )

    @Test
    fun `fires when inside the lead window`() {
        val due = Reminders.due(listOf(monitor(1, 8)), listOf(10), now, emptySet())
        assertEquals(1, due.size)
        assertEquals(10, due[0].leadMinutes)
    }

    @Test
    fun `does not fire before the window`() {
        val due = Reminders.due(listOf(monitor(1, 45)), listOf(10), now, emptySet())
        assertTrue(due.isEmpty())
    }

    @Test
    fun `does not fire for auctions that already ended`() {
        assertTrue(Reminders.due(listOf(monitor(1, -5)), listOf(10), now, emptySet()).isEmpty())
        assertTrue(Reminders.due(listOf(monitor(1, 0)), listOf(10), now, emptySet()).isEmpty())
    }

    @Test
    fun `ignores monitors with no end time`() {
        assertTrue(Reminders.due(listOf(monitor(1, null)), listOf(10), now, emptySet()).isEmpty())
    }

    @Test
    fun `ignores archived and non-active monitors`() {
        assertTrue(Reminders.due(listOf(monitor(1, 5, archived = true)), listOf(10), now, emptySet()).isEmpty())
        assertTrue(Reminders.due(listOf(monitor(2, 5, status = "sold")), listOf(10), now, emptySet()).isEmpty())
    }

    @Test
    fun `each lead time fires at most once per auction`() {
        val m = listOf(monitor(1, 8))
        val first = Reminders.due(m, listOf(60, 10), now, emptySet())
        assertEquals(1, first.size)
        assertEquals(10, first[0].leadMinutes)

        val fired = setOf(first[0].key)
        assertTrue(Reminders.due(m, listOf(60, 10), now, fired).isEmpty())
    }

    @Test
    fun `the tightest matching lead wins so one alarm fires not several`() {
        val due = Reminders.due(listOf(monitor(1, 8)), listOf(60, 10, 2), now, emptySet())
        assertEquals(1, due.size)
        assertEquals(10, due[0].leadMinutes)
    }

    @Test
    fun `a later window still fires after an earlier one was acknowledged`() {
        val m = listOf(monitor(1, 90))
        val atNinety = Reminders.due(m, listOf(120, 10), now, emptySet())
        assertEquals(120, atNinety[0].leadMinutes)

        val fired = setOf(atNinety[0].key)
        val closer = Reminders.due(listOf(monitor(1, 5)), listOf(120, 10), now, fired)
        assertEquals(1, closer.size)
        assertEquals(10, closer[0].leadMinutes)
    }

    @Test
    fun `no leads configured means no reminders`() {
        assertTrue(Reminders.due(listOf(monitor(1, 1)), emptyList(), now, emptySet()).isEmpty())
    }

    @Test
    fun `keys are unique per auction and lead`() {
        val a = Reminders.Due(monitor(1, 5), 10)
        val b = Reminders.Due(monitor(1, 5), 60)
        val c = Reminders.Due(monitor(2, 5), 10)
        assertTrue(a.key != b.key)
        assertTrue(a.key != c.key)
    }

    @Test
    fun `label reads naturally for minutes and hours`() {
        assertEquals("Ends in under 10m", Reminders.label(Reminders.Due(monitor(1, 5), 10)))
        assertEquals("Ends in under 1h", Reminders.label(Reminders.Due(monitor(1, 5), 60)))
        assertEquals("Ends in under 2h", Reminders.label(Reminders.Due(monitor(1, 5), 120)))
        assertEquals("Ends in under 90m", Reminders.label(Reminders.Due(monitor(1, 5), 90)))
    }
}
