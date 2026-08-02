package win.swarsel.shopservation

import android.app.Activity
import android.content.Intent
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

class AlarmActivity : Activity() {

    companion object {
        const val ACTION_REMINDER = "win.swarsel.shopservation.SHOW_REMINDER"
        const val ACTION_HISTORY = "win.swarsel.shopservation.SHOW_HISTORY"
    }

    private var silenced = false
    private var historyMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render(intent)
    }

    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)
        if (newIntent != null) intent = newIntent
        silenced = false
        render(intent)
    }

    private fun render(intent: Intent?) {
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

        historyMode = intent?.action == ACTION_HISTORY
        val reminderMode = intent?.action == ACTION_REMINDER
        val store = Store(this)
        if (historyMode) silenced = true

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(
                Color.parseColor(
                    when {
                        historyMode -> "#1e3a5f"
                        reminderMode -> "#78350f"
                        else -> "#7f1d1d"
                    }
                )
            )
            setPadding(40, 72, 40, 40)
        }

        root.addView(TextView(this).apply {
            text = when {
                historyMode -> "🕘 recent matches"
                reminderMode -> "⏳ auction ending soon"
                else -> "🔔 shopservatory match"
            }
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val items = when {
            historyMode -> store.alarmHistory()
            reminderMode -> store.lastReminder()
            else -> store.lastAlarm()
        }
        if (items.isEmpty()) {
            list.addView(TextView(this).apply {
                text = when {
                    historyMode -> "\nNo alarm matches recorded yet."
                    reminderMode -> "\nA monitored auction is ending soon."
                    else -> "\nA matching listing was found."
                }
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
            text = if (historyMode) "CLOSE" else "SILENCE ALARM"
            textSize = 20f
            setOnClickListener {
                if (!silenced) {
                    silenced = true
                    stopAlarm()
                    text = "CLOSE"
                } else {
                    finish()
                }
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
        Thumbs.load(this, url, into, sample = 2)
    }

    private fun open(url: String) {
        if (!historyMode) {
            silenced = true
            stopAlarm()
        }
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun stopAlarm() {
        AlarmPlayer.stop(this)
        Notifications.clearAlarm(this)
    }

    override fun onDestroy() {
        if (!historyMode) stopAlarm()
        super.onDestroy()
    }
}
