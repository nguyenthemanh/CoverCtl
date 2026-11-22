package app.galaxywifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/** Bật/tắt dữ liệu di động (SIM). App thường bị hạn chế — thử API rồi fallback Cài đặt. */
object MobileDataToggle {
    fun isEnabled(context: Context): Boolean {
        val app = context.applicationContext
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                if (tm.isDataEnabled) return true
            }
        } catch (_: Exception) {
        }
        try {
            val v = Settings.Global.getInt(app.contentResolver, "mobile_data", -1)
            if (v >= 0) return v == 1
        } catch (_: Exception) {
        }
        try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            val ni = cm.activeNetworkInfo
            if (ni != null && ni.type == ConnectivityManager.TYPE_MOBILE && ni.isConnected) {
                return true
            }
        } catch (_: Exception) {
        }
        return false
    }

    fun toggle(context: Context): Boolean {
        val app = context.applicationContext
        return setEnabled(app, !isEnabled(app))
    }

    @SuppressLint("MissingPermission")
    fun setEnabled(context: Context, enable: Boolean): Boolean {
        val app = context.applicationContext
        if (tryTelephonySetData(app, enable)) return true
        if (tryConnectivityReflection(app, enable)) return true
        if (tryITelephony(app, enable)) return true
        if (trySettingsGlobal(app, enable)) return true
        return false
    }

    private fun tryTelephonySetData(context: Context, enable: Boolean): Boolean {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val subId = try {
                SubscriptionManager.getDefaultDataSubscriptionId()
            } catch (_: Exception) {
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            }
            val bound = if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
                Build.VERSION.SDK_INT >= 24
            ) {
                tm.createForSubscriptionId(subId)
            } else tm

            // API 26+: setDataEnabled
            try {
                val m = bound.javaClass.getMethod("setDataEnabled", Boolean::class.javaPrimitiveType)
                m.invoke(bound, enable)
                return true
            } catch (_: Exception) {
            }
            // Một số máy: setDataEnabledForReason
            try {
                val m = bound.javaClass.getMethod(
                    "setDataEnabledForReason",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
                m.invoke(bound, 0, enable)
                return true
            } catch (_: Exception) {
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun tryConnectivityReflection(context: Context, enable: Boolean): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val m = cm.javaClass.getDeclaredMethod(
                "setMobileDataEnabled",
                Boolean::class.javaPrimitiveType
            )
            m.isAccessible = true
            m.invoke(cm, enable)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryITelephony(context: Context, enable: Boolean): Boolean {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val getITelephony = tm.javaClass.getDeclaredMethod("getITelephony")
            getITelephony.isAccessible = true
            val iTel = getITelephony.invoke(tm) ?: return false
            for (name in listOf("setDataEnabled", "enableDataConnectivity", "disableDataConnectivity")) {
                try {
                    when (name) {
                        "setDataEnabled" -> {
                            iTel.javaClass.getMethod(name, Boolean::class.javaPrimitiveType)
                                .invoke(iTel, enable)
                            return true
                        }
                        "enableDataConnectivity" -> if (enable) {
                            iTel.javaClass.getMethod(name).invoke(iTel)
                            return true
                        }
                        "disableDataConnectivity" -> if (!enable) {
                            iTel.javaClass.getMethod(name).invoke(iTel)
                            return true
                        }
                    }
                } catch (_: Exception) {
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun trySettingsGlobal(context: Context, enable: Boolean): Boolean {
        return try {
            Settings.Global.putInt(
                context.contentResolver,
                "mobile_data",
                if (enable) 1 else 0
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
