package app.coverctl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import rikka.shizuku.Shizuku

/**
 * Trạng thái / thao tác thiết lập — theo đúng chế độ đang chọn.
 * ADB ≠ Shizuku.
 */
object PrivilegeManager {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val REQUEST_CODE = 1001

    data class Status(
        val mode: PrivilegeMode,
        val ready: Boolean,
        val messageVi: String
    )

    fun status(context: Context): Status {
        val mode = Prefs.privilegeMode(context)
        Shell.mode = mode
        return when (mode) {
            PrivilegeMode.SHIZUKU -> {
                val ready = ShizukuShell.available()
                Status(mode, ready, ShizukuShell.statusLine())
            }
            PrivilegeMode.ADB -> {
                val ready = AdbShell.available()
                Status(mode, ready, AdbShell.statusLine())
            }
        }
    }

    fun isShizukuInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    /** Thiết lập theo chế độ hiện tại — không nhảy sang kênh kia. */
    fun autoSetup(context: Context): String {
        return when (Prefs.privilegeMode(context)) {
            PrivilegeMode.SHIZUKU -> setupShizuku(context)
            PrivilegeMode.ADB -> {
                openDeveloperOptions(context)
                "Chế độ ADB: bật Wireless debugging → Ghép đôi (mã 6 số) → Kết nối (cổng IP:port)"
            }
        }
    }

    fun setupShizuku(context: Context): String {
        if (!isShizukuInstalled(context)) {
            openShizukuInstall(context)
            return "Đang mở trang cài Shizuku"
        }
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            openShizukuApp(context)
            return "Đã mở Shizuku — bấm Start, rồi Cấp quyền Shizuku"
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE)
            return "Đã xin cấp quyền Shizuku"
        }
        return "Shizuku đã sẵn sàng"
    }

    fun openShizukuApp(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        return if (launch != null) {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } else false
    }

    fun openShizukuInstall(context: Context) {
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$SHIZUKU_PACKAGE")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: Exception) {
            context.startActivity(web)
        }
    }

    fun openDeveloperOptions(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openWirelessDebugging(context: Context) {
        // Nhiều máy Samsung không có action riêng — mở Developer options
        openDeveloperOptions(context)
    }
}
