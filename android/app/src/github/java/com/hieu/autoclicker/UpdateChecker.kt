package com.hieu.autoclicker

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings as SysSettings
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kiểm tra bản mới trên GitHub Releases (tag "latest").
 * CI đăng kèm version.json: {"versionCode": 107, "versionName": "1.3.7", "notes": "..."}.
 */
object UpdateChecker {
    private const val BASE = "https://github.com/tdhieu94/AutoClicker/releases/download/latest"
    private const val VERSION_URL = "$BASE/version.json"
    private const val APK_URL = "$BASE/AutoClicker.apk"
    private const val PREFS = "updates"
    private const val KEY_SKIPPED = "skipped_version"

    data class Release(val versionCode: Int, val versionName: String, val notes: String)

    /**
     * @param manual true khi người dùng tự bấm "Kiểm tra cập nhật": luôn báo kết quả,
     *               kể cả khi đã mới nhất hoặc bản này từng được bấm "Để sau".
     */
    fun check(activity: Activity, manual: Boolean) {
        val main = Handler(Looper.getMainLooper())
        Thread {
            val release = runCatching { fetch() }.getOrNull()
            main.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                when {
                    release == null ->
                        if (manual) toast(activity, "Không kiểm tra được, hãy thử lại khi có mạng")
                    release.versionCode <= BuildConfig.VERSION_CODE ->
                        if (manual) toast(activity, "Bạn đang dùng bản mới nhất (${BuildConfig.VERSION_NAME})")
                    !manual && prefs.getInt(KEY_SKIPPED, 0) == release.versionCode -> Unit
                    else -> showDialog(activity, release)
                }
            }
        }.start()
    }

    private fun fetch(): Release {
        val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Cache-Control", "no-cache")
        try {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            return Release(
                json.getInt("versionCode"),
                json.optString("versionName", ""),
                json.optString("notes", ""),
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun showDialog(activity: Activity, r: Release) {
        val msg = buildString {
            append("Bạn đang dùng ${BuildConfig.VERSION_NAME}.")
            if (r.notes.isNotBlank()) append("\n\nThay đổi: ${r.notes}")
        }
        AlertDialog.Builder(activity)
            .setTitle("Có bản mới ${r.versionName}")
            .setMessage(msg)
            .setPositiveButton("Cập nhật") { _, _ -> startUpdate(activity, r) }
            .setNegativeButton("Để sau") { _, _ ->
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_SKIPPED, r.versionCode).apply()
            }
            .show()
    }

    private fun startUpdate(activity: Activity, r: Release) {
        // Android 8+: phải cho phép AutoClicker "cài ứng dụng không rõ nguồn gốc" một lần.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            toast(activity, "Hãy bật \"Cho phép từ nguồn này\", rồi quay lại bấm Cập nhật")
            activity.startActivity(
                Intent(SysSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return
        }

        val app = activity.applicationContext
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(APK_URL))
            .setTitle("AutoClicker ${r.versionName}")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, "AutoClicker-${r.versionCode}.apk")
        val id = runCatching { dm.enqueue(request) }.getOrElse {
            openInBrowser(activity)
            return
        }
        toast(activity, "Đang tải bản mới…")

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                ctx.unregisterReceiver(this)
                val uri = dm.getUriForDownloadedFile(id)
                if (uri == null) {
                    toast(ctx, "Tải thất bại, mở trình duyệt để tải thủ công")
                    openInBrowser(ctx)
                    return
                }
                val install = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(install) }.onFailure { openInBrowser(ctx) }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
    }

    private fun openInBrowser(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    private const val APK_MIME = "application/vnd.android.package-archive"
}
