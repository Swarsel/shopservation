package win.swarsel.shopservation

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

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
            setPadding(48, 96, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(TextView(this).apply {
            text = "🔔 shopservatory match"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val items = Store(this).lastAlarmSummary()
        if (items.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "\nA matching listing was found."
                setTextColor(Color.WHITE)
            })
        } else {
            items.forEach { (title, price, url) ->
                list.addView(TextView(this).apply {
                    text = "\n• $title${if (price.isNotBlank()) "\n   $price" else ""}"
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    if (url.isNotBlank()) {
                        setOnClickListener {
                            stopAlarm()
                            runCatching {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    }
                })
            }
            list.addView(TextView(this).apply {
                text = "\n(tap an item to open it)"
                textSize = 12f
                setTextColor(Color.parseColor("#fecaca"))
            })
        }
        root.addView(ScrollView(this).apply {
            addView(list)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0,
            ).apply { weight = 1f }
        })

        root.addView(Button(this).apply {
            text = "STOP ALARM"
            textSize = 22f
            setOnClickListener {
                stopAlarm()
                finish()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = 32 }
        })

        setContentView(root)
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
