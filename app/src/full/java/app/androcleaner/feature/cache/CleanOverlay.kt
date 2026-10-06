package app.androcleaner.feature.cache

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import app.androcleaner.R

/** Full-screen progress card shown over Settings while the cache is being cleared. */
internal class CleanOverlay(
    private val context: Context,
    service: AccessibilityService,
    onStop: () -> Unit,
) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val title = text(22f, bold = true)
    private val appName = text(16f).apply { alpha = 0.8f }
    private val counter = text(14f).apply { alpha = 0.6f }
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        isIndeterminate = false
        progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00C9B7"))
    }

    private val root = FrameLayout(context).apply {
        setBackgroundColor(Color.parseColor("#F20E0B1A"))
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val pad = dp(28)
                setPadding(pad, pad, pad, pad)
                background = GradientDrawable().apply {
                    cornerRadius = dp(28).toFloat()
                    setColor(Color.parseColor("#17132A"))
                }
                addView(sparkle())
                addView(title.apply { setText(R.string.cache_overlay_title) }, lp(top = 16))
                addView(appName, lp(top = 8))
                addView(progress, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(20) })
                addView(counter, lp(top = 8))
                addView(
                    TextView(context).apply {
                        setText(R.string.cache_overlay_stop)
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        gravity = Gravity.CENTER
                        setPadding(dp(28), dp(12), dp(28), dp(12))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(24).toFloat()
                            setStroke(dp(1), Color.parseColor("#66FFFFFF"))
                        }
                        isClickable = true
                        setOnClickListener { onStop() }
                    },
                    lp(top = 24),
                )
            },
            FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER).apply {
                leftMargin = dp(24)
                rightMargin = dp(24)
            },
        )
    }

    fun show() {
        val params = WindowManager.LayoutParams(
            MATCH_PARENT,
            MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { windowManager.addView(root, params) }
    }

    fun update(label: String, index: Int, total: Int) {
        appName.text = label
        counter.text = context.getString(R.string.cache_overlay_counter, index, total)
        progress.max = total
        progress.progress = index
    }

    fun hide() {
        runCatching { windowManager.removeView(root) }
    }

    private fun sparkle() = TextView(context).apply {
        text = "✦"
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        gravity = Gravity.CENTER
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#7C5CFF"), Color.parseColor("#00C9B7")),
        ).apply { shape = GradientDrawable.OVAL }
        layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
    }

    private fun text(sizeSp: Float, bold: Boolean = false) = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
