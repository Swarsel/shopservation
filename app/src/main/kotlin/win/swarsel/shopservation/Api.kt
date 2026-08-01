package win.swarsel.shopservation

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiError(message: String) : IOException(message)

class Api(private val store: Store) {
    fun fetchListings(): List<Listing> {
        var token = store.token
        if (token.isBlank()) token = login()

        var body = try {
            get("/api/v1/state?page=1", token)
        } catch (e: ApiError) {
            if (e.message?.contains("401") != true) throw e
            token = login()
            get("/api/v1/state?page=1", token)
        }

        val root = JSONObject(body)
        val arr = root.optJSONArray("listings") ?: return emptyList()
        return (0 until arr.length()).map { Listing.fromJson(arr.getJSONObject(it)) }
    }

    fun testConnection(): String {
        val listings = fetchListings()
        return "OK — ${listings.size} listing(s) in the current feed"
    }

    fun login(): String {
        val url = URL(base() + "/login")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            connectTimeout = 15000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        val form = "email=${enc(store.email)}&password=${enc(store.password)}"
        conn.outputStream.use { it.write(form.toByteArray()) }

        val code = conn.responseCode
        val cookie = conn.headerFields["Set-Cookie"]?.firstNotNullOfOrNull { raw ->
            raw.split(';').firstOrNull()?.trim()?.takeIf { it.startsWith("shopservatory_session=") }
        }
        conn.disconnect()

        if (cookie == null) {
            throw ApiError(
                if (code == 401) "login rejected — check email and password"
                else "login failed (HTTP $code) — no session returned"
            )
        }
        val token = cookie.removePrefix("shopservatory_session=")
        store.token = token
        return token
    }

    private fun get(path: String, token: String): String {
        val conn = (URL(base() + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw ApiError("HTTP $code from $path")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun base(): String {
        val u = store.serverUrl.trim().trimEnd('/')
        if (u.isBlank()) throw ApiError("server URL is not set")
        return if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
