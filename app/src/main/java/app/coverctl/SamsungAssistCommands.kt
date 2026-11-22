package app.coverctl

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** Good Lock / One Hand Operation+ / MultiStar helpers for cover workflow. */
object SamsungAssistCommands {
    const val PKG_OHO = "com.samsung.android.sidegesturepad"
    const val PKG_GOODLOCK = "com.samsung.android.goodlock"
    const val PKG_MULTISTAR = "com.samsung.android.multistar"
    const val PKG_EASY_ONE_HAND = "com.sec.android.easyonehand"

    fun installed(context: Context, pkg: String): Boolean =
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun status(context: Context): String = buildString {
        appendLine("One Hand Operation+: ${if (installed(context, PKG_OHO)) "installed" else "missing"}")
        appendLine("Good Lock: ${if (installed(context, PKG_GOODLOCK)) "installed" else "missing"}")
        appendLine("MultiStar: ${if (installed(context, PKG_MULTISTAR)) "installed" else "missing"}")
        appendLine("Easy One Hand: ${if (installed(context, PKG_EASY_ONE_HAND)) "installed" else "missing"}")
        appendLine()
        appendLine("Ghi chú: Samsung không hỗ trợ OHO+ trên cover Flip.")
        appendLine("CoverCtl dùng gesture cạnh riêng trên màn ngoài (kiểu OHO+).")
    }

    fun openOrStore(context: Context, pkg: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        return try {
            if (launch != null) {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } else {
                openStore(context, pkg)
                false
            }
        } catch (_: ActivityNotFoundException) {
            openStore(context, pkg)
            false
        }
    }

    fun openStore(context: Context, pkg: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Bật Edge panel Samsung nếu có (hữu ích khi dual). */
    fun enableSamsungEdge(): Shell.Result =
        Shell.exec(
            """
            settings put system edge_enable 1
            settings put system edge_lighting 1
            settings put system edge_show_screen 0
            echo edge_enable=$(settings get system edge_enable)
            echo 'OK: Edge panel bật (kéo cạnh màn chính / dual nếu firmware hỗ trợ)'
            """.trimIndent().lines().joinToString("; ")
        )

    fun openOnCover(pkg: String): Shell.Result =
        ScreenModes.launchOnCover(pkg)
}
