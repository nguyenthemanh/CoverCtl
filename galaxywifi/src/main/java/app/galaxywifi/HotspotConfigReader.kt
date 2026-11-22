package app.galaxywifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** Đọc SSID / mật khẩu / băng tần hotspot AP khớp cấu hình hệ thống. */
object HotspotConfigReader {
    data class ApInfo(val ssid: String, val password: String, val band: String)

    @SuppressLint("MissingPermission")
    fun read(context: Context): ApInfo {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val fromSoft = readSoftAp(wifi)
        val fromLegacy = readLegacyAp(wifi)
        val fromSem = readSemWifi(context, wifi)
        val fromSettings = readSamsungSettings(context)
        // Samsung: SoftAp thường khóa với app thường; SSID hotspot = tên thiết bị
        val fromDeviceName = ApInfo(readDeviceName(context), "", "")
        val fromPrefs = ApInfo(
            Prefs.apSsidOverride(context),
            Prefs.apPasswordOverride(context),
            ""
        )

        // SoftAp/Sem trước; nếu trống dùng device_name (khớp Cài đặt hệ thống trên Flip)
        val ssid = firstGood(
            fromSoft.ssid, fromLegacy.ssid, fromSem.ssid, fromSettings.ssid,
            fromDeviceName.ssid, fromPrefs.ssid
        )
        val pass = firstGood(
            fromSoft.password, fromLegacy.password, fromSem.password, fromSettings.password, fromPrefs.password
        )
        val band = firstGood(fromSoft.band, fromLegacy.band, fromSem.band, fromSettings.band)
        return ApInfo(ssid, pass, band)
    }

    private fun readDeviceName(context: Context): String {
        val cr = context.contentResolver
        val candidates = mutableListOf<String>()
        try {
            if (Build.VERSION.SDK_INT >= 25) {
                candidates += Settings.Global.getString(cr, Settings.Global.DEVICE_NAME).orEmpty()
            }
        } catch (_: Exception) {
        }
        for (key in listOf("device_name", "wifi_p2p_device_name", "bluetooth_name")) {
            try {
                candidates += Settings.Global.getString(cr, key).orEmpty()
            } catch (_: Exception) {
            }
            try {
                candidates += Settings.Secure.getString(cr, key).orEmpty()
            } catch (_: Exception) {
            }
            try {
                candidates += Settings.System.getString(cr, key).orEmpty()
            } catch (_: Exception) {
            }
        }
        try {
            candidates += Settings.Secure.getString(cr, "bluetooth_name").orEmpty()
        } catch (_: Exception) {
        }
        return firstGood(*candidates.toTypedArray())
    }

    private fun firstGood(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() && it != "<unknown ssid>" && !it.equals("null", true) }
            .orEmpty()

    private fun readSoftAp(wifi: WifiManager): ApInfo {
        if (Build.VERSION.SDK_INT < 30) return ApInfo("", "", "")
        return try {
            val cfg = wifi.javaClass.getMethod("getSoftApConfiguration").invoke(wifi)
                ?: return ApInfo("", "", "")
            ApInfo(extractSsid(cfg), invokeString(cfg, "getPassphrase"), readBands(cfg))
        } catch (_: SecurityException) {
            ApInfo("", "", "")
        } catch (_: Exception) {
            ApInfo("", "", "")
        }
    }

    private fun extractSsid(cfg: Any): String {
        // API 33+: WifiSsid — hỗ trợ UTF-8 (tên tiếng Việt)
        try {
            val wifiSsid = cfg.javaClass.getMethod("getWifiSsid").invoke(cfg)
            if (wifiSsid != null) {
                val fromBytes = wifiSsidBytes(wifiSsid)
                if (fromPrefsGood(fromBytes)) return fromBytes
                val s = cleanSsid(wifiSsid.toString())
                if (fromPrefsGood(s)) return s
            }
        } catch (_: Exception) {
        }
        return cleanSsid(invokeString(cfg, "getSsid"))
    }

    private fun wifiSsidBytes(wifiSsid: Any): String {
        val byteGetters = listOf("getBytes", "getByteArray", "bytes")
        for (name in byteGetters) {
            try {
                val m = wifiSsid.javaClass.methods.firstOrNull {
                    it.name == name && it.parameterCount == 0
                } ?: continue
                val raw = m.invoke(wifiSsid) ?: continue
                val bytes = when (raw) {
                    is ByteArray -> raw
                    else -> continue
                }
                // Bỏ trailing 0
                val end = bytes.indexOfFirst { it == 0.toByte() }.let { if (it < 0) bytes.size else it }
                val slice = bytes.copyOfRange(0, end)
                for (cs in listOf(StandardCharsets.UTF_8, Charset.forName("UTF-8"))) {
                    val s = String(slice, cs).trim().trim('"')
                    if (fromPrefsGood(s)) return s
                }
            } catch (_: Exception) {
            }
        }
        // Field mBytes / mUtf8Bytes
        for (fieldName in listOf("mBytes", "mUtf8Text", "mSsid", "bytes")) {
            try {
                val f = wifiSsid.javaClass.getDeclaredField(fieldName)
                f.isAccessible = true
                when (val v = f.get(wifiSsid)) {
                    is ByteArray -> {
                        val s = String(v, StandardCharsets.UTF_8).trim().trim('\u0000').trim('"')
                        if (fromPrefsGood(s)) return s
                    }
                    is String -> {
                        val s = cleanSsid(v)
                        if (fromPrefsGood(s)) return s
                    }
                }
            } catch (_: Exception) {
            }
        }
        return ""
    }

    private fun fromPrefsGood(s: String) =
        s.isNotBlank() && s != "<unknown ssid>" && !s.startsWith("WifiSsid")

    private fun cleanSsid(raw: String): String {
        var s = raw.trim()
        s = s.removePrefix("WifiSsid:").trim()
        s = s.removePrefix("SSID:").trim()
        if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
            s = s.substring(1, s.length - 1)
        }
        return s.trim()
    }

    private fun readLegacyAp(wifi: WifiManager): ApInfo {
        return try {
            val m = wifi.javaClass.methods.firstOrNull {
                it.name == "getWifiApConfiguration" && it.parameterCount == 0
            } ?: wifi.javaClass.getDeclaredMethod("getWifiApConfiguration").also { it.isAccessible = true }
            val cfg = m.invoke(wifi) ?: return ApInfo("", "", "")
            val ssid = cleanSsid(fieldString(cfg, "SSID"))
            val pass = fieldString(cfg, "preSharedKey").trim('"')
            ApInfo(ssid, pass, "")
        } catch (_: Exception) {
            ApInfo("", "", "")
        }
    }

    private fun readSemWifi(context: Context, wifi: WifiManager): ApInfo {
        try {
            for (m in wifi.javaClass.methods) {
                if (m.parameterCount != 0) continue
                if (!m.name.contains("WifiApConfiguration", ignoreCase = true) &&
                    !m.name.contains("SoftApConfiguration", ignoreCase = true)
                ) continue
                try {
                    val cfg = m.invoke(wifi) ?: continue
                    val ssid = cleanSsid(
                        fieldString(cfg, "SSID").ifBlank { invokeString(cfg, "getSsid") }
                            .ifBlank { extractSsid(cfg) }
                    )
                    val pass = fieldString(cfg, "preSharedKey")
                        .ifBlank { invokeString(cfg, "getPassphrase") }
                    if (ssid.isNotBlank() || pass.isNotBlank()) return ApInfo(ssid, pass, readBands(cfg))
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        try {
            val semCls = Class.forName("com.samsung.android.net.wifi.SemWifiManager")
            val sem = context.getSystemService(semCls) ?: run {
                // Một số máy: "sem_wifi" / "wifi"
                context.getSystemService("sem_wifi")
            } ?: return ApInfo("", "", "")
            for (m in sem.javaClass.methods) {
                if (m.parameterCount != 0) continue
                if (!m.name.contains("ApConfig", ignoreCase = true) &&
                    !m.name.contains("WifiAp", ignoreCase = true) &&
                    !m.name.contains("SoftAp", ignoreCase = true)
                ) continue
                try {
                    val cfg = m.invoke(sem) ?: continue
                    val ssid = cleanSsid(
                        fieldString(cfg, "SSID").ifBlank { invokeString(cfg, "getSsid") }
                            .ifBlank { extractSsid(cfg) }
                    )
                    val pass = fieldString(cfg, "preSharedKey")
                        .ifBlank { invokeString(cfg, "getPassphrase") }
                    if (ssid.isNotBlank() || pass.isNotBlank()) return ApInfo(ssid, pass, "")
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        return ApInfo("", "", "")
    }

    private fun readSamsungSettings(context: Context): ApInfo {
        val cr = context.contentResolver
        fun get(table: (android.content.ContentResolver, String) -> String?, key: String) =
            try {
                cleanSsid(table(cr, key).orEmpty())
            } catch (_: Exception) {
                ""
            }

        val ssidKeys = listOf(
            "wifi_ap_ssid", "soft_ap_ssid", "wifi_ap_name", "tether_ssid",
            "samsung_wifi_ap_ssid", "wifi_ap_last_ssid"
        )
        val passKeys = listOf(
            "wifi_ap_passwd", "wifi_ap_password", "soft_ap_passwd",
            "tether_password", "samsung_wifi_ap_passwd", "wifi_ap_last_passwd"
        )
        var ssid = ""
        var pass = ""
        for (k in ssidKeys) {
            ssid = get(Settings.System::getString, k)
                .ifBlank { get(Settings.Global::getString, k) }
                .ifBlank { get(Settings.Secure::getString, k) }
            if (ssid.isNotBlank()) break
        }
        for (k in passKeys) {
            pass = get(Settings.System::getString, k)
                .ifBlank { get(Settings.Global::getString, k) }
                .ifBlank { get(Settings.Secure::getString, k) }
            if (pass.isNotBlank()) break
        }
        return ApInfo(ssid, pass, "")
    }

    private fun readBands(cfg: Any): String {
        return try {
            val arr = cfg.javaClass.getMethod("getBands").invoke(cfg) as? IntArray
            bandLabel(arr?.toList().orEmpty())
        } catch (_: Exception) {
            try {
                val b = cfg.javaClass.getMethod("getBand").invoke(cfg) as? Int
                bandLabel(listOfNotNull(b))
            } catch (_: Exception) {
                ""
            }
        }
    }

    private fun bandLabel(bands: List<Int>): String {
        val has2 = bands.any { it and 1 != 0 || it == 1 }
        val has5 = bands.any { it and 2 != 0 || it == 2 }
        return when {
            has2 && has5 -> "2.4 + 5 GHz"
            has5 -> "5 GHz"
            has2 -> "2.4 GHz"
            else -> ""
        }
    }

    private fun invokeString(obj: Any, method: String): String =
        try {
            (obj.javaClass.getMethod(method).invoke(obj) as? String)?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }

    private fun fieldString(obj: Any, name: String): String =
        try {
            val f = obj.javaClass.getDeclaredField(name)
            f.isAccessible = true
            (f.get(obj) as? String)?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }
}
