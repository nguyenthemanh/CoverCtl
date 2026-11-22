package app.galaxywifi

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.provider.Settings

object WidgetAppIcons {
    private val FALLBACK = listOf(
        "com.android.settings",
        "com.android.chrome",
        "com.samsung.android.dialer",
        "com.sec.android.app.camera",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.sec.android.app.sbrowser",
        "com.google.android.youtube",
        "com.sec.android.app.launcher"
    )

    fun favoritesOrDefaults(context: Context): List<String> {
        val slots = Prefs.favoriteSlots(context)
        val hasAny = slots.any { it.isNotEmpty() }
        if (hasAny) return slots.filter { it.isNotEmpty() }.distinct().take(4)

        // Chưa chọn sở thích → 4 app hay dùng nhất
        val used = mostUsedLaunchable(context, 4)
        if (used.isNotEmpty()) return used

        return fallbackLaunchable(context, 4)
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }

    /** Top app hay dùng (14 ngày), chỉ app mở được. */
    fun mostUsedLaunchable(context: Context, limit: Int): List<String> {
        if (!hasUsageAccess(context)) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val end = System.currentTimeMillis()
        val start = end - 14L * 24 * 60 * 60 * 1000
        val stats = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
        } catch (_: Exception) {
            return emptyList()
        }
        if (stats.isNullOrEmpty()) return emptyList()

        val self = context.packageName
        return stats
            .asSequence()
            .filter { it.totalTimeInForeground > 0L }
            .filter { it.packageName != self }
            .sortedByDescending { it.totalTimeInForeground }
            .map { it.packageName }
            .distinct()
            .filter { isLaunchable(context, it) }
            .filter { !isExcludedSystem(it) }
            .take(limit)
            .toList()
    }

    fun labelFor(context: Context, packageName: String): String {
        if (packageName.isEmpty()) return ""
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    fun iconBitmap(context: Context, packageName: String, sizePx: Int): Bitmap? {
        val pm = context.packageManager
        val d: Drawable = try {
            pm.getApplicationIcon(packageName)
        } catch (_: Exception) {
            return null
        }
        return drawableToBitmap(d, sizePx)
    }

    fun launchIntent(context: Context, packageName: String): Intent? {
        val pm = context.packageManager
        return (pm.getLaunchIntentForPackage(packageName)
            ?: pm.getLeanbackLaunchIntentForPackage(packageName))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun fallbackLaunchable(context: Context, limit: Int): List<String> =
        FALLBACK.filter { isLaunchable(context, it) }.take(limit)

    private fun isLaunchable(context: Context, pkg: String): Boolean {
        val pm = context.packageManager
        return pm.getLaunchIntentForPackage(pkg) != null ||
            pm.getLeanbackLaunchIntentForPackage(pkg) != null
    }

    private fun isExcludedSystem(pkg: String): Boolean {
        val block = listOf(
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.samsung.android.app.telephonyui",
            "com.android.settings.intelligence"
        )
        return block.any { pkg.startsWith(it) || pkg == it }
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }
}
