package app.coverctl

import android.content.Context

object Prefs {
    private const val NAME = "coverctl"
    private const val KEY_PROFILE = "device_profile"
    private const val KEY_PRIVILEGE = "privilege_mode"
    private const val KEY_AA_APPS = "aa_apps"
    private const val KEY_UNINSTALL_APPS = "uninstall_apps"
    private const val KEY_ADB_HOST = "adb_host"
    private const val KEY_ADB_PORT = "adb_port"
    private const val KEY_ADB_PAIR_PORT = "adb_pair_port"
    private const val KEY_LANGUAGE = "language_tag"
    private const val KEY_SCREEN_MODE = "screen_mode"
    private const val KEY_KEEP_DUAL = "keep_dual"
    private const val KEY_KEEP_DUAL_STATE = "keep_dual_state"
    private const val KEY_EDGE_GESTURES = "edge_gestures"
    private const val KEY_EDGE_BOTH_SIDES = "edge_both_sides"
    private const val KEY_SHOW_NAV_BAR = "show_nav_bar"
    private const val KEY_WIDGET_DARK = "widget_dark"
    private const val KEY_WIDGET_AUTO_DARK = "widget_auto_dark"
    private const val KEY_WIDGET_CALENDAR = "widget_calendar"
    private const val KEY_WIDGET_CAL_CHARGING = "widget_cal_charging"
    private const val KEY_WIDGET_SIGNAL = "widget_signal"
    private const val KEY_WIDGET_BATTERY = "widget_battery"
    private const val KEY_WIDGET_DATA = "widget_data"
    private const val KEY_WIDGET_METERS = "widget_meters"
    private const val KEY_FAV_APPS = "favorite_apps"

    private fun sp(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Tối đa 4 app ghim trên widget (kiểu Tất Cả APP). */
    fun favoriteApps(context: Context): List<String> {
        val raw = sp(context).getString(KEY_FAV_APPS, "").orEmpty()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(4)
    }

    fun setFavoriteApps(context: Context, packages: List<String>) {
        sp(context).edit()
            .putString(KEY_FAV_APPS, packages.distinct().take(4).joinToString(","))
            .apply()
    }

    fun addFavoriteApp(context: Context, packageName: String) {
        val cur = favoriteApps(context).toMutableList()
        cur.remove(packageName)
        cur.add(0, packageName)
        setFavoriteApps(context, cur)
    }

    fun widgetDark(context: Context): Boolean =
        sp(context).getBoolean(KEY_WIDGET_DARK, false)

    fun setWidgetDark(context: Context, dark: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_DARK, dark).apply()
    }

    /** Tự dark mode buổi tối (19:00–06:00), giống timer Galaxy Wi‑Fi. */
    fun widgetAutoDark(context: Context): Boolean =
        sp(context).getBoolean(KEY_WIDGET_AUTO_DARK, false)

    fun setWidgetAutoDark(context: Context, auto: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_AUTO_DARK, auto).apply()
    }

    fun widgetCalendar(context: Context): Boolean =
        sp(context).getBoolean(KEY_WIDGET_CALENDAR, true)

    fun setWidgetCalendar(context: Context, show: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_CALENDAR, show).apply()
    }

    fun widgetCalendarWhenCharging(context: Context): Boolean =
        sp(context).getBoolean(KEY_WIDGET_CAL_CHARGING, false)

    fun setWidgetCalendarWhenCharging(context: Context, onlyCharging: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_CAL_CHARGING, onlyCharging).apply()
    }

    fun widgetShowSignal(context: Context): Boolean =
        sp(context).getBoolean(KEY_WIDGET_SIGNAL, true)

    fun setWidgetShowSignal(context: Context, show: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_SIGNAL, show).apply()
    }

    fun widgetShowBattery(context: Context): Boolean =
        sp(context).getBoolean(KEY_WIDGET_BATTERY, true)

    fun setWidgetShowBattery(context: Context, show: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_BATTERY, show).apply()
    }

    fun widgetShowData(context: Context): Boolean =
        sp(context).getBoolean(KEY_WIDGET_DATA, true)

    fun setWidgetShowData(context: Context, show: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_DATA, show).apply()
    }

    fun widgetShowMeters(context: Context): Boolean =
        sp(context).getBoolean(KEY_WIDGET_METERS, true)

    fun setWidgetShowMeters(context: Context, show: Boolean) {
        sp(context).edit().putBoolean(KEY_WIDGET_METERS, show).apply()
    }

    fun edgeGestures(context: Context): Boolean =
        sp(context).getBoolean(KEY_EDGE_GESTURES, true)

    fun setEdgeGestures(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_EDGE_GESTURES, enabled).apply()
    }

    fun edgeBothSides(context: Context): Boolean =
        sp(context).getBoolean(KEY_EDGE_BOTH_SIDES, true)

    fun setEdgeBothSides(context: Context, both: Boolean) {
        sp(context).edit().putBoolean(KEY_EDGE_BOTH_SIDES, both).apply()
    }

    fun showNavBar(context: Context): Boolean =
        sp(context).getBoolean(KEY_SHOW_NAV_BAR, true)

    fun setShowNavBar(context: Context, show: Boolean) {
        sp(context).edit().putBoolean(KEY_SHOW_NAV_BAR, show).apply()
    }

    fun screenMode(context: Context): String =
        sp(context).getString(KEY_SCREEN_MODE, ScreenModes.Mode.MAIN.id) ?: ScreenModes.Mode.MAIN.id

    fun setScreenMode(context: Context, mode: String) {
        sp(context).edit().putString(KEY_SCREEN_MODE, mode).apply()
    }

    fun keepDual(context: Context): Boolean =
        sp(context).getBoolean(KEY_KEEP_DUAL, true)

    fun setKeepDual(context: Context, keep: Boolean) {
        sp(context).edit().putBoolean(KEY_KEEP_DUAL, keep).apply()
    }

    fun keepDualStateId(context: Context): Int =
        sp(context).getInt(KEY_KEEP_DUAL_STATE, 4)

    fun setKeepDualStateId(context: Context, id: Int) {
        sp(context).edit().putInt(KEY_KEEP_DUAL_STATE, id).apply()
    }

    fun languageTag(context: Context): String =
        sp(context).getString(KEY_LANGUAGE, LocaleHelper.SYSTEM) ?: LocaleHelper.SYSTEM

    fun setLanguageTag(context: Context, tag: String) {
        sp(context).edit().putString(KEY_LANGUAGE, tag).apply()
    }

    fun profileId(context: Context): String =
        sp(context).getString(KEY_PROFILE, DeviceProfile.AUTO.id) ?: DeviceProfile.AUTO.id

    fun setProfileId(context: Context, id: String) {
        sp(context).edit().putString(KEY_PROFILE, id).apply()
    }

    fun privilegeMode(context: Context): PrivilegeMode =
        PrivilegeMode.fromId(sp(context).getString(KEY_PRIVILEGE, PrivilegeMode.SHIZUKU.id))

    fun setPrivilegeMode(context: Context, mode: PrivilegeMode) {
        sp(context).edit().putString(KEY_PRIVILEGE, mode.id).apply()
    }

    fun aaApps(context: Context): Set<String> =
        sp(context).getStringSet(KEY_AA_APPS, emptySet())?.toSet() ?: emptySet()

    fun setAaApps(context: Context, packages: Set<String>) {
        sp(context).edit().putStringSet(KEY_AA_APPS, packages).apply()
    }

    fun uninstallApps(context: Context): Set<String> =
        sp(context).getStringSet(KEY_UNINSTALL_APPS, emptySet())?.toSet() ?: emptySet()

    fun setUninstallApps(context: Context, packages: Set<String>) {
        sp(context).edit().putStringSet(KEY_UNINSTALL_APPS, packages).apply()
    }

    fun adbHost(context: Context): String =
        sp(context).getString(KEY_ADB_HOST, null)
            ?: AdbShell.suggestedHost(context)

    fun setAdbHost(context: Context, host: String) {
        sp(context).edit().putString(KEY_ADB_HOST, host.trim()).apply()
    }

    fun adbPort(context: Context): Int =
        sp(context).getInt(KEY_ADB_PORT, 5555)

    fun setAdbPort(context: Context, port: Int) {
        sp(context).edit().putInt(KEY_ADB_PORT, port).apply()
    }

    fun adbPairPort(context: Context): Int =
        sp(context).getInt(KEY_ADB_PAIR_PORT, 0)

    fun setAdbPairPort(context: Context, port: Int) {
        sp(context).edit().putInt(KEY_ADB_PAIR_PORT, port).apply()
    }
}

/** Hai kênh độc lập — không trộn. */
enum class PrivilegeMode(val id: String, val labelVi: String) {
    SHIZUKU("shizuku", "Shizuku (độc lập)"),
    ADB("adb", "ADB không dây (độc lập)");

    fun label(context: Context): String = when (this) {
        SHIZUKU -> context.getString(R.string.privilege_shizuku)
        ADB -> context.getString(R.string.privilege_adb)
    }

    companion object {
        fun fromId(id: String?): PrivilegeMode =
            when (id) {
                "adb", "adb_shell" -> ADB
                "shizuku", "auto", null -> SHIZUKU
                else -> entries.firstOrNull { it.id == id } ?: SHIZUKU
            }

        fun uiEntries(): List<PrivilegeMode> = entries.toList()
    }
}
