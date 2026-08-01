package win.swarsel.shopservation

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class Store(context: Context) {
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

    fun setLastAlarm(items: List<Listing>) {
        val arr = JSONArray()
        items.take(20).forEach { l ->
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
        prefs.edit().putString("lastAlarm", arr.toString()).apply()
    }

    fun lastAlarm(): List<Listing> {
        val raw = prefs.getString("lastAlarm", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { Listing.fromJson(arr.getJSONObject(i)) }.getOrNull()
        }
    }
}
