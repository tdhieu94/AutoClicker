package com.hieu.autoclicker

import android.content.Context

/** Cấu hình lưu trong SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("autoclicker", Context.MODE_PRIVATE)

    /** Thời gian chờ giữa hai lần chạm (ms). */
    var intervalMs: Long
        get() = prefs.getLong("interval", 500L)
        set(v) = prefs.edit().putLong("interval", v.coerceAtLeast(MIN_INTERVAL_MS)).apply()

    /** Số vòng lặp qua toàn bộ các điểm. 0 = chạy mãi đến khi bấm dừng. */
    var repeatCount: Int
        get() = prefs.getInt("repeat", 0)
        set(v) = prefs.edit().putInt("repeat", v.coerceAtLeast(0)).apply()

    /** Thời gian giữ ngón tay mỗi lần chạm (ms). */
    var pressMs: Long
        get() = prefs.getLong("press", 20L)
        set(v) = prefs.edit().putLong("press", v.coerceIn(1L, 5_000L)).apply()

    /** Lệch ngẫu nhiên tối đa quanh tâm điểm (px), giúp chạm trông tự nhiên hơn. */
    var jitterPx: Int
        get() = prefs.getInt("jitter", 0)
        set(v) = prefs.edit().putInt("jitter", v.coerceIn(0, 100)).apply()

    /** Thời gian thực hiện một lần vuốt (ms). */
    var swipeMs: Long
        get() = prefs.getLong("swipe", 300L)
        set(v) = prefs.edit().putLong("swipe", v.coerceIn(50L, 5_000L)).apply()

    /** Tự dừng sau số phút này. 0 = không hẹn giờ. */
    var stopAfterMin: Int
        get() = prefs.getInt("stop_after", 0)
        set(v) = prefs.edit().putInt("stop_after", v.coerceIn(0, 24 * 60)).apply()

    companion object {
        const val MIN_INTERVAL_MS = 10L
    }
}
