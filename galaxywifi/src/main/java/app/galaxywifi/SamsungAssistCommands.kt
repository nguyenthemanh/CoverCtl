package app.galaxywifi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

object SamsungAssistCommands {
    fun openCoverWidgetSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent("com.samsung.settings.SubLauncherWidgetSettings").setComponent(
                ComponentName(
                    "com.samsung.android.app.aodservice",
                    "com.samsung.android.app.subui.settings.multiwidget.SubLauncherMultiWidgetSettingsActivity"
                )
            ),
            Intent("com.samsung.settings.SubLauncherWidgetSettings"),
            Intent().setComponent(
                ComponentName(
                    "com.samsung.android.app.aodservice",
                    "com.samsung.android.app.subui.settings.multiwidget.SubLauncherMultiWidgetSettingsActivity"
                )
            ),
            Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
        )
        return startFirst(context, candidates)
    }

    /** Cài đặt → Màn ngoài → Ứng dụng (cho phép mở app trên cover). */
    fun openCoverAppsSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent("com.samsung.settings.APPS_ALLOWED_COVER_SCREEN"),
            Intent("com.samsung.settings.COVER_SCREEN_SETTINGS"),
            Intent().setComponent(
                ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$CoverScreenSettingsActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.samsung.android.app.dressroom",
                    "com.samsung.android.app.dressroom.presentation.settings.WallpaperSettingActivity"
                )
            ),
            Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
        )
        return startFirst(context, candidates)
    }

    private fun startFirst(context: Context, candidates: List<Intent>): Boolean {
        for (raw in candidates) {
            try {
                context.startActivity(Intent(raw).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun requestPinWifiWidget(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        val mgr = AppWidgetManager.getInstance(context)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(context, MobileWifiWidgetProvider::class.java)
        val success = PendingIntent.getBroadcast(
            context,
            9101,
            Intent(context, MobileWifiWidgetProvider::class.java)
                .setAction(MobileWifiWidgetProvider.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return try {
            mgr.requestPinAppWidget(provider, null, success)
        } catch (_: IllegalStateException) {
            false
        }
    }
}
