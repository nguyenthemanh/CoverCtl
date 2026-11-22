package app.coverctl

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

/**
 * Widget multi-info kiểu Galaxy 5G Mobile Wi-Fi:
 * mạng / sóng / pin / data — đồng bộ trạng thái máy.
 */
class MobileWifiWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_BATTERY_CHANGED,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            WifiManagerCompat.NETWORK_ACTION,
            ACTION_REFRESH -> refreshAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "app.coverctl.action.REFRESH_WIFI_WIDGET"

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MobileWifiWidgetProvider::class.java))
            ids.forEach { updateWidget(context, mgr, it) }
        }

        fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val status = DeviceNetStatus.read(context)
            val views = RemoteViews(context.packageName, R.layout.widget_mobile_wifi)
            views.setTextViewText(R.id.txtWidgetNetwork, status.networkLabel)
            views.setTextViewText(R.id.txtWidgetLink, status.linkText)
            views.setTextViewText(R.id.txtWidgetSignal, status.signalBars)
            views.setTextViewText(R.id.txtWidgetBattery, status.batteryText)
            views.setTextViewText(R.id.txtWidgetData, status.dataText)

            val battColor = when {
                status.charging -> Color.parseColor("#34C759")
                status.batteryPercent <= 20 -> Color.parseColor("#FF453A")
                status.batteryPercent <= 40 -> Color.parseColor("#FF9F0A")
                else -> Color.parseColor("#FFFFFF")
            }
            views.setTextColor(R.id.txtWidgetBattery, battColor)

            val open = PendingIntent.getActivity(
                context,
                widgetId,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, open)

            val refresh = PendingIntent.getBroadcast(
                context,
                widgetId + 1000,
                Intent(context, MobileWifiWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.txtWidgetHint, refresh)

            mgr.updateAppWidget(widgetId, views)
        }
    }
}

object WifiManagerCompat {
    const val NETWORK_ACTION = "android.net.conn.CONNECTIVITY_CHANGE"
}
