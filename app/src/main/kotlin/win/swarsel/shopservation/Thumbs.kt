package win.swarsel.shopservation

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object Thumbs {

    private val pool = Executors.newFixedThreadPool(4)

    fun load(activity: Activity, url: String, into: ImageView, sample: Int = 4) {
        if (url.isBlank()) return
        runCatching {
            pool.execute {
                val bmp = runCatching { fetch(url, sample) }.getOrNull() ?: return@execute
                if (activity.isFinishing || activity.isDestroyed) return@execute
                activity.runOnUiThread { into.setImageBitmap(bmp) }
            }
        }
    }

    private fun fetch(url: String, sample: Int): Bitmap? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0")
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
