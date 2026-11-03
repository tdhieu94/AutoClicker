package com.hieu.autoclicker

import android.app.Activity
import android.widget.FrameLayout

/** Bản GitHub: không quảng cáo, có tự kiểm tra cập nhật. */
object Flavor {
    const val HAS_UPDATER = true

    fun onCreate(activity: Activity, adContainer: FrameLayout, firstLaunch: Boolean) {
        if (firstLaunch) UpdateChecker.check(activity, manual = false)
    }

    fun checkForUpdates(activity: Activity) = UpdateChecker.check(activity, manual = true)

    /** Gọi trước khi ẩn app để hiện bảng điều khiển nổi. */
    fun beforeShowOverlay(activity: Activity, proceed: () -> Unit) = proceed()
}
