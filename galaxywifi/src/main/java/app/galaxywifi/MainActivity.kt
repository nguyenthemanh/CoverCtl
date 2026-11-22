package app.galaxywifi

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import app.galaxywifi.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

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
        refreshPreview()
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
        refreshFavoriteButtons()
        refreshHotspotButton()
        MobileWifiWidgetProvider.refreshAll(this)
    }

    private fun refreshHotspotButton() {
        binding.btnToggleHotspot.text = if (HotspotToggle.isEnabled(this)) {
            getString(R.string.btn_hotspot_turn_off)
        } else {
            getString(R.string.btn_hotspot_turn_on)
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
                btn.text = getString(R.string.fav_slot_empty).let {
                    "Ô ${index + 1} · $it"
                }
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
        val s = DeviceNetStatus.read(this)
        binding.txtPreview.text = buildString {
            appendLine("Uplink ${s.uplinkLabel}  ${s.signalBars}")
            appendLine(
                "AP ${if (s.hotspotOn) "ON" else "OFF"} · " +
                    s.apSsid.ifBlank { getString(R.string.widget_ap_unnamed) }
            )
            if (s.apPassword.isNotBlank()) appendLine("${getString(R.string.widget_net_pass)}: ${s.apPassword}")
            else appendLine(getString(R.string.widget_pass_unknown))
            if (s.apBand.isNotBlank()) appendLine(s.apBand)
            appendLine(getString(R.string.widget_clients_fmt, s.connectedClients, s.maxClients))
            append("${getString(R.string.widget_data)} ${s.dataText}")
        }
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

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
