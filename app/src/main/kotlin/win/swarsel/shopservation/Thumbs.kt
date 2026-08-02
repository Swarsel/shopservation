package win.swarsel.shopservation

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

object Thumbs {

    private val pool = Executors.newFixedThreadPool(4)

    private val proxiedHosts = listOf("yimg.jp", "yahoo.co.jp")

    fun needsProxy(imageUrl: String): Boolean {
        val host = runCatching { URI(imageUrl).host }.getOrNull()?.lowercase() ?: return false
        return proxiedHosts.any { host == it || host.endsWith(".$it") }
    }

    fun proxiedUrl(serverUrl: String, imageUrl: String): String? {
        val base = serverUrl.trim().trimEnd('/')
        if (base.isBlank()) return null
        val full = if (base.startsWith("http://") || base.startsWith("https://")) base else "https://$base"
        return full + "/api/v1/img?u=" + URLEncoder.encode(imageUrl, "UTF-8")
    }

    fun load(
        activity: Activity,
        url: String,
        into: ImageView,
        sample: Int = 4,
        onDone: (() -> Unit)? = null,
    ) {
        if (url.isBlank()) return
        val store = Store(activity)
        val proxied = if (needsProxy(url)) proxiedUrl(store.serverUrl, url) else null
        val token = if (proxied != null) store.token else ""
        runCatching {
            pool.execute {
                var bmp = runCatching { fetch(proxied ?: url, sample, token) }.getOrNull()
                if (bmp == null && proxied != null) {
                    bmp = runCatching { fetch(url, sample, "") }.getOrNull()
                }
                if (activity.isFinishing || activity.isDestroyed) return@execute
                activity.runOnUiThread {
                    if (bmp != null) into.setImageBitmap(bmp)
                    onDone?.invoke()
                }
            }
        }
    }

    fun showEnlarged(activity: Activity, url: String) {
        if (url.isBlank()) return

        val frame = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#111111"))
        }
        val spinner = ProgressBar(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER }
        }
        val big = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        frame.addView(big)
        frame.addView(spinner)

        val dialog = AlertDialog.Builder(activity).setView(frame).create()
        frame.setOnClickListener { dialog.dismiss() }
        dialog.show()

        load(activity, url, big, sample = 1) { spinner.visibility = ProgressBar.GONE }
    }

    private fun fetch(url: String, sample: Int, token: String): Bitmap? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0")
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            if (conn.responseCode != 200) null
            else conn.inputStream.use { s ->
                BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } finally {
            conn.disconnect()
        }
    }
}
