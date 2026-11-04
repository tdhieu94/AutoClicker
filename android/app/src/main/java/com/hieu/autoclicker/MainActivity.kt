package com.hieu.autoclicker

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.FrameLayout
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

        root.addView(button("1. Bật dịch vụ Trợ năng") { openAccessibilityWithDisclosure() })
        root.addView(button("2. Lưu & hiện bảng điều khiển nổi") {
            save()
            val svc = AutoClickService.instance
            if (svc == null) {
                Toast.makeText(this, "Hãy bật dịch vụ Trợ năng trước", Toast.LENGTH_LONG).show()
            } else {
                Flavor.beforeShowOverlay(this) {
                    svc.showOverlay()
                    moveTaskToBack(true)
                }
            }
        })
        if (Flavor.HAS_UPDATER) {
            root.addView(button("Kiểm tra cập nhật (đang dùng ${BuildConfig.VERSION_NAME})") {
                Flavor.checkForUpdates(this)
            })
        }
        root.addView(TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(24), 0, 0)
            text = """
                Cách dùng:
                • ▶ bắt đầu. Khi đang chạy, bảng nổi thu gọn chỉ còn ⏸ và số lần chạm, số vòng.
                  ⏸ tạm dừng; sau đó ▶ chạy tiếp từ chỗ dừng, ■ kết thúc.
                • ＋ thêm điểm chạm (vòng xanh dương có số), kéo tới chỗ cần chạm.
                • ↗ thêm thao tác vuốt: kéo vòng xanh lá (điểm đầu) và vòng đỏ (điểm cuối).
                • ⋯ hiện thêm: － xoá bước cuối, 💾 lưu/tải kịch bản, ⚙ chỉnh thông số, ✕ ẩn bảng.
                • Kéo vạch xám trên cùng để di chuyển bảng.
            """.trimIndent()
        })

        root.addView(TextView(this).apply {
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dp(16), 0, 0)
            text = "Phiên bản ${BuildConfig.VERSION_NAME}"
        })

        // Nội dung cuộn ở trên, vùng quảng cáo (chỉ bản CH Play) cố định ở dưới.
        val adContainer = FrameLayout(this)
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(ScrollView(this@MainActivity).apply { addView(root) },
                LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(adContainer, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        setContentView(screen)
        Flavor.onCreate(this, adContainer, firstLaunch = savedInstanceState == null)
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

    /**
     * Chính sách Google Play: trước khi người dùng bật dịch vụ Trợ năng, phải giải thích rõ
     * app dùng quyền này để làm gì và xin đồng ý.
     */
    private fun openAccessibilityWithDisclosure() {
        val prefs = getSharedPreferences("consent", MODE_PRIVATE)
        val open = {
            startActivity(Intent(SysSettings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Tìm \"AutoClicker\" trong danh sách và bật lên", Toast.LENGTH_LONG).show()
        }
        if (prefs.getBoolean("accessibility_disclosure", false)) return open()
        AlertDialog.Builder(this)
            .setTitle("AutoClicker cần quyền Trợ năng")
            .setMessage(
                "AutoClicker dùng API Trợ năng (Accessibility Service) của Android để:\n\n" +
                    "• Thực hiện thao tác chạm và vuốt tại các điểm BẠN tự đặt trên màn hình, chỉ khi bạn bấm ▶.\n" +
                    "• Hiển thị bảng điều khiển nổi trên các ứng dụng khác.\n\n" +
                    "AutoClicker KHÔNG đọc nội dung màn hình, không ghi lại những gì bạn gõ, và không thu thập " +
                    "hay gửi dữ liệu cá nhân nào qua quyền này.\n\n" +
                    "Bạn có thể tắt quyền bất cứ lúc nào trong Cài đặt → Trợ năng."
            )
            .setPositiveButton("Đồng ý") { _, _ ->
                prefs.edit().putBoolean("accessibility_disclosure", true).apply()
                open()
            }
            .setNegativeButton("Không đồng ý", null)
            .show()
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
