package app.coverctl

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import java.util.Locale

/** Snapshot mạng/pin đồng bộ với máy — kiểu Galaxy 5G Mobile Wi-Fi multi-info. */
data class DeviceNetStatus(
    val batteryPercent: Int,
    val charging: Boolean,
    val networkLabel: String,
    val signalLevel: Int, // 0..4
    val wifiSsid: String,
    val hotspotOn: Boolean,
    val hotspotSsid: String,
    val mobileDataMb: Long,
    val wifiConnected: Boolean
) {
    val batteryText: String get() = if (charging) "$batteryPercent% ⚡" else "$batteryPercent%"
    val signalBars: String get() = "▮".repeat(signalLevel.coerceIn(0, 4)) + "▯".repeat((4 - signalLevel).coerceIn(0, 4))
    val dataText: String get() = formatMb(mobileDataMb)
    val linkText: String
        get() = when {
            hotspotOn -> "AP ${hotspotSsid.ifBlank { "ON" }}"
            wifiConnected -> wifiSsid.ifBlank { "Wi‑Fi" }
            else -> networkLabel
        }

    companion object {
        fun formatMb(bytes: Long): String {
            val mb = bytes / (1024.0 * 1024.0)
            return when {
                mb >= 1024 -> String.format(Locale.US, "%.1f GB", mb / 1024.0)
                else -> String.format(Locale.US, "%.0f MB", mb)
            }
        }

        @SuppressLint("MissingPermission")
        fun read(context: Context): DeviceNetStatus {
            val app = context.applicationContext
            val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
            val charging = run {
                val intent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }

            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

            val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ssid = wifi.connectionInfo?.ssid
                ?.trim('"')
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                .orEmpty()

            val hotspotOn = isHotspotEnabled(wifi)
            val hotspotSsid = if (hotspotOn) readHotspotSsid(wifi) else ""

            val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val networkLabel = when {
                wifiConnected -> "Wi‑Fi"
                cellular -> cellularGeneration(tm, caps)
                hotspotOn -> "5G AP"
                else -> "Offline"
            }

            val signalLevel = when {
                wifiConnected -> {
                    @Suppress("DEPRECATION")
                    WifiManager.calculateSignalLevel(wifi.connectionInfo.rssi, 5).coerceIn(0, 4)
                }
                cellular -> {
                    try {
                        if (Build.VERSION.SDK_INT >= 28) {
                            tm.signalStrength?.level?.coerceIn(0, 4) ?: 2
                        } else 2
                    } catch (_: SecurityException) {
                        2
                    }
                }
                else -> 0
            }

            val mobileBytes = try {
                val rx = TrafficStats.getMobileRxBytes()
                val tx = TrafficStats.getMobileTxBytes()
                if (rx < 0 || tx < 0) 0L else rx + tx
            } catch (_: Exception) {
                0L
            }

            return DeviceNetStatus(
                batteryPercent = battery,
                charging = charging,
                networkLabel = networkLabel,
                signalLevel = signalLevel,
                wifiSsid = ssid,
                hotspotOn = hotspotOn,
                hotspotSsid = hotspotSsid,
                mobileDataMb = mobileBytes,
                wifiConnected = wifiConnected
            )
        }

        @SuppressLint("MissingPermission")
        private fun cellularGeneration(tm: TelephonyManager, caps: NetworkCapabilities?): String =
            try {
                when (tm.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    TelephonyManager.NETWORK_TYPE_HSPAP,
                    TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    TelephonyManager.NETWORK_TYPE_EDGE,
                    TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                    else -> if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) "Mobile" else "—"
                }
            } catch (_: SecurityException) {
                "Mobile"
            }

        private fun isHotspotEnabled(wifi: WifiManager): Boolean =
            try {
                val m = wifi.javaClass.getDeclaredMethod("isWifiApEnabled")
                m.isAccessible = true
                m.invoke(wifi) as Boolean
            } catch (_: Exception) {
                false
            }

        private fun readHotspotSsid(wifi: WifiManager): String =
            try {
                val cfg = wifi.javaClass.getDeclaredMethod("getWifiApConfiguration").invoke(wifi)
                val field = cfg?.javaClass?.getDeclaredField("SSID")
                field?.isAccessible = true
                (field?.get(cfg) as? String).orEmpty()
            } catch (_: Exception) {
                "Hotspot"
            }
    }
}
