package app.galaxywifi

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Widget multi-info kiểu Galaxy 5G Mobile Wi‑Fi (SCR01):
 * meter vòng, status, lịch, quick tiles — hoạt động trên cover.
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
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            ACTION_REFRESH -> refreshAll(context)
            ACTION_TILE -> {
                val tile = intent.getStringExtra(EXTRA_TILE).orEmpty()
                handleTile(context, tile)
                // Wi‑Fi/hotspot cần chút thời gian để trạng thái đổi
                val delay = if (tile == TILE_WIFI || tile == TILE_HOTSPOT || tile == TILE_TETHER) 1200L else 0L
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    refreshAll(context)
                }, delay)
            }
        }
    }

    private fun handleTile(context: Context, tile: String) {
        when (tile) {
            TILE_REFRESH -> Unit
            TILE_DARK -> {
                Prefs.setWidgetAutoDark(context, false)
                Prefs.setWidgetDark(context, !Prefs.widgetDark(context))
            }
            TILE_HOTSPOT -> {
                val wasOn = HotspotToggle.isEnabled(context)
                val ok = HotspotToggle.toggle(context)
                if (!ok) openTetherSettings(context)
                else {
                    // Báo nhẹ qua meta sau refresh; nếu OS từ chối vẫn mở settings
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (HotspotToggle.isEnabled(context) == wasOn) {
                            openTetherSettings(context)
                        }
                    }, 1500L)
                }
            }
            TILE_TETHER -> openTetherSettings(context)
            TILE_WIFI -> openWifiSettings(context)
            TILE_SETTINGS -> openOnCover(context, MainActivity::class.java)
            TILE_APPS -> openOnCover(context, CoverAppDrawerActivity::class.java)
            TILE_DATA -> openDataUsageSettings(context)
            TILE_SIGNAL, TILE_NETWORK -> openNetworkModeSettings(context)
            TILE_QR, TILE_PASS -> openHotspotPasswordSettings(context)
            TILE_FAV0, TILE_FAV1, TILE_FAV2, TILE_FAV3 -> {
                val idx = when (tile) {
                    TILE_FAV0 -> 0
                    TILE_FAV1 -> 1
                    TILE_FAV2 -> 2
                    else -> 3
                }
                val pkg = WidgetAppIcons.favoritesOrDefaults(context).getOrNull(idx)
                if (pkg != null) launchPackageOnCover(context, pkg)
                else openOnCover(context, CoverAppDrawerActivity::class.java)
            }
        }
    }

    private fun launchPackageOnCover(context: Context, packageName: String) {
        val launch = WidgetAppIcons.launchIntent(context, packageName) ?: return
        val opts = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 26) {
            opts.launchDisplayId = ScreenModes.COVER_DISPLAY
        }
        try {
            context.startActivity(launch, opts.toBundle())
        } catch (_: Exception) {
            try {
                context.startActivity(launch)
            } catch (_: Exception) {
            }
        }
    }

    private fun openOnCover(context: Context, cls: Class<*>) {
        val intent = Intent(context, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opts = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 26) {
            opts.launchDisplayId = ScreenModes.COVER_DISPLAY
        }
        try {
            context.startActivity(intent, opts.toBundle())
        } catch (_: Exception) {
            context.startActivity(intent)
        }
    }

    /** Khoang tròn Data → Data SIM / sử dụng dữ liệu di động (không mở Wi‑Fi). */
    private fun openDataUsageSettings(context: Context) {
        val subId = try {
            val sm = context.getSystemService(android.telephony.SubscriptionManager::class.java)
            sm?.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId
                ?: android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()
        } catch (_: Exception) {
            android.telephony.SubscriptionManager.getDefaultDataSubscriptionId()
        }

        val candidates = listOf(
            // Data di động theo SIM (không Wi‑Fi)
            Intent("android.settings.MOBILE_DATA_USAGE")
                .putExtra("android.provider.extra.SUB_ID", subId),
            Intent("android.settings.MOBILE_DATA_USAGE")
                .putExtra("subscription_id", subId),
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$MobileDataUsageListActivity"
            ),
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$DataUsageSummaryActivity"
            ),
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$BillingCycleSettingsActivity"
            ),
            Intent().setClassName(
                "com.samsung.android.app.telephonyui",
                "com.samsung.android.app.telephonyui.netsettings.ui.MobileNetworksActivity"
            ),
            Intent().setClassName(
                "com.samsung.android.app.telephonyui",
                "com.samsung.android.app.telephonyui.netsettings.ui.datausage.DataUsageSummaryActivity"
            ),
            Intent().setClassName(
                "com.samsung.android.sm",
                "com.samsung.android.sm.datausage.ui.DataUsageSummaryActivity"
            ),
            Intent(Settings.ACTION_DATA_USAGE_SETTINGS),
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
        )
        openFirstResolved(context, candidates)
    }

    /** Khoang tròn sóng / 通信モード → Mạng di động / chế độ mạng */
    private fun openNetworkModeSettings(context: Context) {
        val candidates = listOf(
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$MobileNetworkActivity"
            ),
            Intent().setClassName(
                "com.samsung.android.app.telephonyui",
                "com.samsung.android.app.telephonyui.netsettings.ui.NetSettingsActivity"
            ),
            Intent().setClassName(
                "com.android.phone",
                "com.android.phone.MobileNetworkSettings"
            ),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )
        openFirstWorking(context, candidates)
    }

    /** Ô Wi‑Fi → Cài đặt Wi‑Fi hệ thống */
    private fun openWifiSettings(context: Context) {
        val candidates = listOf(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent("android.settings.WIFI_SETTINGS"),
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$WifiSettingsActivity"
            ),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )
        openFirstWorking(context, candidates)
    }

    /** Điểm phát sóng / Chia sẻ kết nối Internet */
    private fun openTetherSettings(context: Context) {
        val candidates = listOf(
            Intent("android.settings.TETHER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$SecTetherSettingsActivity"
            ),
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$TetherSettingsActivity"
            ),
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.TetherSettings"
            ),
            Intent().setClassName(
                "com.samsung.android.app.telephonyui",
                "com.samsung.android.app.telephonyui.netsettings.ui.NetSettingsActivity"
            )
        )
        openFirstWorking(context, candidates)
    }

    /** Màn cấu hình tên/mật khẩu hotspot hệ thống (Samsung One UI). */
    private fun openHotspotPasswordSettings(context: Context) {
        val candidates = listOf(
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$WifiApSettingsActivity"
            ),
            Intent().setClassName(
                "com.android.settings",
                "com.samsung.android.settings.wifi.mobileap.WifiApSettingsActivity"
            ),
            Intent().setClassName(
                "com.android.settings",
                "com.samsung.android.settings.wifi.mobileap.WifiApEditSettings"
            ),
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.wifi.mobileap.WifiApSettings"
            ),
            Intent("android.settings.WIFI_AP_SETTINGS"),
            Intent("com.android.settings.WIFI_AP_SETTINGS"),
            Intent("android.settings.TETHER_SETTINGS"),
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.Settings\$SecTetherSettingsActivity"
            )
        )
        openFirstWorking(context, candidates)
    }

    private fun openFirstWorking(context: Context, candidates: List<Intent>) {
        openFirstResolved(context, candidates)
    }

    private fun openFirstResolved(context: Context, candidates: List<Intent>) {
        val pm = context.packageManager
        for (raw in candidates) {
            val intent = Intent(raw).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = try {
                intent.resolveActivity(pm) != null
            } catch (_: Exception) {
                false
            }
            if (!resolved) continue
            if (openIntentOnCover(context, intent)) return
        }
        // Thử vẫn start nếu resolveActivity null nhưng class tồn tại
        for (raw in candidates) {
            val intent = Intent(raw).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (openIntentOnCover(context, intent)) return
        }
    }

    private fun openIntentOnCover(context: Context, intent: Intent): Boolean {
        val opts = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 26) {
            opts.launchDisplayId = ScreenModes.COVER_DISPLAY
        }
        return try {
            context.startActivity(intent, opts.toBundle())
            true
        } catch (_: Exception) {
            try {
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "app.galaxywifi.action.REFRESH_WIFI_WIDGET"
        const val ACTION_TILE = "app.galaxywifi.action.WIFI_WIDGET_TILE"
        const val EXTRA_TILE = "tile"
        const val TILE_WIFI = "wifi"
        const val TILE_HOTSPOT = "hotspot"
        const val TILE_TETHER = "tether"
        const val TILE_DARK = "dark"
        const val TILE_REFRESH = "refresh"
        const val TILE_SETTINGS = "settings"
        const val TILE_DATA = "data"
        const val TILE_SIGNAL = "signal"
        const val TILE_NETWORK = "network"
        const val TILE_QR = "qr"
        const val TILE_PASS = "pass"
        const val TILE_APPS = "apps"
        const val TILE_FAV0 = "fav0"
        const val TILE_FAV1 = "fav1"
        const val TILE_FAV2 = "fav2"
        const val TILE_FAV3 = "fav3"

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MobileWifiWidgetProvider::class.java))
            ids.forEach { updateWidget(context, mgr, it) }
        }

        fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            val status = DeviceNetStatus.read(context)
            val views = RemoteViews(context.packageName, R.layout.widget_mobile_wifi)
            val dark = resolveDark(context)
            val dens = context.resources.displayMetrics.density
            fun dp(v: Int) = (v * dens).toInt().coerceAtLeast(1)

            applyTheme(views, dark)

            val now = Date()
            val time = DateFormat.getTimeFormat(context).format(now)
            views.setTextViewText(R.id.txtWidgetTime, "$time  ${compactDate(context, now)}")
            views.setViewVisibility(R.id.txtWidgetDate, View.GONE)
            // Icon: đang sáng → hiện trăng (bấm sang tối); đang tối → hiện nắng
            views.setImageViewResource(
                R.id.btnThemeToggle,
                if (dark) R.drawable.ic_theme_light else R.drawable.ic_theme_dark
            )
            views.setTextViewText(R.id.txtWidgetNetwork, status.uplinkLabel)
            val apName = when {
                status.apSsid.isNotBlank() -> status.apSsid
                status.hotspotOn -> context.getString(R.string.widget_ap_unnamed)
                else -> context.getString(R.string.widget_hotspot_off)
            }
            views.setTextViewText(R.id.txtWidgetLink, apName)
            // Mật khẩu không hiện trên widget — chỉ khi nhấn QR
            views.setViewVisibility(R.id.txtWidgetPass, View.GONE)
            views.setViewVisibility(R.id.txtPassLabel, View.GONE)
            val meta = buildString {
                if (status.apBand.isNotBlank()) append(status.apBand)
                if (isNotEmpty()) append(" · ")
                append(if (status.hotspotOn) "ON" else "OFF")
                append(" · ")
                append(context.getString(R.string.widget_uplink_fmt, status.uplinkLabel))
            }
            views.setTextViewText(R.id.txtApMeta, meta)
            views.setTextViewText(
                R.id.btnHotspotToggle,
                if (status.hotspotOn) {
                    context.getString(R.string.widget_tile_hotspot_on)
                } else {
                    context.getString(R.string.widget_tile_hotspot)
                }
            )
            views.setTextColor(
                R.id.btnHotspotToggle,
                if (status.hotspotOn) Color.parseColor("#DC2626") else Color.parseColor("#0B57D0")
            )
            views.setTextViewText(R.id.txtWidgetSignal, status.signalBars)
            views.setTextViewText(R.id.txtWidgetData, status.dataText)
            views.setTextViewText(
                R.id.txtClients,
                context.getString(R.string.widget_clients_fmt, status.connectedClients, status.maxClients)
            )
            val label = if (dark) Color.WHITE else Color.parseColor("#111827")
            val track = if (dark) Color.parseColor("#333333") else Color.parseColor("#E5E7EB")
            val blue = Color.parseColor("#0B57D0")

            // Status icons (không hiện pin — góc phải hệ thống đã có)
            views.setImageViewBitmap(
                R.id.imgSignalBars,
                WidgetMeterBitmap.signalBars(
                    dp(28), dp(16), status.signalLevel,
                    if (dark) Color.parseColor("#93C5FD") else blue,
                    track
                )
            )

            // Circular meters (SCR01)
            val showMeters = Prefs.widgetShowMeters(context)
            val meterSize = dp(56)
            views.setViewVisibility(R.id.panelAppShortcuts, View.VISIBLE)
            views.setViewVisibility(R.id.btnTileApps, View.VISIBLE)
            if (showMeters && Prefs.widgetShowData(context)) {
                val dataPct = dataMeterPercent(status.mobileDataMb) / 100f
                views.setImageViewBitmap(
                    R.id.imgDataMeter,
                    WidgetMeterBitmap.draw(
                        meterSize, dataPct, track, blue,
                        status.dataText, context.getString(R.string.widget_data_used),
                        label
                    )
                )
            }
            if (showMeters && Prefs.widgetShowSignal(context)) {
                val sigPct = status.signalLevel.coerceIn(0, 4) / 4f
                views.setImageViewBitmap(
                    R.id.imgSignalMeter,
                    WidgetMeterBitmap.draw(
                        meterSize, sigPct, track,
                        if (dark) Color.parseColor("#38BDF8") else Color.parseColor("#0284C7"),
                        status.uplinkLabel, "${status.signalLevel}/4",
                        label
                    )
                )
            }

            views.setViewVisibility(
                R.id.panelSignal,
                if (Prefs.widgetShowSignal(context)) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.panelData,
                if (Prefs.widgetShowData(context)) View.VISIBLE else View.GONE
            )

            views.setViewVisibility(R.id.panelCalendar, View.GONE)
            views.setViewVisibility(R.id.panelCreds, View.VISIBLE)

            // App shortcuts (Tất Cả APP)
            val favs = WidgetAppIcons.favoritesOrDefaults(context)
            val favIds = listOf(R.id.imgFav0, R.id.imgFav1, R.id.imgFav2, R.id.imgFav3)
            val favTiles = listOf(TILE_FAV0, TILE_FAV1, TILE_FAV2, TILE_FAV3)
            favIds.forEachIndexed { i, id ->
                val pkg = favs.getOrNull(i)
                if (pkg != null) {
                    val bmp = WidgetAppIcons.iconBitmap(context, pkg, dp(40))
                    if (bmp != null) {
                        views.setImageViewBitmap(id, bmp)
                        views.setViewVisibility(id, View.VISIBLE)
                    } else {
                        views.setViewVisibility(id, View.INVISIBLE)
                    }
                    bindTile(context, views, widgetId, id, favTiles[i])
                } else {
                    views.setViewVisibility(id, View.INVISIBLE)
                }
            }
            bindTile(context, views, widgetId, R.id.btnTileApps, TILE_APPS)

            // Khoang tròn → Cài đặt Data / Sóng hệ thống
            bindTile(context, views, widgetId, R.id.panelData, TILE_DATA)
            bindTile(context, views, widgetId, R.id.imgDataMeter, TILE_DATA)
            bindTile(context, views, widgetId, R.id.lblData, TILE_DATA)
            bindTile(context, views, widgetId, R.id.txtWidgetData, TILE_DATA)
            bindTile(context, views, widgetId, R.id.panelSignal, TILE_SIGNAL)
            bindTile(context, views, widgetId, R.id.imgSignalMeter, TILE_SIGNAL)
            bindTile(context, views, widgetId, R.id.lblSignal, TILE_SIGNAL)
            bindTile(context, views, widgetId, R.id.txtWidgetSignal, TILE_SIGNAL)
            bindTile(context, views, widgetId, R.id.imgSignalBars, TILE_SIGNAL)
            bindTile(context, views, widgetId, R.id.txtClients, TILE_TETHER)
            bindTile(context, views, widgetId, R.id.panelClients, TILE_TETHER)
            // PendingIntent Activity trực tiếp (tránh BAL khi mở Settings từ broadcast)
            listOf(
                R.id.txtWidgetLink, R.id.txtPassHint, R.id.txtApMeta,
                R.id.txtApLabel, R.id.panelCreds
            ).forEach { id ->
                bindHotspotPassword(context, views, widgetId, id)
            }
            bindTile(context, views, widgetId, R.id.btnHotspotToggle, TILE_HOTSPOT)
            bindTile(context, views, widgetId, R.id.btnThemeToggle, TILE_DARK)
            // Không gán click root (tránh nuốt tap Data → mở nhầm Wi‑Fi/tether)
            bindTile(context, views, widgetId, R.id.txtWidgetTime, TILE_SETTINGS)

            mgr.updateAppWidget(widgetId, views)
        }

        private fun bindTile(
            context: Context,
            views: RemoteViews,
            widgetId: Int,
            viewId: Int,
            tile: String
        ) {
            val intent = Intent(context, MobileWifiWidgetProvider::class.java)
                .setAction(ACTION_TILE)
                .putExtra(EXTRA_TILE, tile)
            val pi = PendingIntent.getBroadcast(
                context,
                widgetId * 31 + viewId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(viewId, pi)
        }

        private fun bindHotspotPassword(
            context: Context,
            views: RemoteViews,
            widgetId: Int,
            viewId: Int
        ) {
            val intent = resolveHotspotPasswordIntent(context)
                ?: Intent("android.settings.TETHER_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val opts = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 26) {
                opts.launchDisplayId = ScreenModes.COVER_DISPLAY
            }
            val pi = PendingIntent.getActivity(
                context,
                widgetId * 31 + viewId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                opts.toBundle()
            )
            views.setOnClickPendingIntent(viewId, pi)
        }

        private fun resolveHotspotPasswordIntent(context: Context): Intent? {
            val pm = context.packageManager
            val candidates = listOf(
                Intent().setClassName(
                    "com.android.settings",
                    "com.android.settings.Settings\$WifiApSettingsActivity"
                ),
                Intent().setClassName(
                    "com.android.settings",
                    "com.samsung.android.settings.wifi.mobileap.WifiApSettingsActivity"
                ),
                Intent().setClassName(
                    "com.android.settings",
                    "com.samsung.android.settings.wifi.mobileap.WifiApEditSettings"
                ),
                Intent().setClassName(
                    "com.android.settings",
                    "com.android.settings.wifi.mobileap.WifiApSettings"
                ),
                Intent("android.settings.WIFI_AP_SETTINGS"),
                Intent("com.android.settings.WIFI_AP_SETTINGS"),
                Intent("android.settings.TETHER_SETTINGS"),
                Intent().setClassName(
                    "com.android.settings",
                    "com.android.settings.Settings\$SecTetherSettingsActivity"
                )
            )
            for (raw in candidates) {
                val intent = Intent(raw)
                try {
                    if (intent.resolveActivity(pm) != null) return intent
                } catch (_: Exception) {
                }
            }
            return candidates.firstOrNull()
        }

        private fun resolveDark(context: Context): Boolean {
            if (Prefs.widgetAutoDark(context)) {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                return hour >= 19 || hour < 6
            }
            return Prefs.widgetDark(context)
        }

        private fun applyTheme(views: RemoteViews, dark: Boolean) {
            val bg = if (dark) R.drawable.bg_scr01_dark else R.drawable.bg_scr01_light
            val tile = if (dark) R.drawable.bg_scr01_tile_dark else R.drawable.bg_scr01_tile
            val chip = if (dark) R.drawable.bg_scr01_chip_dark else R.drawable.bg_scr01_chip
            // One UI glass: chữ đậm hơn trên nền bán trong suốt
            val primary = if (dark) Color.parseColor("#F8FAFC") else Color.parseColor("#0F172A")
            val muted = if (dark) Color.parseColor("#A8B3C7") else Color.parseColor("#5B6B7C")
            val link = if (dark) Color.parseColor("#C5D0E0") else Color.parseColor("#3D4F63")
            val accent = if (dark) Color.parseColor("#8EC5FF") else Color.parseColor("#0057D9")

            views.setInt(R.id.widgetRoot, "setBackgroundResource", bg)
            listOf(
                R.id.panelCreds, R.id.panelData, R.id.panelSignal,
                R.id.panelAppShortcuts
            ).forEach { views.setInt(it, "setBackgroundResource", tile) }
            views.setInt(R.id.txtWidgetNetwork, "setBackgroundResource", chip)
            views.setInt(R.id.btnThemeToggle, "setBackgroundResource", chip)
            views.setInt(R.id.panelClients, "setBackgroundResource", chip)
            views.setInt(R.id.btnTileApps, "setBackgroundResource", chip)
            views.setInt(R.id.btnHotspotToggle, "setBackgroundResource", chip)
            views.setTextColor(R.id.btnTileApps, accent)

            views.setTextColor(R.id.txtWidgetTime, primary)
            views.setTextColor(R.id.txtWidgetNetwork, accent)
            views.setTextColor(R.id.txtApLabel, muted)
            views.setTextColor(R.id.txtWidgetLink, primary)
            views.setTextColor(R.id.txtApMeta, link)
            views.setTextColor(R.id.txtPassHint, accent)
            views.setTextColor(R.id.lblData, muted)
            views.setTextColor(R.id.lblSignal, muted)
            views.setTextColor(R.id.txtWidgetData, primary)
            views.setTextColor(R.id.txtWidgetSignal, primary)
            views.setTextColor(R.id.txtClients, accent)
        }

        private fun calendarLine(context: Context): String {
            val now = Date()
            val date = DateFormat.getMediumDateFormat(context).format(now)
            val dow = DateFormat.format("EEEE", now).toString()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            return "$dow · $date"
        }

        /** Gọn: CN 22/11 */
        private fun compactDate(context: Context, now: Date): String {
            val dow = DateFormat.format("EE", now).toString()
                .replace(".", "")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val dm = DateFormat.format("d/M", now).toString()
            return "$dow $dm"
        }

        /** % meter theo data SIM (trần tham chiếu 15GB). */
        private fun dataMeterPercent(bytes: Long): Int {
            val mb = bytes / (1024.0 * 1024.0)
            val limitMb = 15.0 * 1024.0
            return ((mb / limitMb) * 100).toInt().coerceIn(0, 100)
        }
    }
}

object WifiManagerCompat {
    const val NETWORK_ACTION = "android.net.conn.CONNECTIVITY_CHANGE"
}
