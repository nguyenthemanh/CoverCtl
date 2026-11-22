package app.galaxywifi

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import app.galaxywifi.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val langs = LocaleHelper.languages()
    private var languageReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableCoverDisplay()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.txtAppVersion.text = try {
            val p = packageManager.getPackageInfo(packageName, 0)
            "v${p.versionName} (${p.longVersionCode})"
        } catch (_: Exception) {
            ""
        }
        binding.txtAuthorName.text = BuildConfig.AUTHOR_NAME
        binding.txtAuthorName.setOnClickListener {
            openUrl(BuildConfig.AUTHOR_URL)
        }
        binding.btnCheckUpdate.setOnClickListener { checkUpdate() }

        bindLanguage()
        bindLayoutControls()
        bindOptions()
        bindFavoriteSlots()
        AppPermissions.request(this)
        binding.editApSsid.setText(Prefs.apSsidOverride(this).ifBlank {
            DeviceNetStatus.read(this).apSsid
        })
        binding.editApPass.setText(Prefs.apPasswordOverride(this))
        binding.btnSaveApCreds.setOnClickListener {
            Prefs.setApSsidOverride(this, binding.editApSsid.text?.toString().orEmpty())
            Prefs.setApPasswordOverride(this, binding.editApPass.text?.toString().orEmpty())
            MobileWifiWidgetProvider.refreshAll(this)
            refreshPreview()
            toast(getString(R.string.toast_ap_creds_saved))
        }
        binding.btnToggleHotspot.setOnClickListener {
            val wasOn = HotspotToggle.isEnabled(this)
            val ok = HotspotToggle.toggle(this)
            toast(
                when {
                    !ok -> getString(R.string.toast_hotspot_fail)
                    wasOn -> getString(R.string.toast_hotspot_off)
                    else -> getString(R.string.toast_hotspot_on)
                }
            )
            if (!ok) {
                try {
                    startActivity(
                        Intent("android.settings.TETHER_SETTINGS")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                }
            }
            binding.root.postDelayed({
                MobileWifiWidgetProvider.refreshAll(this)
                refreshPreview()
                refreshHotspotButton()
            }, 1200L)
        }
        binding.btnOpenHotspotSettings.setOnClickListener {
            val candidates = listOf(
                Intent("android.settings.TETHER_SETTINGS"),
                Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
            )
            for (raw in candidates) {
                try {
                    startActivity(Intent(raw).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    break
                } catch (_: Exception) {
                }
            }
        }
        binding.btnToggleMobileData.setOnClickListener {
            val wasOn = MobileDataToggle.isEnabled(this)
            val ok = MobileDataToggle.toggle(this)
            toast(
                when {
                    !ok -> getString(R.string.toast_mobile_data_fail)
                    wasOn -> getString(R.string.toast_mobile_data_off)
                    else -> getString(R.string.toast_mobile_data_on)
                }
            )
            if (!ok) {
                try {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                }
            }
            binding.root.postDelayed({
                MobileWifiWidgetProvider.refreshAll(this)
                refreshPreview()
                refreshMobileDataButton()
            }, 1000L)
        }
        binding.btnMediaAccess.setOnClickListener {
            MediaNotifListener.openSettings(this)
            toast(getString(R.string.toast_media_access))
        }
        binding.btnGrantPerms.setOnClickListener {
            AppPermissions.request(this)
            if (AppPermissions.missing(this).isEmpty()) {
                MobileWifiWidgetProvider.refreshAll(this)
                refreshPreview()
                toast(getString(R.string.toast_perms_ok))
            } else {
                toast(getString(R.string.toast_perms_need))
            }
        }
        binding.btnAddCover.setOnClickListener {
            val ok = SamsungAssistCommands.openCoverWidgetSettings(this)
            toast(if (ok) getString(R.string.toast_cover_widget_settings) else getString(R.string.toast_pin_widget_fail))
        }
        binding.btnPinHome.setOnClickListener {
            val ok = SamsungAssistCommands.requestPinWifiWidget(this)
            toast(if (ok) getString(R.string.toast_pin_widget_ok) else getString(R.string.toast_pin_widget_fail))
        }
        binding.btnAllowCover.setOnClickListener {
            val ok = SamsungAssistCommands.openCoverAppsSettings(this)
            toast(
                if (ok) getString(R.string.toast_cover_apps_settings)
                else getString(R.string.toast_cover_apps_fail)
            )
        }
        binding.btnOpenOnCover.setOnClickListener {
            val ok = ScreenModes.launchSelfOnCover(this)
            toast(
                if (ok) getString(R.string.toast_opened_on_cover)
                else getString(R.string.toast_cover_apps_fail)
            )
        }
        binding.btnOpenApps.setOnClickListener {
            ScreenModes.launchOnCover(
                this,
                Intent(this, CoverAppDrawerActivity::class.java)
            )
        }
        binding.btnUsageAccess.setOnClickListener {
            WidgetAppIcons.openUsageAccessSettings(this)
            toast(getString(R.string.toast_usage_access))
        }
        binding.btnClearFavs.setOnClickListener {
            Prefs.setFavoriteApps(this, emptyList())
            refreshFavoriteButtons()
            MobileWifiWidgetProvider.refreshAll(this)
            toast(getString(R.string.btn_clear_favs))
        }
        binding.btnRefresh.setOnClickListener {
            MobileWifiWidgetProvider.refreshAll(this)
            refreshPreview()
            toast(getString(R.string.toast_widget_refreshed))
        }
        refreshHotspotButton()
        refreshMobileDataButton()
        refreshPreview()
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
        refreshFavoriteButtons()
        refreshHotspotButton()
        refreshMobileDataButton()
        MobileWifiWidgetProvider.refreshAll(this)
    }

    private fun refreshHotspotButton() {
        binding.btnToggleHotspot.text = if (HotspotToggle.isEnabled(this)) {
            getString(R.string.btn_hotspot_turn_off)
        } else {
            getString(R.string.btn_hotspot_turn_on)
        }
    }

    private fun refreshMobileDataButton() {
        binding.btnToggleMobileData.text = if (MobileDataToggle.isEnabled(this)) {
            getString(R.string.btn_mobile_data_off)
        } else {
            getString(R.string.btn_mobile_data_on)
        }
    }

    private fun bindFavoriteSlots() {
        val buttons = listOf(binding.btnFav0, binding.btnFav1, binding.btnFav2, binding.btnFav3)
        buttons.forEachIndexed { index, btn ->
            btn.setOnClickListener {
                startActivity(
                    Intent(this, PickFavoriteActivity::class.java)
                        .putExtra(PickFavoriteActivity.EXTRA_SLOT, index)
                )
            }
            btn.setOnLongClickListener {
                Prefs.clearFavoriteAt(this, index)
                refreshFavoriteButtons()
                MobileWifiWidgetProvider.refreshAll(this)
                toast(getString(R.string.fav_slot_cleared, index + 1))
                true
            }
        }
        refreshFavoriteButtons()
    }

    private fun refreshFavoriteButtons() {
        val slots = Prefs.favoriteSlots(this)
        val buttons = listOf(binding.btnFav0, binding.btnFav1, binding.btnFav2, binding.btnFav3)
        buttons.forEachIndexed { index, btn ->
            val pkg = slots.getOrNull(index).orEmpty()
            if (pkg.isEmpty()) {
                btn.text = getString(R.string.fav_slot_fmt, index + 1, getString(R.string.fav_slot_empty))
                btn.icon = null
            } else {
                btn.text = getString(
                    R.string.fav_slot_fmt,
                    index + 1,
                    WidgetAppIcons.labelFor(this, pkg)
                )
                try {
                    btn.icon = packageManager.getApplicationIcon(pkg)
                    btn.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    btn.iconPadding = (12 * resources.displayMetrics.density).toInt()
                } catch (_: Exception) {
                    btn.icon = null
                }
            }
        }
    }

    private fun bindLanguage() {
        binding.spinnerLanguage.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            langs.map { it.label }
        )
        val saved = Prefs.languageTag(this)
        binding.spinnerLanguage.setSelection(
            langs.indexOfFirst { it.tag == saved }.coerceAtLeast(0)
        )
        languageReady = true
        binding.spinnerLanguage.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (!languageReady) return
                    val tag = langs[position].tag
                    if (tag == Prefs.languageTag(this@MainActivity)) return
                    Prefs.setLanguageTag(this@MainActivity, tag)
                    LocaleHelper.apply(tag)
                    MobileWifiWidgetProvider.refreshAll(this@MainActivity)
                    recreate()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun bindLayoutControls() {
        refreshLayoutLabels()
        binding.btnZoomIn.setOnClickListener {
            Prefs.setWidgetScalePct(this, Prefs.widgetScalePct(this) + 5)
            onLayoutChanged()
        }
        binding.btnZoomOut.setOnClickListener {
            Prefs.setWidgetScalePct(this, Prefs.widgetScalePct(this) - 5)
            onLayoutChanged()
        }
        binding.btnMoveLeft.setOnClickListener {
            Prefs.setWidgetOffsetX(this, Prefs.widgetOffsetX(this) - 4)
            onLayoutChanged()
        }
        binding.btnMoveRight.setOnClickListener {
            Prefs.setWidgetOffsetX(this, Prefs.widgetOffsetX(this) + 4)
            onLayoutChanged()
        }
        binding.btnMoveUp.setOnClickListener {
            Prefs.setWidgetOffsetY(this, Prefs.widgetOffsetY(this) - 4)
            onLayoutChanged()
        }
        binding.btnMoveDown.setOnClickListener {
            Prefs.setWidgetOffsetY(this, Prefs.widgetOffsetY(this) + 4)
            onLayoutChanged()
        }
        binding.btnLayoutReset.setOnClickListener {
            Prefs.resetWidgetLayout(this)
            onLayoutChanged()
        }
    }

    private fun onLayoutChanged() {
        refreshLayoutLabels()
        MobileWifiWidgetProvider.refreshAll(this)
        toast(getString(R.string.toast_layout_updated))
    }

    private fun refreshLayoutLabels() {
        binding.txtLayoutScale.text = getString(R.string.widget_layout_scale, Prefs.widgetScalePct(this))
        binding.txtLayoutOffset.text = getString(
            R.string.widget_layout_offset,
            Prefs.widgetOffsetX(this),
            Prefs.widgetOffsetY(this)
        )
    }

    private fun bindOptions() {
        binding.switchDark.isChecked = Prefs.widgetDark(this)
        binding.switchAutoDark.isChecked = Prefs.widgetAutoDark(this)
        binding.switchCalendar.isChecked = Prefs.widgetCalendar(this)
        binding.switchCalCharging.isChecked = Prefs.widgetCalendarWhenCharging(this)
        binding.switchMeters.isChecked = Prefs.widgetShowMeters(this)
        binding.switchSignal.isChecked = Prefs.widgetShowSignal(this)
        binding.switchData.isChecked = Prefs.widgetShowData(this)
        binding.switchDark.isEnabled = !Prefs.widgetAutoDark(this)
        binding.switchCalCharging.isEnabled = Prefs.widgetCalendar(this)

        val listener = CompoundButton.OnCheckedChangeListener { _, _ ->
            Prefs.setWidgetDark(this, binding.switchDark.isChecked)
            Prefs.setWidgetAutoDark(this, binding.switchAutoDark.isChecked)
            Prefs.setWidgetCalendar(this, binding.switchCalendar.isChecked)
            Prefs.setWidgetCalendarWhenCharging(this, binding.switchCalCharging.isChecked)
            Prefs.setWidgetShowMeters(this, binding.switchMeters.isChecked)
            Prefs.setWidgetShowSignal(this, binding.switchSignal.isChecked)
            Prefs.setWidgetShowData(this, binding.switchData.isChecked)
            binding.switchDark.isEnabled = !binding.switchAutoDark.isChecked
            binding.switchCalCharging.isEnabled = binding.switchCalendar.isChecked
            refreshPreview()
            MobileWifiWidgetProvider.refreshAll(this)
        }
        listOf(
            binding.switchDark, binding.switchAutoDark, binding.switchCalendar,
            binding.switchCalCharging, binding.switchMeters, binding.switchSignal,
            binding.switchData
        ).forEach { it.setOnCheckedChangeListener(listener) }
    }

    private fun refreshPreview() {
        val missingRuntime = AppPermissions.missing(this)
        val media = MediaNotifListener.isEnabled(this)
        val usage = WidgetAppIcons.hasUsageAccess(this)
        val widgetIds = android.appwidget.AppWidgetManager.getInstance(this)
            .getAppWidgetIds(
                android.content.ComponentName(this, MobileWifiWidgetProvider::class.java)
            )
        val coverPresent = try {
            val dm = getSystemService(android.hardware.display.DisplayManager::class.java)
            dm.getDisplay(ScreenModes.COVER_DISPLAY) != null
        } catch (_: Exception) {
            false
        }
        val displayId = try {
            if (Build.VERSION.SDK_INT >= 30) {
                display?.displayId ?: 0
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.displayId
            }
        } catch (_: Exception) {
            0
        }
        val onCover = displayId == ScreenModes.COVER_DISPLAY
        val surface = if (onCover) {
            getString(R.string.status_surface_cover, displayId)
        } else {
            getString(R.string.status_surface_main, displayId)
        }

        val issues = mutableListOf<String>()
        if (missingRuntime.isNotEmpty()) {
            issues += getString(
                R.string.status_missing_runtime,
                missingRuntime.joinToString(", ") { friendlyPerm(it) }
            )
        }
        if (!media) issues += getString(R.string.status_missing_media)
        if (!usage) issues += getString(R.string.status_missing_usage)
        if (widgetIds.isEmpty()) issues += getString(R.string.status_missing_widget)
        if (!coverPresent) issues += getString(R.string.status_no_cover_display)

        val health = if (issues.isEmpty()) {
            getString(R.string.status_health_ok)
        } else {
            getString(R.string.status_health_issues, issues.size)
        }

        val s = DeviceNetStatus.read(this)
        binding.txtPreview.text = buildString {
            appendLine(health)
            appendLine(
                getString(R.string.status_privilege_line, getString(R.string.status_privilege_normal))
            )
            appendLine(getString(R.string.status_running_on, surface))
            appendLine(
                getString(
                    R.string.status_cover_hw,
                    if (coverPresent) getString(R.string.status_yes) else getString(R.string.status_no)
                )
            )
            appendLine(
                getString(
                    R.string.status_widget_line,
                    if (widgetIds.isNotEmpty()) {
                        getString(R.string.status_widget_ok, widgetIds.size)
                    } else {
                        getString(R.string.status_no)
                    }
                )
            )
            appendLine(
                "Hotspot ${if (s.hotspotOn) "ON" else "OFF"} · ${s.uplinkLabel} · ${s.dataText}"
            )
            appendLine()
            if (issues.isEmpty()) {
                append(getString(R.string.status_perms_all_ok))
            } else {
                appendLine(getString(R.string.status_need_fix))
                issues.forEachIndexed { i, line ->
                    append(if (i == issues.lastIndex) "• $line" else "• $line\n")
                }
            }
        }
    }

    private fun friendlyPerm(name: String): String = when (name) {
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION -> getString(R.string.perm_location)
        android.Manifest.permission.READ_PHONE_STATE -> getString(R.string.perm_phone)
        android.Manifest.permission.NEARBY_WIFI_DEVICES -> getString(R.string.perm_nearby)
        android.Manifest.permission.POST_NOTIFICATIONS -> getString(R.string.perm_notifications)
        android.Manifest.permission.ACCESS_WIFI_STATE,
        android.Manifest.permission.CHANGE_WIFI_STATE -> getString(R.string.perm_wifi)
        else -> name.substringAfterLast('.')
    }

    private fun enableCoverDisplay() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun checkUpdate() {
        binding.btnCheckUpdate.isEnabled = false
        binding.txtUpdateStatus.text = getString(R.string.update_checking)
        thread {
            try {
                val result = UpdateChecker.check(BuildConfig.VERSION_NAME)
                if (!result.newer) {
                    runOnUiThread {
                        binding.btnCheckUpdate.isEnabled = true
                        binding.txtUpdateStatus.text =
                            getString(R.string.update_latest, result.tagName)
                        toast(getString(R.string.update_latest, result.tagName))
                    }
                    return@thread
                }
                val apkUrl = result.apkUrl
                if (apkUrl.isNullOrBlank()) {
                    runOnUiThread {
                        binding.btnCheckUpdate.isEnabled = true
                        binding.txtUpdateStatus.text =
                            getString(R.string.update_no_apk, result.tagName)
                    }
                    return@thread
                }
                runOnUiThread {
                    binding.txtUpdateStatus.text =
                        getString(R.string.update_found, result.tagName)
                }
                val file = UpdateChecker.downloadApk(this, apkUrl) { pct ->
                    runOnUiThread {
                        binding.txtUpdateStatus.text =
                            getString(R.string.update_downloading, pct)
                    }
                }
                runOnUiThread {
                    binding.txtUpdateStatus.text = getString(R.string.update_installing)
                    installApk(file)
                    binding.btnCheckUpdate.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.btnCheckUpdate.isEnabled = true
                    val msg = getString(R.string.update_fail, e.message ?: e.javaClass.simpleName)
                    binding.txtUpdateStatus.text = msg
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(file: java.io.File) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            toast(getString(R.string.update_need_install_perm))
            try {
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
