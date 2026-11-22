package app.galaxywifi

import android.content.Context

object Prefs {
    private const val NAME = "galaxywifi"
    private const val KEY_WIDGET_DARK = "widget_dark"
    private const val KEY_WIDGET_AUTO_DARK = "widget_auto_dark"
    private const val KEY_WIDGET_CALENDAR = "widget_calendar"
    private const val KEY_WIDGET_CAL_CHARGING = "widget_cal_charging"
    private const val KEY_WIDGET_SIGNAL = "widget_signal"
    private const val KEY_WIDGET_DATA = "widget_data"
    private const val KEY_WIDGET_METERS = "widget_meters"
    private const val KEY_FAV_APPS = "favorite_apps"
    private const val KEY_AP_SSID = "ap_ssid_override"
    private const val KEY_AP_PASS = "ap_password_override"
    private const val KEY_LANGUAGE = "language_tag"
    private const val KEY_WIDGET_SCALE = "widget_scale_pct"
    private const val KEY_WIDGET_OX = "widget_offset_x"
    private const val KEY_WIDGET_OY = "widget_offset_y"

    private fun sp(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun languageTag(context: Context): String =
        sp(context).getString(KEY_LANGUAGE, LocaleHelper.SYSTEM).orEmpty().ifBlank { LocaleHelper.SYSTEM }

    fun setLanguageTag(context: Context, tag: String) {
        sp(context).edit().putString(KEY_LANGUAGE, tag).apply()
    }

    /** 70..130 (%), mặc định 100 */
    fun widgetScalePct(context: Context): Int =
        sp(context).getInt(KEY_WIDGET_SCALE, 100).coerceIn(70, 130)

    fun setWidgetScalePct(context: Context, pct: Int) {
        sp(context).edit().putInt(KEY_WIDGET_SCALE, pct.coerceIn(70, 130)).apply()
    }

    /** -40..40 dp */
    fun widgetOffsetX(context: Context): Int =
        sp(context).getInt(KEY_WIDGET_OX, 0).coerceIn(-40, 40)

    fun setWidgetOffsetX(context: Context, dp: Int) {
        sp(context).edit().putInt(KEY_WIDGET_OX, dp.coerceIn(-40, 40)).apply()
    }

    fun widgetOffsetY(context: Context): Int =
        sp(context).getInt(KEY_WIDGET_OY, 0).coerceIn(-40, 40)

    fun setWidgetOffsetY(context: Context, dp: Int) {
        sp(context).edit().putInt(KEY_WIDGET_OY, dp.coerceIn(-40, 40)).apply()
    }

    fun resetWidgetLayout(context: Context) {
        sp(context).edit()
            .putInt(KEY_WIDGET_SCALE, 100)
            .putInt(KEY_WIDGET_OX, 0)
            .putInt(KEY_WIDGET_OY, 0)
            .apply()
    }

    fun widgetDark(context: Context): Boolean = sp(context).getBoolean(KEY_WIDGET_DARK, false)
    fun setWidgetDark(context: Context, dark: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_DARK, dark).apply()
    }

    fun widgetAutoDark(context: Context): Boolean = sp(context).getBoolean(KEY_WIDGET_AUTO_DARK, false)
    fun setWidgetAutoDark(context: Context, auto: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_AUTO_DARK, auto).apply()
    }

    fun widgetCalendar(context: Context): Boolean = sp(context).getBoolean(KEY_WIDGET_CALENDAR, true)
    fun setWidgetCalendar(context: Context, show: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_CALENDAR, show).apply()
    }

    fun widgetCalendarWhenCharging(context: Context): Boolean =
        sp(context).getBoolean(KEY_WIDGET_CAL_CHARGING, false)

    fun setWidgetCalendarWhenCharging(context: Context, onlyCharging: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_CAL_CHARGING, onlyCharging).apply()
    }

    fun widgetShowSignal(context: Context): Boolean = sp(context).getBoolean(KEY_WIDGET_SIGNAL, true)
    fun setWidgetShowSignal(context: Context, show: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_SIGNAL, show).apply()
    }

    fun widgetShowData(context: Context): Boolean = sp(context).getBoolean(KEY_WIDGET_DATA, true)
    fun setWidgetShowData(context: Context, show: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_DATA, show).apply()
    }

    fun widgetShowMeters(context: Context): Boolean = sp(context).getBoolean(KEY_WIDGET_METERS, true)
    fun setWidgetShowMeters(context: Context, show: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_METERS, show).apply()
    }

    /** 4 ô ưu tiên trên widget (có thể rỗng). */
    fun favoriteSlots(context: Context): List<String> {
        val raw = sp(context).getString(KEY_FAV_APPS, "").orEmpty()
        val parts = raw.split(',').map { it.trim() }
        return List(4) { i -> parts.getOrNull(i).orEmpty() }
    }

    fun favoriteApps(context: Context): List<String> =
        favoriteSlots(context).filter { it.isNotEmpty() }.distinct()

    fun setFavoriteApps(context: Context, packages: List<String>) {
        val slots = List(4) { i -> packages.getOrNull(i).orEmpty() }
        sp(context).edit().putString(KEY_FAV_APPS, slots.joinToString(",")).apply()
    }

    fun setFavoriteAt(context: Context, index: Int, packageName: String) {
        if (index !in 0..3) return
        val slots = favoriteSlots(context).toMutableList()
        // Tránh trùng: gỡ package khỏi ô khác
        for (i in slots.indices) {
            if (i != index && slots[i] == packageName) slots[i] = ""
        }
        slots[index] = packageName
        setFavoriteApps(context, slots)
    }

    fun clearFavoriteAt(context: Context, index: Int) {
        if (index !in 0..3) return
        val slots = favoriteSlots(context).toMutableList()
        slots[index] = ""
        setFavoriteApps(context, slots)
    }

    fun apSsidOverride(context: Context): String =
        sp(context).getString(KEY_AP_SSID, "").orEmpty()

    fun setApSsidOverride(context: Context, ssid: String) {
        sp(context).edit().putString(KEY_AP_SSID, ssid.trim()).apply()
    }

    fun apPasswordOverride(context: Context): String =
        sp(context).getString(KEY_AP_PASS, "").orEmpty()

    fun setApPasswordOverride(context: Context, password: String) {
        sp(context).edit().putString(KEY_AP_PASS, password).apply()
    }

    fun addFavoriteApp(context: Context, packageName: String) {
        val slots = favoriteSlots(context).toMutableList()
        val existing = slots.indexOf(packageName)
        if (existing >= 0) {
            // Đưa lên ô đầu
            slots.removeAt(existing)
            slots.add(0, packageName)
            while (slots.size < 4) slots.add("")
            setFavoriteApps(context, slots.take(4))
            return
        }
        val empty = slots.indexOfFirst { it.isEmpty() }
        if (empty >= 0) {
            slots[empty] = packageName
        } else {
            slots.add(0, packageName)
            slots.removeLast()
        }
        setFavoriteApps(context, slots.take(4))
    }
}
