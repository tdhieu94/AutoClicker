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
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

/**
 * Dịch vụ Trợ năng: hiển thị bảng điều khiển nổi + các điểm chạm/vuốt,
 * và dùng dispatchGesture() để thao tác trên màn hình (hoạt động trên mọi ứng dụng).
 * Cửa sổ dùng TYPE_ACCESSIBILITY_OVERLAY nên không cần quyền "Hiển thị trên ứng dụng khác".
 */
class AutoClickService : AccessibilityService() {

    /** Một bước: chạm tại [start], hoặc vuốt từ [start] tới [end]. */
    private class Step(val start: View, val end: View?) {
        val isSwipe get() = end != null
        val views get() = listOfNotNull(start, end)
    }

    private lateinit var wm: WindowManager
    private lateinit var settings: Settings
    private lateinit var profiles: Profiles
    private val handler = Handler(Looper.getMainLooper())

    private var panel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var infoView: TextView? = null
    private var expanded = false              // bảng chờ đang mở rộng các nút phụ (⋯)
    private var popup: View? = null          // bảng ⚙ hoặc 💾 đang mở (chỉ một cái một lúc)
    private val steps = mutableListOf<Step>()

    /** Cập nhật đồng hồ đếm ngược mỗi giây khi có hẹn giờ (kể cả khi khoảng cách chạm dài). */
    private val infoTicker = object : Runnable {
        override fun run() {
            if (state != State.RUNNING) return
            updateInfo()
            handler.postDelayed(this, 1000)
        }
    }

    private enum class State { IDLE, RUNNING, PAUSED }
    private var state = State.IDLE
    private val running get() = state == State.RUNNING
    private var pausedAt = 0L
    private var stepIndex = 0
    private var cyclesDone = 0
    private var actionsDone = 0L
    private var startedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        settings = Settings(this)
        profiles = Profiles(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() = stop()

    override fun onDestroy() {
        stop()
        hideOverlay()
        instance = null
        super.onDestroy()
    }

    // ---------- Bảng điều khiển ----------

    fun showOverlay() {
        if (panel != null) return
        val p = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundedBg("#E6212121", 24)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val lp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
            .apply { x = 0; y = dp(160) }
        wm.addView(p, lp)
        panel = p
        panelParams = lp
        renderPanel()
        if (steps.isEmpty()) addTap()
    }

    fun hideOverlay() {
        stop()
        closePopup()
        clearSteps()
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        panelParams = null
        infoView = null
    }

    /**
     * Dựng lại bảng nổi theo trạng thái:
     *  - Chờ:      cột dọc gọn ≡ ▶ ＋ ↗ ⋯ (bấm ⋯ để hiện thêm － 💾 ⚙ ✕).
     *  - Đang chạy: viên thuốc nhỏ [⏸ | số lần · vòng].
     *  - Tạm dừng:  [▶ ■ | số lần · vòng].
     */
    private fun renderPanel() {
        val p = panel ?: return
        val lp = panelParams ?: return
        p.removeAllViews()
        infoView = null
        when (state) {
            State.IDLE -> {
                p.orientation = LinearLayout.VERTICAL
                val handle = dragStrip()
                p.addView(handle)
                p.addView(panelButton("▶") { start() })
                p.addView(panelButton("＋") { addTap() })
                p.addView(panelButton("↗") { addSwipe() })
                if (expanded) {
                    p.addView(panelButton("－") { removeLastStep() })
                    p.addView(panelButton("💾") { togglePopup("profiles", ::buildProfilesPopup) })
                    p.addView(panelButton("⚙") { togglePopup("settings", ::buildSettingsPopup) })
                    p.addView(panelButton("✕") { hideOverlay() })
                }
                p.addView(panelButton(if (expanded) "▴" else "⋯") {
                    expanded = !expanded
                    if (!expanded) closePopup()
                    renderPanel()
                })
                makeDraggable(handle, p, lp)
            }
            State.RUNNING, State.PAUSED -> {
                p.orientation = LinearLayout.HORIZONTAL
                if (state == State.RUNNING) {
                    p.addView(roundButton("⏸", "#FB8C00") { pause() })
                } else {
                    p.addView(roundButton("▶", "#43A047") { resume() })
                    p.addView(roundButton("■", "#E53935") { stop() })
                }
                val info = TextView(this).apply {
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    setPadding(dp(8), 0, dp(10), 0)
                    minWidth = dp(76)
                }
                p.addView(info, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)).apply { gravity = Gravity.CENTER_VERTICAL })
                info.gravity = Gravity.CENTER_VERTICAL
                infoView = info
                updateInfo()
                // Kéo vùng chữ để di chuyển viên thuốc.
                makeDraggable(info, p, lp)
            }
        }
        runCatching { wm.updateViewLayout(p, lp) }
    }

    /** "128 lần" / "Vòng 3/10 · còn 4:12". */
    private fun updateInfo() {
        val v = infoView ?: return
        val limit = settings.repeatCount
        val round = (cyclesDone + 1).coerceAtMost(if (limit > 0) limit else Int.MAX_VALUE)
        val line2 = buildString {
            append("Vòng $round/${if (limit > 0) limit else "∞"}")
            val stopMin = settings.stopAfterMin
            if (stopMin > 0) {
                val now = if (state == State.PAUSED) pausedAt else SystemClock.elapsedRealtime()
                val left = ((stopMin * 60_000L - (now - startedAt)) / 1000).coerceAtLeast(0)
                append(" · còn %d:%02d".format(left / 60, left % 60))
            }
        }
        v.text = "$actionsDone lần\n$line2"
    }

    /** Thanh kéo: vạch nhỏ nhưng vùng chạm cao 24dp cho dễ kéo. */
    private fun dragStrip() = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(24))
        addView(View(context).apply { background = roundedBg("#9E9E9E", 3) },
            FrameLayout.LayoutParams(dp(26), dp(5), Gravity.CENTER))
    }

    private fun roundButton(label: String, color: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 16f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(color))
        }
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(2), 0, dp(2), 0) }
        setOnClickListener { onClick() }
    }

    // ---------- Các bước ----------

    private fun addTap(x: Int? = null, y: Int? = null) {
        if (running) return
        val n = steps.size + 1
        val (dx, dy) = defaultPos(n)
        steps.add(Step(addMarker("$n", "#801E88E5", x ?: dx, y ?: dy), null))
    }

    private fun addSwipe(x: Int? = null, y: Int? = null, x2: Int? = null, y2: Int? = null) {
        if (running) return
        val n = steps.size + 1
        val (dx, dy) = defaultPos(n)
        val start = addMarker("$n", "#8043A047", x ?: dx, y ?: dy)
        val end = addMarker("$n↗", "#80E53935", x2 ?: dx, y2 ?: (dy - dp(150)))
        steps.add(Step(start, end))
    }

    private fun defaultPos(n: Int): Pair<Int, Int> {
        val dm = resources.displayMetrics
        val size = dp(TARGET_DP)
        return Pair(dm.widthPixels / 2 - size / 2 + (n - 1) * dp(12), dm.heightPixels / 2 - size / 2 + (n - 1) * dp(12))
    }

    private fun addMarker(label: String, color: String, x: Int, y: Int): View {
        val size = dp(TARGET_DP)
        val v = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(color))
                setStroke(dp(2), Color.WHITE)
            }
        }
        val lp = overlayParams(size, size).apply { this.x = x; this.y = y }
        makeDraggable(v, v, lp)
        wm.addView(v, lp)
        return v
    }

    private fun removeLastStep() {
        if (running || steps.isEmpty()) return
        steps.removeAt(steps.lastIndex).views.forEach { runCatching { wm.removeView(it) } }
    }

    private fun clearSteps() {
        steps.forEach { s -> s.views.forEach { runCatching { wm.removeView(it) } } }
        steps.clear()
    }

    private fun pos(v: View): Pair<Int, Int> = (v.layoutParams as WindowManager.LayoutParams).let { it.x to it.y }

    /** Khi đang chạy, các điểm phải "trong suốt" với chạm để cử chỉ rơi xuống ứng dụng bên dưới. */
    private fun setMarkersTouchable(touchable: Boolean) {
        steps.flatMap { it.views }.forEach { v ->
            val lp = v.layoutParams as WindowManager.LayoutParams
            lp.flags = if (touchable) lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            v.alpha = if (touchable) 1f else 0.5f
            wm.updateViewLayout(v, lp)
        }
    }

    // ---------- Vòng lặp thao tác ----------

    private fun start() {
        if (steps.isEmpty()) {
            toast("Hãy thêm ít nhất một điểm (＋ hoặc ↗)")
            return
        }
        closePopup()
        stepIndex = 0
        cyclesDone = 0
        actionsDone = 0
        startedAt = SystemClock.elapsedRealtime()
        state = State.RUNNING
        renderPanel()
        if (settings.stopAfterMin > 0) handler.postDelayed(infoTicker, 1000)
        setMarkersTouchable(false)
        // Chờ một nhịp để hệ thống áp dụng FLAG_NOT_TOUCHABLE trước khi chạm.
        handler.postDelayed(::tick, 100)
    }

    /** Tạm dừng: giữ nguyên bước, số lần và thời gian đã chạy để tiếp tục sau. */
    private fun pause() {
        if (state != State.RUNNING) return
        state = State.PAUSED
        pausedAt = SystemClock.elapsedRealtime()
        handler.removeCallbacksAndMessages(null)
        renderPanel()
    }

    private fun resume() {
        if (state != State.PAUSED) return
        startedAt += SystemClock.elapsedRealtime() - pausedAt   // không tính thời gian tạm dừng vào hẹn giờ
        state = State.RUNNING
        renderPanel()
        if (settings.stopAfterMin > 0) handler.postDelayed(infoTicker, 1000)
        handler.postDelayed(::tick, 100)
    }

    fun stop() {
        if (state == State.IDLE) return
        state = State.IDLE
        handler.removeCallbacksAndMessages(null)
        setMarkersTouchable(true)
        renderPanel()
    }

    private fun center(v: View): Pair<Float, Float> {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val j = settings.jitterPx
        fun r() = if (j > 0) Random.nextInt(-j, j + 1) else 0
        return Pair(
            (loc[0] + v.width / 2f + r()).coerceAtLeast(0f),
            (loc[1] + v.height / 2f + r()).coerceAtLeast(0f),
        )
    }

    private fun tick() {
        if (!running) return
        val step = steps.getOrNull(stepIndex) ?: return stop()
        val (x, y) = center(step.start)
        val path = Path().apply { moveTo(x, y) }
        val duration = if (step.end != null) {
            val (x2, y2) = center(step.end)
            path.lineTo(x2, y2)
            settings.swipeMs
        } else settings.pressMs

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
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
        // Vẫn ghi nhận thao tác vừa xong nếu người dùng bấm ⏸ giữa chừng, để khi chạy tiếp không lặp lại bước đó.
        if (state == State.IDLE) return
        actionsDone++
        updateInfo()

        val stopMin = settings.stopAfterMin
        if (stopMin > 0 && SystemClock.elapsedRealtime() - startedAt >= stopMin * 60_000L) {
            stop()
            toast("Đã chạy đủ $stopMin phút")
            return
        }
        stepIndex++
        if (stepIndex >= steps.size) {
            stepIndex = 0
            cyclesDone++
            val limit = settings.repeatCount
            if (limit > 0 && cyclesDone >= limit) {
                stop()
                toast("Đã hoàn thành $limit vòng")
                return
            }
        }
        if (running) handler.postDelayed(::tick, settings.intervalMs)
    }

    // ---------- Bảng nổi phụ (⚙ cài đặt, 💾 kịch bản) ----------

    private fun togglePopup(key: String, build: () -> View) {
        val wasOpen = popup?.tag
        closePopup()
        if (wasOpen != key) showPopup(build().apply { tag = key })
    }

    private fun closePopup() {
        popup?.let { runCatching { wm.removeView(it) } }
        popup = null
    }

    private fun showPopup(content: View, atX: Int? = null, atY: Int? = null) {
        val lp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        // Kéo tiêu đề (con đầu tiên) để di chuyển bảng.
        (content as? LinearLayout)?.getChildAt(0)?.let { makeDraggable(it, content, lp) }
        // Đặt cạnh bảng điều khiển, lật sang trái nếu sát mép phải.
        val pp = panelParams
        val screenW = resources.displayMetrics.widthPixels
        val panelX = pp?.x ?: 0
        lp.x = if (panelX + dp(56) + dp(280) <= screenW) panelX + dp(60) else (panelX - dp(284)).coerceAtLeast(0)
        lp.y = pp?.y ?: dp(160)
        atX?.let { lp.x = it }
        atY?.let { lp.y = it }
        wm.addView(content, lp)
        popup = content
    }

    private fun popupBox(title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg("#F2212121", 16)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        addView(TextView(context).apply {
            text = "≡  $title"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(8))
        })
    }

    private fun closeLink(box: LinearLayout) = box.addView(TextView(this).apply {
        text = "Xong"
        gravity = Gravity.CENTER
        textSize = 16f
        setTextColor(Color.parseColor("#90CAF9"))
        setPadding(0, dp(10), 0, dp(6))
        setOnClickListener { closePopup() }
    })

    private fun buildSettingsPopup(): View {
        val box = popupBox("Cài đặt")
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(stepperRow("Khoảng cách (ms)", { settings.intervalMs }, { settings.intervalMs = it }, { "$it" },
            { v -> when { v < 100 -> 10L; v < 1_000 -> 50L; v < 10_000 -> 500L; else -> 5_000L } }))
        inner.addView(stepperRow("Số vòng", { settings.repeatCount.toLong() }, { settings.repeatCount = it.toInt() },
            { if (it == 0L) "∞" else "$it" }, { v -> when { v < 10 -> 1L; v < 100 -> 5L; else -> 50L } }))
        inner.addView(stepperRow("Tự dừng sau (phút)", { settings.stopAfterMin.toLong() }, { settings.stopAfterMin = it.toInt() },
            { if (it == 0L) "Tắt" else "$it" }, { v -> when { v < 10 -> 1L; v < 60 -> 5L; else -> 30L } }))
        inner.addView(stepperRow("Giữ mỗi lần chạm (ms)", { settings.pressMs }, { settings.pressMs = it }, { "$it" },
            { v -> when { v < 100 -> 10L; v < 1_000 -> 50L; else -> 250L } }))
        inner.addView(stepperRow("Thời gian vuốt (ms)", { settings.swipeMs }, { settings.swipeMs = it }, { "$it" },
            { v -> when { v < 1_000 -> 50L; else -> 250L } }))
        inner.addView(stepperRow("Lệch ngẫu nhiên (px)", { settings.jitterPx.toLong() }, { settings.jitterPx = it.toInt() },
            { if (it == 0L) "Tắt" else "$it" }, { 2L }))
        box.addView(ScrollView(this).apply {
            addView(inner)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, maxPopupHeight())
        })
        closeLink(box)
        return box
    }

    private fun buildProfilesPopup(): View {
        val box = popupBox("Kịch bản")
        box.addView(actionRow("＋ Lưu cấu hình hiện tại", "#90CAF9") {
            if (steps.isEmpty()) return@actionRow toast("Chưa có điểm nào để lưu")
            val name = profiles.nextName()
            profiles.add(currentProfile(name))
            toast("Đã lưu \"$name\"")
            refreshPopup("profiles", ::buildProfilesPopup)
        })
        val list = profiles.all()
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (list.isEmpty()) {
            inner.addView(TextView(this).apply {
                text = "Chưa có kịch bản nào.\nĐặt điểm xong bấm \"Lưu\" ở trên."
                textSize = 13f
                setTextColor(Color.parseColor("#BDBDBD"))
                setPadding(0, dp(8), 0, dp(8))
            })
        }
        list.forEachIndexed { i, p ->
            val taps = p.steps.count { !it.swipe }
            val swipes = p.steps.size - taps
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(TextView(this).apply {
                text = "${p.name}\n$taps chạm · $swipes vuốt · ${p.intervalMs} ms"
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                layoutParams = LinearLayout.LayoutParams(dp(200), LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    loadProfile(p)
                    toast("Đã tải \"${p.name}\"")
                    closePopup()
                }
            })
            row.addView(TextView(this).apply {
                text = "🗑"
                textSize = 18f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                setOnClickListener {
                    profiles.remove(i)
                    refreshPopup("profiles", ::buildProfilesPopup)
                }
            })
            inner.addView(row)
        }
        box.addView(ScrollView(this).apply {
            addView(inner)
            if (list.size > 4) layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, maxPopupHeight())
        })
        closeLink(box)
        return box
    }

    /** Dựng lại bảng đang mở tại đúng vị trí cũ (sau khi thêm/xoá kịch bản). */
    private fun refreshPopup(key: String, build: () -> View) {
        val lp = popup?.layoutParams as? WindowManager.LayoutParams
        closePopup()
        showPopup(build().apply { tag = key }, lp?.x, lp?.y)
    }

    private fun currentProfile(name: String) = Profile(
        name = name,
        steps = steps.map { s ->
            val (x, y) = pos(s.start)
            val (x2, y2) = s.end?.let(::pos) ?: (0 to 0)
            StepData(s.isSwipe, x, y, x2, y2)
        },
        intervalMs = settings.intervalMs,
        repeatCount = settings.repeatCount,
        pressMs = settings.pressMs,
        jitterPx = settings.jitterPx,
        swipeMs = settings.swipeMs,
        stopAfterMin = settings.stopAfterMin,
    )

    private fun loadProfile(p: Profile) {
        stop()
        clearSteps()
        p.steps.forEach { s -> if (s.swipe) addSwipe(s.x, s.y, s.x2, s.y2) else addTap(s.x, s.y) }
        settings.intervalMs = p.intervalMs
        settings.repeatCount = p.repeatCount
        settings.pressMs = p.pressMs
        settings.jitterPx = p.jitterPx
        settings.swipeMs = p.swipeMs
        settings.stopAfterMin = p.stopAfterMin
    }

    private fun maxPopupHeight() = (resources.displayMetrics.heightPixels * 0.55).toInt()

    private fun actionRow(label: String, color: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 15f
        setTextColor(Color.parseColor(color))
        setPadding(dp(4), dp(8), dp(4), dp(8))
        setOnClickListener { onClick() }
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
        background = roundedBg("#424242", 10)
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

    // ---------- Helpers ----------

    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun roundedBg(color: String, radiusDp: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun panelButton(label: String, onClick: (() -> Unit)? = null) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 20f
        setTextColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(46))
        onClick?.let { cb -> setOnClickListener { cb() } }
    }

    /** Kéo [handle] để di chuyển cửa sổ [window]. */
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
