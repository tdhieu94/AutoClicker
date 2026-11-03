package com.hieu.autoclicker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings as SysSettings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var status: TextView
    private lateinit var interval: EditText
    private lateinit var repeat: EditText
    private lateinit var press: EditText
    private lateinit var jitter: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        val pad = dp(20)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "AutoClicker"
            textSize = 28f
            setTextColor(Color.parseColor("#1E88E5"))
        })
        status = TextView(this).apply { textSize = 16f; setPadding(0, dp(8), 0, dp(16)) }
        root.addView(status)

        interval = field(root, "Khoảng cách giữa các lần chạm (ms)", settings.intervalMs.toString())
        repeat = field(root, "Số vòng lặp (0 = chạy mãi)", settings.repeatCount.toString())
        press = field(root, "Thời gian giữ mỗi lần chạm (ms)", settings.pressMs.toString())
        jitter = field(root, "Lệch ngẫu nhiên quanh điểm (px, 0 = tắt)", settings.jitterPx.toString())

        root.addView(button("1. Bật dịch vụ Trợ năng") {
            startActivity(Intent(SysSettings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Tìm \"AutoClicker\" trong danh sách và bật lên", Toast.LENGTH_LONG).show()
        })
        root.addView(button("2. Lưu & hiện bảng điều khiển nổi") {
            save()
            val svc = AutoClickService.instance
            if (svc == null) {
                Toast.makeText(this, "Hãy bật dịch vụ Trợ năng trước", Toast.LENGTH_LONG).show()
            } else {
                svc.showOverlay()
                moveTaskToBack(true)
            }
        })
        root.addView(button("Kiểm tra cập nhật (đang dùng ${BuildConfig.VERSION_NAME})") {
            UpdateChecker.check(this, manual = true)
        })
        root.addView(TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(24), 0, 0)
            text = """
                Cách dùng:
                • ＋ thêm một điểm chạm (vòng tròn có số). Kéo vòng tròn tới vị trí cần chạm.
                • － xoá điểm cuối cùng.
                • ⚙ chỉnh khoảng cách, số vòng… ngay trên màn hình (giữ ＋/－ để thay đổi nhanh).
                • ▶ bắt đầu chạm lần lượt theo thứ tự 1, 2, 3…; ■ để dừng.
                • ✕ ẩn bảng điều khiển.
                • Kéo nút ≡ để di chuyển bảng điều khiển.
            """.trimIndent()
        })

        setContentView(ScrollView(this).apply { addView(root) })
        if (savedInstanceState == null) UpdateChecker.check(this, manual = false)
    }

    override fun onResume() {
        super.onResume()
        val on = AutoClickService.instance != null
        status.text = if (on) "✅ Dịch vụ Trợ năng đang bật" else "⚠️ Dịch vụ Trợ năng chưa bật"
        status.setTextColor(if (on) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
        // Cài đặt có thể đã đổi trên bảng nổi: nạp lại để onPause không ghi đè bằng giá trị cũ.
        interval.setText(settings.intervalMs.toString())
        repeat.setText(settings.repeatCount.toString())
        press.setText(settings.pressMs.toString())
        jitter.setText(settings.jitterPx.toString())
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    private fun save() {
        interval.text.toString().toLongOrNull()?.let { settings.intervalMs = it }
        repeat.text.toString().toIntOrNull()?.let { settings.repeatCount = it }
        press.text.toString().toLongOrNull()?.let { settings.pressMs = it }
        jitter.text.toString().toIntOrNull()?.let { settings.jitterPx = it }
    }

    private fun field(parent: LinearLayout, label: String, value: String): EditText {
        parent.addView(TextView(this).apply { text = label; textSize = 14f })
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value)
            parent.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
