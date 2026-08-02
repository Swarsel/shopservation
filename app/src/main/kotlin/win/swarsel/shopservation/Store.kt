package win.swarsel.shopservation

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class Store(context: Context) {
    companion object {
        const val ALARM_HISTORY_MAX = 100

        fun mergeHistory(fresh: List<Listing>, existing: List<Listing>): List<Listing> {
            val seen = existing.mapTo(mutableSetOf()) { it.key }
            val newest = fresh.filterNot { it.key in seen }
            return (newest + existing).take(ALARM_HISTORY_MAX)
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("shopservation", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("serverUrl", "") ?: ""
        set(v) = prefs.edit().putString("serverUrl", v.trim().trimEnd('/')).apply()

    var email: String
        get() = prefs.getString("email", "") ?: ""
        set(v) = prefs.edit().putString("email", v.trim()).apply()

    var password: String
        get() = prefs.getString("password", "") ?: ""
        set(v) = prefs.edit().putString("password", v).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit().putString("token", v).apply()

    var pollSeconds: Int
        get() = prefs.getInt("pollSeconds", 60)
        set(v) = prefs.edit().putInt("pollSeconds", v.coerceAtLeast(15)).apply()

    var watching: Boolean
        get() = prefs.getBoolean("watching", false)
        set(v) = prefs.edit().putBoolean("watching", v).apply()

    var lastStatus: String
        get() = prefs.getString("lastStatus", "never polled") ?: "never polled"
        set(v) = prefs.edit().putString("lastStatus", v).apply()

    var alarmSoundUri: String
        get() = prefs.getString("alarmSoundUri", "") ?: ""
        set(v) = prefs.edit().putString("alarmSoundUri", v).apply()

    var alarmSoundLabel: String
        get() = prefs.getString("alarmSoundLabel", "Built-in siren") ?: "Built-in siren"
        set(v) = prefs.edit().putString("alarmSoundLabel", v).apply()

    var alarmVolumePercent: Int
        get() = prefs.getInt("alarmVolumePercent", 100)
        set(v) = prefs.edit().putInt("alarmVolumePercent", v.coerceIn(10, 100)).apply()

    var alarmVibrate: Boolean
        get() = prefs.getBoolean("alarmVibrate", true)
        set(v) = prefs.edit().putBoolean("alarmVibrate", v).apply()

    var lastSoundError: String
        get() = prefs.getString("lastSoundError", "") ?: ""
        set(v) = prefs.edit().putString("lastSoundError", v).apply()

    var previewLimit: Int
        get() = prefs.getInt("previewLimit", 5000)
        set(v) = prefs.edit().putInt("previewLimit", if (v <= 0) 0 else v.coerceAtLeast(100)).apply()

    var reminderEnabled: Boolean
        get() = prefs.getBoolean("reminderEnabled", false)
        set(v) = prefs.edit().putBoolean("reminderEnabled", v).apply()

    var reminderMinutes: String
        get() = prefs.getString("reminderMinutes", "10") ?: "10"
        set(v) = prefs.edit().putString("reminderMinutes", v).apply()

    var reminderSoundUri: String
        get() = prefs.getString("reminderSoundUri", "") ?: ""
        set(v) = prefs.edit().putString("reminderSoundUri", v).apply()

    var reminderSoundLabel: String
        get() = prefs.getString("reminderSoundLabel", "Built-in siren") ?: "Built-in siren"
        set(v) = prefs.edit().putString("reminderSoundLabel", v).apply()

    var reminderVolumePercent: Int
        get() = prefs.getInt("reminderVolumePercent", 100)
        set(v) = prefs.edit().putInt("reminderVolumePercent", v.coerceIn(10, 100)).apply()

    var reminderVibrate: Boolean
        get() = prefs.getBoolean("reminderVibrate", true)
        set(v) = prefs.edit().putBoolean("reminderVibrate", v).apply()

    fun reminderLeadMinutes(): List<Int> =
        reminderMinutes.split(',', '\n')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()

    fun reminderFired(): Set<String> = prefs.getStringSet("reminderFired", emptySet())?.toSet() ?: emptySet()

    fun markReminderFired(key: String, cap: Int = 500) {
        val cur = reminderFired().toMutableList()
        if (cur.contains(key)) return
        cur.add(key)
        val trimmed = if (cur.size > cap) cur.subList(cur.size - cap, cur.size) else cur
        prefs.edit().putStringSet("reminderFired", trimmed.toSet()).apply()
    }

    fun configured(): Boolean =
        serverUrl.isNotBlank() && (token.isNotBlank() || (email.isNotBlank() && password.isNotBlank()))

    fun rules(): List<Rule> {
        val raw = prefs.getString("rules", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { Rule.fromJson(arr.getJSONObject(i)) }.getOrNull()
        }
    }

    fun saveRules(rules: List<Rule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("rules", arr.toString()).apply()
    }

    fun upsertRule(rule: Rule) {
        val list = rules().toMutableList()
        val idx = list.indexOfFirst { it.id == rule.id }
        if (idx >= 0) list[idx] = rule else list.add(rule)
        saveRules(list)
    }

    fun deleteRule(id: Long) = saveRules(rules().filterNot { it.id == id })

    fun nextRuleId(): Long = (rules().maxOfOrNull { it.id } ?: 0L) + 1L

    fun seen(): Set<String> = prefs.getStringSet("seen", emptySet())?.toSet() ?: emptySet()

    fun markSeen(keys: Collection<String>, cap: Int = 2000) {
        if (keys.isEmpty()) return
        val ordered = seenOrdered().toMutableList()
        keys.forEach { k -> if (!ordered.contains(k)) ordered.add(k) }
        val trimmed = if (ordered.size > cap) ordered.subList(ordered.size - cap, ordered.size) else ordered
        val arr = JSONArray()
        trimmed.forEach { arr.put(it) }
        prefs.edit()
            .putString("seenOrder", arr.toString())
            .putStringSet("seen", trimmed.toSet())
            .apply()
    }

    private fun seenOrdered(): List<String> {
        val raw = prefs.getString("seenOrder", null) ?: return seen().toList()
        val arr = runCatching { JSONArray(raw) }.getOrElse { return seen().toList() }
        return (0 until arr.length()).map { arr.getString(it) }
    }

    var seeded: Boolean
        get() = prefs.getBoolean("seeded", false)
        set(v) = prefs.edit().putBoolean("seeded", v).apply()

    fun setLastReminder(dues: List<Reminders.Due>) {
        val arr = JSONArray()
        dues.take(20).forEach { d ->
            arr.put(JSONObject().apply {
                put("title", d.monitor.title)
                put("source", d.monitor.source)
                put("price", d.monitor.price)
                put("url", d.monitor.url)
                put("ends", d.monitor.ends)
                put("lead", d.leadMinutes)
                put("imageUrl", d.monitor.imageUrl)
                put("doorzoUrl", d.monitor.doorzoUrl)
                put("buyeeUrl", d.monitor.buyeeUrl)
            })
        }
        prefs.edit().putString("lastReminder", arr.toString()).apply()
    }

    fun lastReminder(): List<Listing> {
        val raw = prefs.getString("lastReminder", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Listing(
                source = o.optString("source"),
                searchId = 0,
                externalId = "reminder$i",
                title = o.optString("title"),
                price = 0.0,
                currency = "",
                url = o.optString("url"),
                imageUrl = o.optString("imageUrl"),
                saleType = "auction",
                proxyDoorzoUrl = o.optString("doorzoUrl"),
                proxyBuyeeUrl = o.optString("buyeeUrl"),
            )
        }
    }

    fun lastReminderLabels(): List<String> {
        val raw = prefs.getString("lastReminder", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val price = o.optString("price")
            buildString {
                append(o.optString("title"))
                if (price.isNotBlank()) append("\n   ")
                append(price)
                append("\n   ends in under ")
                append(o.optInt("lead"))
                append(" min")
            }
        }
    }

    fun cacheMonitors(items: List<Monitor>) {
        val arr = JSONArray()
        items.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("source", m.source)
                put("title", m.title)
                put("url", m.url)
                put("price", m.price)
                put("status", m.status)
                put("saleType", m.saleType)
                put("ends", m.ends)
                put("archived", m.archived)
            })
        }
        prefs.edit().putString("monitors", arr.toString()).apply()
    }

    fun cachedMonitors(): List<Monitor> {
        val raw = prefs.getString("monitors", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { Monitor.fromJson(arr.getJSONObject(i)) }.getOrNull()
        }
    }

    fun setLastAlarm(items: List<Listing>) {
        prefs.edit().putString("lastAlarm", encodeListings(items.take(20))).apply()
        appendAlarmHistory(items)
    }

    fun lastAlarm(): List<Listing> = decodeListings(prefs.getString("lastAlarm", "[]"))

    fun alarmHistory(): List<Listing> = decodeListings(prefs.getString("alarmHistory", "[]"))

    fun clearAlarmHistory() {
        prefs.edit().remove("alarmHistory").apply()
    }

    private fun appendAlarmHistory(items: List<Listing>) {
        if (items.isEmpty()) return
        prefs.edit()
            .putString("alarmHistory", encodeListings(mergeHistory(items, alarmHistory())))
            .apply()
    }

    private fun encodeListings(items: List<Listing>): String {
        val arr = JSONArray()
        items.forEach { l ->
            arr.put(JSONObject().apply {
                put("title", l.title)
                put("source", l.source)
                put("searchId", l.searchId)
                put("externalId", l.externalId)
                put("price", l.price)
                put("currency", l.currency)
                put("url", l.url)
                put("imageUrl", l.imageUrl)
                put("saleType", l.saleType)
            })
        }
        return arr.toString()
    }

    private fun decodeListings(raw: String?): List<Listing> {
        val arr = runCatching { JSONArray(raw ?: "[]") }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { Listing.fromJson(arr.getJSONObject(i)) }.getOrNull()
        }
    }
}
