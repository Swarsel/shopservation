package win.swarsel.shopservation

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class AlarmActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#7f1d1d"))
            setPadding(40, 72, 40, 40)
        }

        root.addView(TextView(this).apply {
            text = "🔔 shopservatory match"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val items = Store(this).lastAlarm()
        if (items.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "\nA matching listing was found."
                setTextColor(Color.WHITE)
            })
        } else {
            items.forEach { item -> list.addView(itemCard(item)) }
        }
        root.addView(ScrollView(this).apply {
            addView(list)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0,
            ).apply { weight = 1f }
        })

        root.addView(Button(this).apply {
            text = "STOP ALARM"
            textSize = 20f
            setOnClickListener {
                stopAlarm()
                finish()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 28 }
        })

        setContentView(root)
    }

    private fun itemCard(item: Listing): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 8)
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (item.imageUrl.isNotBlank()) {
            val thumb = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(220, 220).apply { rightMargin = 24 }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#00000033"))
            }
            head.addView(thumb)
            loadThumb(item.imageUrl, thumb)
        }
        head.addView(TextView(this).apply {
            text = item.title + (if (item.priceLabel.isNotBlank()) "\n${item.priceLabel}" else "") +
                "\n${item.source}"
            textSize = 15f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { weight = 1f }
        })
        card.addView(head)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        if (item.url.isNotBlank()) {
            buttons.addView(Button(this).apply {
                text = "Open"
                setOnClickListener { open(item.url) }
            })
        }
        Proxies.doorzoUrl(item)?.let { dz ->
            buttons.addView(Button(this).apply {
                text = "Doorzo"
                setOnClickListener { open(dz) }
            })
        }
        if (buttons.childCount > 0) card.addView(buttons)

        return card
    }

    private fun loadThumb(url: String, into: ImageView) {
        thread(isDaemon = true) {
            val bmp = runCatching { fetchBitmap(url) }.getOrNull() ?: return@thread
            runOnUiThread { into.setImageBitmap(bmp) }
        }
    }

    private fun fetchBitmap(url: String): Bitmap? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        return try {
            if (conn.responseCode != 200) return null
            conn.inputStream.use { s ->
                BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inSampleSize = 2 })
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String) {
        stopAlarm()
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun stopAlarm() {
        AlarmPlayer.stop(this)
        Notifications.clearAlarm(this)
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}
