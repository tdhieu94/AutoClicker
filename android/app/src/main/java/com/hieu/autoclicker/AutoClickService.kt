package com.hieu.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

/**
 * Dịch vụ Trợ năng: hiển thị bảng điều khiển nổi + các điểm chạm,
 * và dùng dispatchGesture() để chạm vào màn hình (hoạt động trên mọi ứng dụng).
 * Cửa sổ dùng TYPE_ACCESSIBILITY_OVERLAY nên không cần quyền "Hiển thị trên ứng dụng khác".
 */
class AutoClickService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private lateinit var settings: Settings
    private val handler = Handler(Looper.getMainLooper())

    private var panel: LinearLayout? = null
    private var playButton: TextView? = null
    private val targets = mutableListOf<View>()

    private var running = false
    private var stepIndex = 0
    private var cyclesDone = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        settings = Settings(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() = stop()

    override fun onDestroy() {
        stop()
        hideOverlay()
        instance = null
        super.onDestroy()
    }

    // ---------- Overlay ----------

    fun showOverlay() {
        if (panel != null) return
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6212121"))
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val lp = overlayParams(dp(56), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = 0; y = dp(200)
        }
        val handle = panelButton("≡")
        p.addView(handle)
        playButton = panelButton("▶") { if (running) stop() else start() }
        p.addView(playButton)
        p.addView(panelButton("＋") { addTarget() })
        p.addView(panelButton("－") { removeLastTarget() })
        p.addView(panelButton("✕") { hideOverlay() })
        makeDraggable(handle, p, lp)
        wm.addView(p, lp)
        panel = p
        if (targets.isEmpty()) addTarget()
    }

    fun hideOverlay() {
        stop()
        targets.forEach { runCatching { wm.removeView(it) } }
        targets.clear()
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        playButton = null
    }

    private fun addTarget() {
        if (running) return
        val size = dp(TARGET_DP)
        val number = targets.size + 1
        val v = TextView(this).apply {
            text = number.toString()
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#801E88E5"))
                setStroke(dp(2), Color.WHITE)
            }
        }
        val dm = resources.displayMetrics
        val lp = overlayParams(size, size).apply {
            x = dm.widthPixels / 2 - size / 2 + (number - 1) * dp(12)
            y = dm.heightPixels / 2 - size / 2 + (number - 1) * dp(12)
        }
        makeDraggable(v, v, lp)
        wm.addView(v, lp)
        targets.add(v)
    }

    private fun removeLastTarget() {
        if (running || targets.isEmpty()) return
        runCatching { wm.removeView(targets.removeAt(targets.lastIndex)) }
    }

    /** Khi đang chạy, các điểm phải "trong suốt" với chạm để cử chỉ rơi xuống ứng dụng bên dưới. */
    private fun setTargetsTouchable(touchable: Boolean) {
        targets.forEach { v ->
            val lp = v.layoutParams as WindowManager.LayoutParams
            lp.flags = if (touchable) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            v.alpha = if (touchable) 1f else 0.5f
            wm.updateViewLayout(v, lp)
        }
    }

    // ---------- Click loop ----------

    private fun start() {
        if (targets.isEmpty()) {
            toast("Hãy thêm ít nhất một điểm (＋)")
            return
        }
        running = true
        stepIndex = 0
        cyclesDone = 0
        playButton?.text = "■"
        setTargetsTouchable(false)
        // Chờ một nhịp để hệ thống áp dụng FLAG_NOT_TOUCHABLE trước khi chạm.
        handler.postDelayed(::tick, 100)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        playButton?.text = "▶"
        setTargetsTouchable(true)
    }

    private fun tick() {
        if (!running) return
        val target = targets.getOrNull(stepIndex) ?: return stop()
        val loc = IntArray(2)
        target.getLocationOnScreen(loc)
        val j = settings.jitterPx
        val x = loc[0] + target.width / 2f + if (j > 0) Random.nextInt(-j, j + 1) else 0
        val y = loc[1] + target.height / 2f + if (j > 0) Random.nextInt(-j, j + 1) else 0

        val path = Path().apply { moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f)) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, settings.pressMs))
            .build()

        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) = scheduleNext()
            override fun onCancelled(g: GestureDescription?) = scheduleNext()
        }, null)
        if (!ok) {
            toast("Không thể gửi thao tác chạm")
            stop()
        }
    }

    private fun scheduleNext() {
        if (!running) return
        stepIndex++
        if (stepIndex >= targets.size) {
            stepIndex = 0
            cyclesDone++
            val limit = settings.repeatCount
            if (limit > 0 && cyclesDone >= limit) {
                stop()
                toast("Đã hoàn thành $limit vòng")
                return
            }
        }
        handler.postDelayed(::tick, settings.intervalMs)
    }

    // ---------- Helpers ----------

    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun panelButton(label: String, onClick: (() -> Unit)? = null) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 20f
        setTextColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        onClick?.let { cb -> setOnClickListener { cb() } }
    }

    /** Kéo [handle] để di chuyển cửa sổ [window]; chạm nhẹ vẫn được tính là click. */
    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(handle: View, window: View, lp: WindowManager.LayoutParams) {
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y; touchX = e.rawX; touchY = e.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - touchX).toInt()
                    lp.y = startY + (e.rawY - touchY).toInt()
                    wm.updateViewLayout(window, lp); true
                }
                else -> false
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TARGET_DP = 48
        @Volatile var instance: AutoClickService? = null
            private set
    }
}
