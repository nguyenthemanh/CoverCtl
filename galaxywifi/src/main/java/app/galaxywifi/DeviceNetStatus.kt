package app.galaxywifi

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
import java.io.File
import java.util.Locale

/**
 * Snapshot kiểu Galaxy 5G Mobile Wi‑Fi (SCR01) = **router / hotspot**:
 * SSID là AP người khác kết nối vào, không phải Wi‑Fi máy đang bắt.
 * Chip mạng = đường lên di động (5G/LTE).
 */
data class DeviceNetStatus(
    val batteryPercent: Int,
    val charging: Boolean,
    /** Đường lên: 5G / LTE / … */
    val uplinkLabel: String,
    val signalLevel: Int, // 0..4 — sóng di động (uplink)
    val hotspotOn: Boolean,
    /** SSID hotspot (AP) — tên mạng phát ra */
    val apSsid: String,
    /** Mật khẩu hotspot nếu đọc được */
    val apPassword: String = "",
    /** 2.4GHz / 5GHz nếu đọc được */
    val apBand: String,
    /** Byte data di động (SIM) đã dùng — nguồn phát hotspot */
    val mobileDataMb: Long,
    val connectedClients: Int,
    val maxClients: Int = 11,
    /** Wi‑Fi client của máy (chỉ tham khảo, không hiện làm SSID) */
    val phoneWifiSsid: String = ""
) {
    val networkLabel: String get() = uplinkLabel
    val hotspotSsid: String get() = apSsid
    val wifiConnected: Boolean get() = phoneWifiSsid.isNotBlank()
    val wifiSsid: String get() = phoneWifiSsid

    val batteryText: String get() = if (charging) "$batteryPercent% ⚡" else "$batteryPercent%"
    val signalBars: String get() =
        "▮".repeat(signalLevel.coerceIn(0, 4)) + "▯".repeat((4 - signalLevel).coerceIn(0, 4))
    val dataText: String get() = formatMb(mobileDataMb)
    val clientsText: String get() = "$connectedClients/$maxClients"

    /** Dòng SSID chính trên widget = tên AP. */
    val linkText: String
        get() = when {
            hotspotOn && apSsid.isNotBlank() -> apSsid
            hotspotOn -> "Hotspot"
            apSsid.isNotBlank() -> apSsid
            else -> ""
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
            val cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

            val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val phoneWifi = wifi.connectionInfo?.ssid
                ?.trim('"')
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                .orEmpty()

            val hotspotOn = isHotspotEnabled(wifi)
            val ap = HotspotConfigReader.read(app)
            val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // SCR01: luôn ưu tiên sóng / thế hệ di động (đường lên cấp cho hotspot)
            val uplink = cellularGeneration(tm, caps).let { gen ->
                when {
                    gen != "—" && gen != "Mobile" -> gen
                    cellular -> "Mobile"
                    hotspotOn -> "5G"
                    else -> "Offline"
                }
            }

            val signalLevel = try {
                if (Build.VERSION.SDK_INT >= 28) {
                    tm.signalStrength?.level?.coerceIn(0, 4) ?: if (cellular || hotspotOn) 2 else 0
                } else if (cellular || hotspotOn) 2 else 0
            } catch (_: SecurityException) {
                if (cellular || hotspotOn) 2 else 0
            }

            // Data SIM / di động (uplink hotspot) — không gồm Wi‑Fi client
            val mobileBytes = try {
                val rx = TrafficStats.getMobileRxBytes()
                val tx = TrafficStats.getMobileTxBytes()
                if (rx < 0L || tx < 0L) 0L else rx + tx
            } catch (_: Exception) {
                0L
            }

            val clients = if (hotspotOn) {
                readConnectedClients(wifi).coerceAtLeast(countClientsFromArp())
            } else 0

            return DeviceNetStatus(
                batteryPercent = battery,
                charging = charging,
                uplinkLabel = uplink,
                signalLevel = signalLevel,
                hotspotOn = hotspotOn,
                apSsid = ap.ssid,
                apPassword = ap.password,
                apBand = ap.band,
                mobileDataMb = mobileBytes,
                connectedClients = clients.coerceIn(0, 11),
                phoneWifiSsid = phoneWifi
            )
        }

        private fun readConnectedClients(wifi: WifiManager): Int {
            val methods = listOf(
                "getWifiApStaList",
                "getConnectedStations",
                "getSoftApConnectedClients"
            )
            for (name in methods) {
                try {
                    val m = wifi.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
                        ?: continue
                    m.isAccessible = true
                    when (val raw = m.invoke(wifi)) {
                        is Int -> return raw
                        is Collection<*> -> return raw.size
                        is Array<*> -> return raw.size
                        is Map<*, *> -> return raw.size
                    }
                } catch (_: Exception) {
                }
            }
            return 0
        }

        /** Ước lượng client từ ARP (thiết bị trên mạng hotspot). */
        private fun countClientsFromArp(): Int {
            return try {
                val f = File("/proc/net/arp")
                if (!f.canRead()) return 0
                f.readLines()
                    .drop(1)
                    .count { line ->
                        val p = line.trim().split(Regex("\\s+"))
                        p.size >= 4 && p[3] != "00:00:00:00:00:00" && p[5] != "*"
                    }
                    .coerceAtMost(11)
            } catch (_: Exception) {
                0
            }
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
                try {
                    val state = wifi.javaClass.getDeclaredMethod("getWifiApState").invoke(wifi) as Int
                    // WIFI_AP_STATE_ENABLED = 13
                    state == 13
                } catch (_: Exception) {
                    false
                }
            }
    }
}
