package win.swarsel.shopservation

object Reminders {

    data class Due(val monitor: Monitor, val leadMinutes: Int) {
        val key: String get() = "${monitor.id}/$leadMinutes"
    }

    fun due(
        monitors: List<Monitor>,
        leads: List<Int>,
        now: Long,
        fired: Set<String>,
    ): List<Due> {
        if (leads.isEmpty()) return emptyList()
        val ascending = leads.sorted()
        val out = mutableListOf<Due>()
        for (m in monitors) {
            if (m.archived) continue
            if (m.status != "active") continue
            val ends = m.endsAtMillis() ?: continue
            val minutesLeft = (ends - now).toDouble() / 60000.0
            if (minutesLeft <= 0) continue
            for (lead in ascending) {
                if (minutesLeft > lead) continue
                val d = Due(m, lead)
                if (d.key !in fired) out.add(d)
                break
            }
        }
        return out
    }

    fun label(d: Due): String {
        val mins = d.leadMinutes
        val when_ = if (mins >= 60 && mins % 60 == 0) "${mins / 60}h" else "${mins}m"
        return "Ends in under $when_"
    }
}
