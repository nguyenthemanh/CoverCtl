package app.coverctl

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object PackageCommands {
    data class AppEntry(
        val packageName: String,
        val label: String,
        val system: Boolean
    )

    fun listUninstallableApps(context: Context, includeSystem: Boolean = false): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val system = isSystemApp(context, pkg)
                if (system && !includeSystem) return@mapNotNull null
                val label = ri.loadLabel(pm)?.toString() ?: pkg
                AppEntry(pkg, label, system)
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy({ it.system }, { it.label.lowercase() }))
    }

    fun isSystemApp(context: Context, packageName: String): Boolean =
        try {
            val ai = context.packageManager.getApplicationInfo(packageName, 0)
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (_: Exception) {
            false
        }

    /**
     * Gỡ app qua shell của kênh đang chọn (ADB hoặc Shizuku — độc lập).
     */
    fun uninstall(packages: List<String>, keepData: Boolean = false): Shell.Result {
        if (packages.isEmpty()) {
            return Shell.Result(1, "", "Chưa chọn app nào")
        }
        val keepFlag = if (keepData) "-k " else ""
        val parts = packages.joinToString("; ") { pkg ->
            "echo -- $pkg; " +
                "if pm path $pkg >/dev/null 2>&1; then " +
                "  R1=\$(pm uninstall ${keepFlag}--user 0 $pkg 2>&1); echo \"\$R1\"; " +
                "  if echo \"\$R1\" | grep -qi Success; then echo OK; " +
                "  else R2=\$(pm uninstall ${keepFlag}$pkg 2>&1); echo \"\$R2\"; " +
                "    if echo \"\$R2\" | grep -qi Success; then echo OK; else echo FAIL; fi; " +
                "  fi; " +
                "else echo 'chưa cài'; echo FAIL; fi"
        }
        val via = when (Shell.mode) {
            PrivilegeMode.ADB -> "ADB"
            PrivilegeMode.SHIZUKU -> "Shizuku"
        }
        return Shell.exec(
            "echo '=== Gỡ ${packages.size} app (qua $via) ==='; $parts; echo 'Xong.'"
        )
    }
}
