package app.galaxywifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.lang.reflect.Proxy

/** Bật/tắt điểm phát sóng (chia sẻ Internet). App thường bị hạn chế — thử nhiều API Samsung/AOSP. */
object HotspotToggle {
    fun isEnabled(context: Context): Boolean {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return try {
            val m = wifi.javaClass.getDeclaredMethod("isWifiApEnabled")
            m.isAccessible = true
            m.invoke(wifi) as Boolean
        } catch (_: Exception) {
            try {
                val state = wifi.javaClass.getDeclaredMethod("getWifiApState").invoke(wifi) as Int
                state == 13 // WIFI_AP_STATE_ENABLED
            } catch (_: Exception) {
                false
            }
        }
    }

    /** @return true nếu đã gửi lệnh bật/tắt (không chắc OS chấp nhận). */
    fun toggle(context: Context): Boolean {
        val app = context.applicationContext
        val wantOn = !isEnabled(app)
        return if (wantOn) turnOn(app) else turnOff(app)
    }

    fun turnOn(context: Context): Boolean =
        setEnabled(context, true)

    fun turnOff(context: Context): Boolean =
        setEnabled(context, false)

    @SuppressLint("MissingPermission")
    private fun setEnabled(context: Context, enable: Boolean): Boolean {
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (trySemWifi(context, wifi, enable)) return true
        if (tryConnectivityTether(cm, enable)) return true
        if (tryWifiApEnabled(wifi, enable)) return true
        if (tryTetheringManager(context, enable)) return true
        return false
    }

    private fun trySemWifi(context: Context, wifi: WifiManager, enable: Boolean): Boolean {
        try {
            val semCls = Class.forName("com.samsung.android.net.wifi.SemWifiManager")
            val sem = context.getSystemService(semCls) ?: context.getSystemService("sem_wifi")
            if (sem != null) {
                for (m in sem.javaClass.methods) {
                    if (!m.name.equals("setWifiApEnabled", ignoreCase = true)) continue
                    try {
                        when (m.parameterCount) {
                            2 -> {
                                m.invoke(sem, null, enable)
                                return true
                            }
                            1 -> {
                                m.invoke(sem, enable)
                                return true
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        // Một số bản Sem nằm trên WifiManager
        try {
            for (m in wifi.javaClass.methods) {
                if (!m.name.contains("WifiApEnabled", ignoreCase = true)) continue
                if (m.parameterCount == 1 && m.parameterTypes[0] == Boolean::class.javaPrimitiveType) {
                    m.invoke(wifi, enable)
                    return true
                }
                if (m.parameterCount == 2) {
                    m.invoke(wifi, null, enable)
                    return true
                }
            }
        } catch (_: Exception) {
        }
        return false
    }

    private fun tryWifiApEnabled(wifi: WifiManager, enable: Boolean): Boolean {
        return try {
            val m = wifi.javaClass.getMethod(
                "setWifiApEnabled",
                Class.forName("android.net.wifi.WifiConfiguration"),
                Boolean::class.javaPrimitiveType
            )
            m.invoke(wifi, null, enable)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryConnectivityTether(cm: ConnectivityManager, enable: Boolean): Boolean {
        return try {
            if (enable) {
                val callbackClass = Class.forName(
                    "android.net.ConnectivityManager\$OnStartTetheringCallback"
                )
                val callback = Proxy.newProxyInstance(
                    callbackClass.classLoader,
                    arrayOf(callbackClass)
                ) { _, _, _ -> null }
                val start = cm.javaClass.getDeclaredMethod(
                    "startTethering",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    callbackClass,
                    Handler::class.java
                )
                start.isAccessible = true
                start.invoke(cm, 0 /* TETHERING_WIFI */, false, callback, Handler(Looper.getMainLooper()))
                true
            } else {
                val stop = cm.javaClass.getDeclaredMethod(
                    "stopTethering",
                    Int::class.javaPrimitiveType
                )
                stop.isAccessible = true
                stop.invoke(cm, 0)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun tryTetheringManager(context: Context, enable: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        return try {
            val tmClass = Class.forName("android.net.TetheringManager")
            val tm = context.getSystemService(tmClass) ?: return false
            if (enable) {
                // startTethering(TetheringRequest, Executor, StartTetheringCallback) — phức tạp; thử overload cũ
                for (m in tmClass.methods) {
                    if (m.name != "startTethering") continue
                    if (m.parameterCount == 1 && m.parameterTypes[0] == Int::class.javaPrimitiveType) {
                        m.invoke(tm, 0)
                        return true
                    }
                }
            } else {
                val stop = tmClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
                stop.invoke(tm, 0)
                return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }
}
