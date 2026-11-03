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
    private var panelParams: WindowManager.LayoutParams? = null
    private var settingsPanel: View? = null
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
        p.addView(panelButton("⚙") { toggleSettings() })
        p.addView(panelButton("✕") { hideOverlay() })
        makeDraggable(handle, p, lp)
        wm.addView(p, lp)
        panel = p
        panelParams = lp
        if (targets.isEmpty()) addTarget()
    }

    fun hideOverlay() {
        stop()
        closeSettings()
        targets.forEach { runCatching { wm.removeView(it) } }
        targets.clear()
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        panelParams = null
        playButton = null
    }

    // ---------- Bảng cài đặt nổi ----------

    private fun toggleSettings() {
        if (settingsPanel != null) closeSettings() else openSettings()
    }

    private fun closeSettings() {
        settingsPanel?.let { runCatching { wm.removeView(it) } }
        settingsPanel = null
    }

    private fun openSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2212121"))
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val lp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)

        val title = TextView(this).apply {
            text = "≡  Cài đặt"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(8))
        }
        box.addView(title)
        makeDraggable(title, box, lp)

        box.addView(stepperRow(
            "Khoảng cách (ms)",
            get = { settings.intervalMs },
            set = { settings.intervalMs = it },
            format = { "$it" },
            step = { v -> when { v < 100 -> 10L; v < 1_000 -> 50L; v < 10_000 -> 500L; else -> 5_000L } },
        ))
        box.addView(stepperRow(
            "Số vòng",
            get = { settings.repeatCount.toLong() },
            set = { settings.repeatCount = it.toInt() },
            format = { if (it == 0L) "∞" else "$it" },
            step = { v -> when { v < 10 -> 1L; v < 100 -> 5L; else -> 50L } },
        ))
        box.addView(stepperRow(
            "Giữ mỗi lần chạm (ms)",
            get = { settings.pressMs },
            set = { settings.pressMs = it },
            format = { "$it" },
            step = { v -> when { v < 100 -> 10L; v < 1_000 -> 50L; else -> 250L } },
        ))
        box.addView(stepperRow(
            "Lệch ngẫu nhiên (px)",
            get = { settings.jitterPx.toLong() },
            set = { settings.jitterPx = it.toInt() },
            format = { if (it == 0L) "Tắt" else "$it" },
            step = { 2L },
        ))
        box.addView(TextView(this).apply {
            text = "Xong"
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.parseColor("#90CAF9"))
            setPadding(0, dp(10), 0, dp(6))
            setOnClickListener { closeSettings() }
        })

        // Đặt bảng cài đặt cạnh bảng điều khiển, lật sang trái nếu sát mép phải.
        val pp = panelParams
        val screenW = resources.displayMetrics.widthPixels
        val panelX = pp?.x ?: 0
        lp.x = if (panelX + dp(56) + dp(280) <= screenW) panelX + dp(60) else (panelX - dp(284)).coerceAtLeast(0)
        lp.y = pp?.y ?: dp(200)
        wm.addView(box, lp)
        settingsPanel = box
    }

    /** Một dòng: nhãn ở trên, [−] giá trị [+] ở dưới. Giữ nút để tăng/giảm liên tục. */
    private fun stepperRow(
        label: String,
        get: () -> Long,
        set: (Long) -> Unit,
        format: (Long) -> String,
        step: (Long) -> Long,
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor("#BDBDBD"))
        })
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val value = TextView(this).apply {
            text = format(get())
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(110), dp(44))
        }
        fun change(sign: Int) {
            val cur = get()
            // Khi giảm, dùng bước của mức thấp hơn để không nhảy quá xa.
            val s = if (sign > 0) step(cur) else step((cur - 1).coerceAtLeast(0))
            set((cur + sign * s).coerceAtLeast(0))
            value.text = format(get())
        }
        line.addView(stepButton("－") { change(-1) })
        line.addView(value)
        line.addView(stepButton("＋") { change(+1) })
        row.addView(line)
        return row
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun stepButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 20f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#424242"))
            cornerRadius = dp(10).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(dp(56), dp(44))
        val repeater = object : Runnable {
            var delay = 400L
            override fun run() {
                action()
                delay = (delay * 0.8).toLong().coerceAtLeast(50L)
                handler.postDelayed(this, delay)
            }
        }
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.alpha = 0.6f
                    action()
                    repeater.delay = 400L
                    handler.postDelayed(repeater, 400L)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    handler.removeCallbacks(repeater)
                }
            }
            true
        }
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
