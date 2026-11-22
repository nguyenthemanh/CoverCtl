package app.coverctl

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Android Auto helpers inspired by UnknownAuto (Play install mark) + AnyAuto (car apps).
 * CoverCtl does not embed AnyAuto's GPL car mirror; it guides / opens those apps and can
 * install an APK with the Play installer attribution (same idea as UnknownAuto, MIT).
 */
object AndroidAutoCommands {
    const val GEARHEAD = "com.google.android.projection.gearhead"
    const val UNKNOWN_AUTO = "com.mag.unknownauto"
    const val ANY_AUTO = "com.mag.anyauto"
    const val VENDING = "com.android.vending"
    const val GOOGLE_INSTALLER = "com.google.android.packageinstaller"
    const val AOSP_INSTALLER = "com.android.packageinstaller"

    private const val SETTINGS =
        "$GEARHEAD/.companion.settings.DefaultSettingsActivity"
    private const val DEV_SETTINGS =
        "$GEARHEAD/.companion.devsettings.DeveloperSettingsActivity"

    private const val URL_UNKNOWN_AUTO =
        "https://github.com/GitMag/UnknownAuto/releases/latest"
    private const val URL_ANY_AUTO =
        "https://github.com/GitMag/AnyAuto/releases/latest"

    data class AppEntry(val packageName: String, val label: String)

    data class InstallSource(
        val packageName: String,
        val initiating: String?,
        val installing: String?,
        val aaAcceptLikely: Boolean
    )

    fun listLaunchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = ri.loadLabel(pm)?.toString() ?: pkg
                AppEntry(pkg, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun isInstalled(context: Context, packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun openPackageOrUrl(context: Context, packageName: String, url: String): String {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launch != null) {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Đã mở $packageName"
        } else {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "Chưa cài $packageName — đã mở trang tải"
        }
    }

    fun openUnknownAuto(context: Context): String =
        openPackageOrUrl(context, UNKNOWN_AUTO, URL_UNKNOWN_AUTO)

    fun openAnyAuto(context: Context): String =
        openPackageOrUrl(context, ANY_AUTO, URL_ANY_AUTO)

    fun openAndroidAutoSettings(): Shell.Result =
        Shell.exec(
            "am start -n $SETTINGS; " +
                "echo 'Đã mở Cài đặt Android Auto — kéo xuống Version, chạm 10 lần để bật Developer'"
        )

    fun openDeveloperSettings(): Shell.Result =
        Shell.exec(
            "am start -n $DEV_SETTINGS 2>/dev/null || am start -n $SETTINGS; " +
                "echo 'Mở Developer settings. Unknown sources chỉ giúp app thường; app Car Library cần dấu Play.'"
        )

    fun unlockUnknownSources(): Shell.Result =
        Shell.exec(
            """
            echo '=== Android Auto unlock ==='
            am start -n $SETTINGS
            settings put global car_projection_developer_mode 1 2>/dev/null || true
            settings put secure android_auto_unknown_sources 1 2>/dev/null || true
            settings put global android_auto_unknown_sources 1 2>/dev/null || true
            am start -n $DEV_SETTINGS 2>/dev/null || true
            echo 'Trong Developer settings hãy bật Unknown sources (nếu có).'
            echo 'Lưu ý: app kiểu AnyAuto / Car App Library cần cài qua UnknownAuto (dấu Play), không chỉ Unknown sources.'
            """.trimIndent().replace("\n", "; ")
        )

    fun checklist(context: Context): String {
        val shell = PrivilegeManager.status(context)
        val aa = isInstalled(context, GEARHEAD)
        val ua = isInstalled(context, UNKNOWN_AUTO)
        val any = isInstalled(context, ANY_AUTO)
        val lines = buildList {
            add("=== Checklist Android Auto (AnyAuto / UnknownAuto) ===")
            add("Shell (${shell.mode.labelVi}): ${if (shell.ready) "OK" else "CHƯA — ${shell.messageVi}"}")
            add("Android Auto: ${if (aa) "đã cài" else "CHƯA"}")
            add("UnknownAuto: ${if (ua) "đã cài" else "CHƯA — dùng để cài APK với dấu Play"}")
            add("AnyAuto: ${if (any) "đã cài" else "CHƯA — đưa app thường lên màn hình xe"}")
            add("")
            add("Cách dùng khuyến nghị:")
            add("1) Chọn ADB hoặc Shizuku (độc lập) và kết nối/cấp quyền")
            add("2) UnknownAuto: Load APK (vd AnyAuto) → Install (dấu Play)")
            add("3) AnyAuto: chọn app → kết nối xe → mở AnyAuto trên AA")
            add("4) Unknown sources: tùy chọn cho app thường (không đủ cho Car Library)")
        }
        return lines.joinToString("\n")
    }

    fun installSource(context: Context, packageName: String): InstallSource? {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo(packageName, 0)
            val initiating: String?
            val installing: String?
            if (Build.VERSION.SDK_INT >= 30) {
                val info = pm.getInstallSourceInfo(packageName)
                initiating = info.initiatingPackageName
                installing = info.installingPackageName
            } else {
                @Suppress("DEPRECATION")
                initiating = null
                @Suppress("DEPRECATION")
                installing = pm.getInstallerPackageName(packageName)
            }
            val aaOk = isAaAcceptLikely(initiating, installing)
            InstallSource(packageName, initiating, installing, aaOk)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun isAaAcceptLikely(initiating: String?, installing: String?): Boolean {
        // UnknownAuto path: initiating=packageinstaller + installing=vending
        // Cài từ Play Store thật: initiating=vending + installing=vending (Android 14+)
        val installOk = installing == VENDING
        val initOk = initiating == GOOGLE_INSTALLER ||
            initiating == AOSP_INSTALLER ||
            initiating == VENDING
        return installOk && initOk
    }

    /**
     * Kiểm tra nguồn cài của app đã chọn. Không dùng set-installer (AA bỏ qua).
     */
    fun checkPlayMarks(context: Context, packages: List<String>): String {
        if (packages.isEmpty()) {
            return "Chưa chọn ứng dụng. Bấm «Chọn app kiểm tra» trước."
        }
        val sb = StringBuilder()
        sb.appendLine("=== Kiểm tra dấu Play (InstallSource) ===")
        sb.appendLine("AA (Car Library) OK khi installing=vending và initiating=packageinstaller hoặc vending.")
        sb.appendLine("App hệ thống thường không có nguồn — không dùng cho Car Library.")
        sb.appendLine()
        var ok = 0
        var skip = 0
        for (pkg in packages) {
            val src = installSource(context, pkg)
            if (src == null) {
                sb.appendLine("✘ $pkg — chưa cài")
                continue
            }
            val system = isSystemApp(context, pkg)
            val mark = when {
                src.aaAcceptLikely -> {
                    ok++
                    "✔ AA OK"
                }
                system && src.initiating == null && src.installing == null -> {
                    skip++
                    "○ hệ thống (không áp dụng dấu Play)"
                }
                else -> "✘ thiếu dấu Play (cần UnknownAuto / Cài APK như Play)"
            }
            sb.appendLine("$mark  $pkg")
            sb.appendLine("    initiating=${src.initiating ?: "?"}")
            sb.appendLine("    installing=${src.installing ?: "?"}")
        }
        sb.appendLine()
        sb.appendLine("Kết quả: $ok/${packages.size} đủ dấu Play; $skip app hệ thống bỏ qua.")
        val need = packages.size - ok - skip
        if (need > 0) {
            sb.appendLine("→ $need app cần cài lại qua UnknownAuto hoặc «Cài APK như từ Play».")
        }
        return sb.toString()
    }

    fun status(context: Context): Shell.Result {
        val local = checklist(context)
        if (!Shell.available()) {
            return Shell.Result(0, local, "")
        }
        val remote = Shell.exec(
            """
            echo '=== Shell / settings ==='
            pm path $GEARHEAD 2>/dev/null || echo 'Chưa cài Android Auto'
            echo -n 'car_projection_developer_mode='; settings get global car_projection_developer_mode
            echo -n 'android_auto_unknown_sources(global)='; settings get global android_auto_unknown_sources
            dumpsys package $GEARHEAD 2>/dev/null | grep -i 'versionName' | head -n 2
            """.trimIndent().replace("\n", "; ")
        )
        return Shell.Result(
            remote.code,
            listOf(local, remote.stdout).filter { it.isNotBlank() }.joinToString("\n\n"),
            remote.stderr
        )
    }

    fun ensureInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return true
        val pm = context.packageManager
        if (pm.canRequestPackageInstalls()) return true
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return false
    }

    fun isSystemApp(context: Context, packageName: String): Boolean =
        try {
            val ai = context.packageManager.getApplicationInfo(packageName, 0)
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (_: Exception) {
            false
        }
}
