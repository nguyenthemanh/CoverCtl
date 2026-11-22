package app.coverctl

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import app.coverctl.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var currentDpi = 340
    private var lastExportUri: Uri? = null
    private var lastSpoofNote: String = ""
    private val profiles = DeviceProfile.uiEntries()
    private val privilegeModes = PrivilegeMode.uiEntries()

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            updatePrivilegeUi()
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                refreshStatus()
                refreshDeviceInfo()
            }
        }

    private val binderReceived =
        Shizuku.OnBinderReceivedListener { runOnUiThread { updatePrivilegeUi() } }
    private val binderDead =
        Shizuku.OnBinderDeadListener { runOnUiThread { updatePrivilegeUi() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)

        Shell.mode = Prefs.privilegeMode(this)
        setupSpinners()
        refreshAaCount()
        refreshUninstallCount()
        refreshDeviceInfo()
        refreshScreenModeUi()

        binding.btnAppSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnModeMain.setOnClickListener { applyScreenMode(ScreenModes.Mode.MAIN) }
        binding.btnModeCover.setOnClickListener { applyScreenMode(ScreenModes.Mode.COVER) }
        binding.btnModeDual.setOnClickListener { applyScreenMode(ScreenModes.Mode.DUAL) }
        binding.switchKeepDual.isChecked = Prefs.keepDual(this)
        binding.switchKeepDual.setOnCheckedChangeListener { _, checked ->
            Prefs.setKeepDual(this, checked)
            if (!checked) DualKeepService.stop(this)
        }
        binding.btnOverlayPerm.setOnClickListener {
            if (Shell.available()) {
                runCmd(getString(R.string.btn_overlay_perm)) {
                    ScreenModes.grantOverlay(this@MainActivity)
                }
            }
            ScreenModes.openOverlaySettings(this)
        }
        binding.btnDoctor.setOnClickListener {
            runCmd(getString(R.string.btn_doctor)) {
                Shell.Result(0, ScreenModes.doctor(this@MainActivity), "")
            }
        }
        binding.btnAutoSetup.setOnClickListener {
            toast(PrivilegeManager.autoSetup(this))
            updatePrivilegeUi()
        }
        binding.btnShizuku.setOnClickListener { requestShizuku() }
        binding.btnDevOptions.setOnClickListener { PrivilegeManager.openDeveloperOptions(this) }
        binding.btnAdbPair.setOnClickListener { pairAdb() }
        binding.btnAdbConnect.setOnClickListener { connectAdb() }
        binding.btnAdbDisconnect.setOnClickListener {
            AdbShell.disconnect()
            toast(getString(R.string.toast_adb_disconnected))
            updatePrivilegeUi()
        }
        fillAdbFields()

        binding.btnCoverFull.setOnClickListener {
            val p = currentProfile()
            Prefs.setProfileId(this, p.id)
            runCmd(getString(R.string.cmd_cover_full)) {
                CoverCommands.fixCoverFullUi(p)
            }
        }
        binding.btnApplyDisplay.setOnClickListener {
            val p = currentProfile()
            Prefs.setProfileId(this, p.id)
            runCmd(getString(R.string.cmd_apply_display)) {
                CoverCommands.applyDisplayProfile(p)
            }
        }
        binding.btnApplySpoof.setOnClickListener {
            val p = currentProfile()
            Prefs.setProfileId(this, p.id)
            runCmd(getString(R.string.cmd_apply_spoof), showSpoofDialog = true) {
                CoverCommands.applyIdentitySpoof(p)
            }
        }
        binding.btnRefreshDevice.setOnClickListener { refreshDeviceInfo() }
        binding.btnShareDevice.setOnClickListener { shareDeviceInfo() }

        binding.btnDexOn.setOnClickListener {
            runCmd(getString(R.string.cmd_dex_on)) { DexCommands.enableDex() }
        }
        binding.btnDexStart.setOnClickListener {
            runCmd(getString(R.string.cmd_dex_start)) { DexCommands.startDexOnExternal() }
        }
        binding.btnDexSettings.setOnClickListener {
            runCmd(getString(R.string.cmd_dex_settings)) { DexCommands.openDexSettings() }
        }
        binding.btnDexOff.setOnClickListener {
            runCmd(getString(R.string.cmd_dex_off)) { DexCommands.disableDex() }
        }
        binding.btnDexStatus.setOnClickListener {
            runCmd(getString(R.string.cmd_dex_status)) { DexCommands.dexStatus() }
        }

        binding.btnAaChecklist.setOnClickListener {
            val text = AndroidAutoCommands.checklist(this)
            LogStore.append(getString(R.string.cmd_aa_checklist), text)
            binding.txtLog.text = text
            toast("Checklist AA")
        }
        binding.btnAaUnknownAuto.setOnClickListener {
            toast(AndroidAutoCommands.openUnknownAuto(this))
        }
        binding.btnAaAnyAuto.setOnClickListener {
            toast(AndroidAutoCommands.openAnyAuto(this))
        }
        binding.btnAaInstallPlay.setOnClickListener {
            startActivity(Intent(this, AaPlayInstallActivity::class.java))
        }
        binding.btnAaUnlock.setOnClickListener {
            runCmd(getString(R.string.cmd_aa_unlock)) { AndroidAutoCommands.unlockUnknownSources() }
        }
        binding.btnAaSettings.setOnClickListener {
            runCmd(getString(R.string.cmd_aa_settings)) {
                AndroidAutoCommands.openAndroidAutoSettings()
            }
        }
        binding.btnAaPick.setOnClickListener { showAaAppPicker() }
        binding.btnAaPrepare.setOnClickListener {
            val apps = Prefs.aaApps(this).toList()
            val text = AndroidAutoCommands.checkPlayMarks(this, apps)
            LogStore.append(getString(R.string.cmd_aa_prepare), text)
            binding.txtLog.text = text
            toast(getString(R.string.btn_aa_prepare))
        }
        binding.btnAaStatus.setOnClickListener {
            runCmd(getString(R.string.cmd_aa_status)) {
                AndroidAutoCommands.status(this)
            }
        }

        binding.btnUninstallPick.setOnClickListener { showUninstallPicker(includeSystem = false) }
        binding.btnUninstallPickSystem.setOnClickListener { showUninstallPicker(includeSystem = true) }
        binding.btnUninstallRun.setOnClickListener { confirmUninstall(keepData = false) }
        binding.btnUninstallKeepData.setOnClickListener { confirmUninstall(keepData = true) }

        binding.btnLauncher.setOnClickListener {
            runCmd(getString(R.string.cmd_launcher)) {
                CoverCommands.openLauncherOnCover(currentProfile())
            }
        }
        binding.btnSettings.setOnClickListener {
            runCmd(getString(R.string.cmd_settings)) {
                CoverCommands.openSettingsOnCover(currentProfile())
            }
        }
        binding.btnRestoreNav.setOnClickListener {
            runCmd(getString(R.string.cmd_restore_nav)) {
                ScreenModes.grantOverlay(this@MainActivity)
                val r = CoverCommands.restoreCoverNavigation(currentProfile())
                CoverNavOverlayService.start(
                    this@MainActivity,
                    Prefs.screenMode(this@MainActivity).ifBlank { ScreenModes.Mode.DUAL.id }
                )
                r
            }
            if (!android.provider.Settings.canDrawOverlays(this)) {
                toast(getString(R.string.toast_need_overlay))
                ScreenModes.openOverlaySettings(this)
            }
        }

        binding.switchEdgeGestures.isChecked = Prefs.edgeGestures(this)
        binding.switchEdgeBoth.isChecked = Prefs.edgeBothSides(this)
        binding.switchShowNavBar.isChecked = Prefs.showNavBar(this)
        binding.switchEdgeGestures.setOnCheckedChangeListener { _, checked ->
            Prefs.setEdgeGestures(this, checked)
            restartCoverOverlayIfNeeded()
        }
        binding.switchEdgeBoth.setOnCheckedChangeListener { _, checked ->
            Prefs.setEdgeBothSides(this, checked)
            restartCoverOverlayIfNeeded()
        }
        binding.switchShowNavBar.setOnCheckedChangeListener { _, checked ->
            Prefs.setShowNavBar(this, checked)
            restartCoverOverlayIfNeeded()
        }
        binding.btnApplyEdgePreset.setOnClickListener {
            Prefs.setEdgeGestures(this, true)
            Prefs.setEdgeBothSides(this, true)
            Prefs.setShowNavBar(this, true)
            Prefs.setKeepDual(this, true)
            binding.switchEdgeGestures.isChecked = true
            binding.switchEdgeBoth.isChecked = true
            binding.switchShowNavBar.isChecked = true
            applyScreenMode(ScreenModes.Mode.DUAL)
            toast(getString(R.string.toast_edge_on))
        }
        binding.btnOpenOho.setOnClickListener {
            SamsungAssistCommands.openOrStore(this, SamsungAssistCommands.PKG_OHO)
        }
        binding.btnOpenGoodLock.setOnClickListener {
            SamsungAssistCommands.openOrStore(this, SamsungAssistCommands.PKG_GOODLOCK)
        }
        binding.btnOpenMultistar.setOnClickListener {
            SamsungAssistCommands.openOrStore(this, SamsungAssistCommands.PKG_MULTISTAR)
        }
        binding.btnEnableEdgePanel.setOnClickListener {
            runCmd(getString(R.string.cmd_edge_panel)) {
                SamsungAssistCommands.enableSamsungEdge()
            }
        }

        binding.btnSmall.setOnClickListener { applyDpi(260) }
        binding.btnNormal.setOnClickListener { applyDpi(340) }
        binding.btnLarge.setOnClickListener { applyDpi(400) }
        binding.btnXlarge.setOnClickListener { applyDpi(440) }
        binding.btnResetDpi.setOnClickListener {
            runCmd(getString(R.string.cmd_reset_dpi)) {
                CoverCommands.resetDensity(currentProfile())
            }
        }

        binding.btnExportLog.setOnClickListener { exportLog(share = false) }
        binding.btnShareLog.setOnClickListener { exportLog(share = true) }
        binding.btnClearLog.setOnClickListener {
            LogStore.clear()
            binding.txtLog.text = ""
            toast(getString(R.string.toast_log_cleared))
        }

        binding.seekDpi.max = 200
        binding.seekDpi.progress = 80
        binding.seekDpi.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentDpi = 260 + progress
                binding.txtDpiValue.text = getString(R.string.dpi_value, currentDpi)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                applyDpi(currentDpi)
            }
        })
        binding.txtDpiValue.text = getString(R.string.dpi_value, currentDpi)

        updatePrivilegeUi()
        if (Shell.available()) refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePrivilegeUi()
        refreshAaCount()
        refreshUninstallCount()
        refreshDeviceInfo()
        refreshScreenModeUi()
    }

    private fun applyScreenMode(mode: ScreenModes.Mode) {
        val label = when (mode) {
            ScreenModes.Mode.MAIN -> getString(R.string.cmd_mode_main)
            ScreenModes.Mode.COVER -> getString(R.string.cmd_mode_cover)
            ScreenModes.Mode.DUAL -> getString(R.string.cmd_mode_dual)
        }
        if ((mode == ScreenModes.Mode.COVER || mode == ScreenModes.Mode.DUAL) &&
            !android.provider.Settings.canDrawOverlays(this)
        ) {
            toast(getString(R.string.toast_need_overlay))
            ScreenModes.openOverlaySettings(this)
        }
        runCmd(label) {
            ScreenModes.apply(this@MainActivity, mode, keepDual = Prefs.keepDual(this@MainActivity), profile = currentProfile())
        }
        binding.root.postDelayed({ refreshScreenModeUi() }, 1200)
    }

    private fun refreshScreenModeUi() {
        val mode = ScreenModes.Mode.fromId(Prefs.screenMode(this))
        val label = when (mode) {
            ScreenModes.Mode.MAIN -> getString(R.string.mode_main)
            ScreenModes.Mode.COVER -> getString(R.string.mode_cover)
            ScreenModes.Mode.DUAL -> getString(R.string.mode_dual)
        }
        binding.txtScreenMode.text = getString(R.string.screen_mode_status, label)
        binding.switchKeepDual.isChecked = Prefs.keepDual(this)
    }

    private fun restartCoverOverlayIfNeeded() {
        val mode = Prefs.screenMode(this)
        if (mode == ScreenModes.Mode.DUAL.id || mode == ScreenModes.Mode.COVER.id) {
            CoverNavOverlayService.start(this, mode)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
    }

    private fun setupSpinners() {
        binding.spinnerPrivilege.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            privilegeModes.map { it.label(this) }
        )
        binding.spinnerPrivilege.setSelection(
            privilegeModes.indexOf(Prefs.privilegeMode(this)).coerceAtLeast(0)
        )
        binding.spinnerPrivilege.onItemSelectedListener = simpleSelect {
            Prefs.setPrivilegeMode(this, privilegeModes[it])
            Shell.mode = privilegeModes[it]
            updatePrivilegeUi()
            if (Shell.available()) {
                refreshStatus()
                refreshDeviceInfo()
            }
        }

        binding.spinnerProfile.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            profiles.map { it.label(this) }
        )
        binding.spinnerProfile.setSelection(
            profiles.indexOfFirst { it.id == Prefs.profileId(this) }.coerceAtLeast(0)
        )
        binding.spinnerProfile.onItemSelectedListener = simpleSelect {
            Prefs.setProfileId(this, profiles[it].id)
            refreshDeviceInfo()
        }
    }

    private fun simpleSelect(onSelect: (Int) -> Unit) =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) = onSelect(position)

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

    private fun currentProfile(): DeviceProfile =
        profiles.getOrElse(binding.spinnerProfile.selectedItemPosition) { DeviceProfile.AUTO }

    private fun refreshAaCount() {
        binding.txtAaSelected.text =
            getString(R.string.aa_selected_count, Prefs.aaApps(this).size)
    }

    private fun refreshUninstallCount() {
        binding.txtUninstallSelected.text =
            getString(R.string.uninstall_selected_count, Prefs.uninstallApps(this).size)
    }

    private fun showUninstallPicker(includeSystem: Boolean) {
        if (!Shell.available()) {
            toast(shellNotReadyMessage())
            return
        }
        Thread {
            val pm = packageManager
            val apps = PackageCommands.listUninstallableApps(this, includeSystem)
            val selected = Prefs.uninstallApps(this).toMutableSet()
            val picks = apps.map { app ->
                val tag = if (app.system) " [HT]" else ""
                PickApp(
                    packageName = app.packageName,
                    label = "${app.label}$tag",
                    subtitle = app.packageName,
                    icon = AppPickAdapter.loadIcon(pm, app.packageName)
                )
            }
            runOnUiThread {
                if (picks.isEmpty()) {
                    toast("Không tìm thấy app")
                    return@runOnUiThread
                }
                showAppPickDialog(
                    titleRes = R.string.uninstall_pick_title,
                    picks = picks,
                    selected = selected,
                    onSave = {
                        Prefs.setUninstallApps(this, selected)
                        refreshUninstallCount()
                        toast(getString(R.string.uninstall_selected_count, selected.size))
                    }
                )
            }
        }.start()
    }

    private fun confirmUninstall(keepData: Boolean) {
        val apps = Prefs.uninstallApps(this).toList()
        if (apps.isEmpty()) {
            toast(getString(R.string.uninstall_selected_none))
            return
        }
        if (!Shell.available()) {
            toast(shellNotReadyMessage())
            return
        }
        val preview = apps.take(8).joinToString("\n") +
            if (apps.size > 8) "\n… (+${apps.size - 8})" else ""
        val mode = if (keepData) " (giữ dữ liệu)" else ""
        AlertDialog.Builder(this)
            .setTitle(R.string.uninstall_confirm_title)
            .setMessage(getString(R.string.uninstall_confirm_msg, apps.size, preview) + mode)
            .setPositiveButton(R.string.btn_uninstall_run) { _, _ ->
                runCmd(getString(R.string.cmd_uninstall)) {
                    PackageCommands.uninstall(apps, keepData = keepData)
                }
            }
            .setNegativeButton(R.string.aa_pick_cancel, null)
            .show()
    }

    private fun showAaAppPicker() {
        Thread {
            val pm = packageManager
            val apps = AndroidAutoCommands.listLaunchableApps(this)
            val selected = Prefs.aaApps(this).toMutableSet()
            val picks = apps.map { app ->
                PickApp(
                    packageName = app.packageName,
                    label = app.label,
                    subtitle = app.packageName,
                    icon = AppPickAdapter.loadIcon(pm, app.packageName)
                )
            }
            runOnUiThread {
                showAppPickDialog(
                    titleRes = R.string.aa_pick_title,
                    picks = picks,
                    selected = selected,
                    onSave = {
                        Prefs.setAaApps(this, selected)
                        refreshAaCount()
                        toast(getString(R.string.aa_selected_count, selected.size))
                    }
                )
            }
        }.start()
    }

    private fun showAppPickDialog(
        titleRes: Int,
        picks: List<PickApp>,
        selected: MutableSet<String>,
        onSave: () -> Unit
    ) {
        val adapter = AppPickAdapter(picks, selected)
        val listView = android.widget.ListView(this).apply {
            this.adapter = adapter
            dividerHeight = 1
            setOnItemClickListener { _, _, which, _ ->
                val pkg = picks[which].packageName
                if (!selected.add(pkg)) selected.remove(pkg)
                adapter.notifyDataSetChanged()
            }
        }
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(listView)
            .setPositiveButton(R.string.aa_pick_ok) { _, _ -> onSave() }
            .setNegativeButton(R.string.aa_pick_cancel, null)
            .show()
    }

    private fun shellNotReadyMessage(): String =
        when (Prefs.privilegeMode(this)) {
            PrivilegeMode.SHIZUKU -> getString(R.string.shizuku_start_first)
            PrivilegeMode.ADB -> getString(R.string.adb_not_connected)
        }

    private fun fillAdbFields() {
        binding.editAdbHost.setText(Prefs.adbHost(this))
        binding.editAdbPort.setText(Prefs.adbPort(this).toString())
        val pairPort = Prefs.adbPairPort(this)
        if (pairPort > 0) binding.editAdbPairPort.setText(pairPort.toString())
    }

    private fun saveAdbFields() {
        Prefs.setAdbHost(this, binding.editAdbHost.text?.toString().orEmpty().ifBlank {
            AdbShell.suggestedHost(this)
        })
        binding.editAdbPort.text?.toString()?.toIntOrNull()?.let { Prefs.setAdbPort(this, it) }
        binding.editAdbPairPort.text?.toString()?.toIntOrNull()?.let { Prefs.setAdbPairPort(this, it) }
    }

    private fun pairAdb() {
        saveAdbFields()
        val host = Prefs.adbHost(this)
        val pairPort = binding.editAdbPairPort.text?.toString()?.toIntOrNull()
        val code = binding.editAdbPairCode.text?.toString()?.trim().orEmpty()
        if (pairPort == null || pairPort <= 0 || code.length < 6) {
            toast("Nhập cổng ghép đôi + mã 6 số từ Wireless debugging")
            PrivilegeManager.openWirelessDebugging(this)
            return
        }
        toast("Đang ghép đôi…")
        Thread {
            val msg = AdbShell.pair(host, pairPort, code)
            runOnUiThread {
                binding.txtLog.text = msg
                LogStore.append("adb-pair", msg)
                toast(msg.take(80))
                updatePrivilegeUi()
            }
        }.start()
    }

    private fun connectAdb() {
        saveAdbFields()
        val host = Prefs.adbHost(this)
        val port = binding.editAdbPort.text?.toString()?.toIntOrNull()
        if (port == null || port <= 0) {
            toast("Nhập cổng kết nối Wireless debugging (IP:port)")
            PrivilegeManager.openWirelessDebugging(this)
            return
        }
        toast("Đang kết nối ADB…")
        Thread {
            val msg = AdbShell.connect(this@MainActivity, host, port)
            runOnUiThread {
                binding.txtLog.text = msg
                LogStore.append("adb-connect", msg)
                toast(msg.take(80))
                updatePrivilegeUi()
                if (AdbShell.available()) {
                    refreshStatus()
                    refreshDeviceInfo()
                }
            }
        }.start()
    }

    private fun requestShizuku() {
        if (Prefs.privilegeMode(this) != PrivilegeMode.SHIZUKU) {
            toast("Đang ở chế độ ADB — chuyển spinner sang Shizuku trước")
            return
        }
        if (!Shizuku.pingBinder()) {
            toast(getString(R.string.shizuku_start_first))
            PrivilegeManager.openShizukuApp(this)
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(PrivilegeManager.REQUEST_CODE)
        } else {
            updatePrivilegeUi()
            refreshStatus()
            refreshDeviceInfo()
        }
    }

    private fun updatePrivilegeUi() {
        val st = PrivilegeManager.status(this)
        binding.txtShizuku.text = st.messageVi
        if (st.ready) {
            binding.txtShizuku.setBackgroundResource(R.drawable.bg_status_chip)
            binding.txtShizuku.setTextColor(getColor(R.color.ios_green))
        } else {
            binding.txtShizuku.setBackgroundResource(R.drawable.bg_status_chip_warn)
            binding.txtShizuku.setTextColor(getColor(R.color.ios_orange))
        }
        val isShizuku = st.mode == PrivilegeMode.SHIZUKU
        binding.panelShizuku.visibility = if (isShizuku) View.VISIBLE else View.GONE
        binding.panelAdb.visibility = if (isShizuku) View.GONE else View.VISIBLE
        if (isShizuku) {
            binding.btnShizuku.text =
                if (st.ready) getString(R.string.shizuku_refresh) else getString(R.string.shizuku_grant)
        }
        val ok = st.ready
        listOf(
            binding.btnCoverFull, binding.btnApplyDisplay, binding.btnApplySpoof,
            binding.btnDexOn, binding.btnDexStart, binding.btnDexSettings,
            binding.btnDexOff, binding.btnDexStatus,
            binding.btnAaUnlock, binding.btnAaSettings, binding.btnAaStatus,
            binding.btnUninstallPick, binding.btnUninstallPickSystem,
            binding.btnUninstallRun, binding.btnUninstallKeepData,
            binding.btnModeMain, binding.btnModeCover, binding.btnModeDual, binding.btnDoctor,
            binding.btnLauncher, binding.btnSettings, binding.btnRestoreNav,
            binding.btnApplyEdgePreset, binding.btnEnableEdgePanel,
            binding.btnSmall, binding.btnNormal, binding.btnLarge, binding.btnXlarge,
            binding.btnResetDpi, binding.seekDpi
        ).forEach { it.isEnabled = ok }
        binding.btnRefreshDevice.isEnabled = true
        binding.btnShareDevice.isEnabled = true
        binding.btnAaChecklist.isEnabled = true
        binding.btnAaUnknownAuto.isEnabled = true
        binding.btnAaAnyAuto.isEnabled = true
        binding.btnAaInstallPlay.isEnabled = true
        binding.btnAaPick.isEnabled = true
        binding.btnAaPrepare.isEnabled = true
        binding.btnExportLog.isEnabled = true
        binding.btnShareLog.isEnabled = true
        binding.btnClearLog.isEnabled = true
    }

    private fun applyDpi(dpi: Int) {
        currentDpi = dpi
        binding.txtDpiValue.text = getString(R.string.dpi_value, dpi)
        binding.seekDpi.progress = (dpi - 260).coerceIn(0, 200)
        runCmd(getString(R.string.cmd_dpi, dpi)) {
            CoverCommands.setDensity(currentProfile(), dpi)
        }
    }

    private fun runCmd(
        label: String,
        showSpoofDialog: Boolean = false,
        block: () -> Shell.Result
    ) {
        Thread {
            val result = block()
            val text = result.text()
            LogStore.append(label, text)
            runOnUiThread {
                binding.txtLog.text = "$label\n$text"
                if (showSpoofDialog) {
                    when {
                        text.contains("SPOOF_FAIL") -> {
                            lastSpoofNote = "Lần spoof: FAIL — máy chưa Magisk/resetprop"
                            AlertDialog.Builder(this)
                                .setTitle(R.string.spoof_fail_title)
                                .setMessage(text)
                                .setPositiveButton(android.R.string.ok, null)
                                .setNeutralButton(R.string.btn_share_log) { _, _ ->
                                    exportLog(share = true)
                                }
                                .show()
                        }
                        text.contains("SPOOF_OK") -> {
                            lastSpoofNote = "Lần spoof: OK"
                            toast(getString(R.string.spoof_ok_toast))
                        }
                        text.contains("SPOOF_SKIP") -> {
                            lastSpoofNote = "Lần spoof: bỏ qua (hồ sơ Tự nhận diện)"
                            toast("Chọn Flip/Fold rồi thử lại")
                        }
                    }
                } else if (!result.ok) {
                    toast(
                        getString(
                            R.string.cmd_error,
                            label,
                            result.stderr.ifBlank { result.stdout }.take(120)
                        )
                    )
                }
                refreshStatus()
                refreshDeviceInfo()
            }
        }.start()
    }

    private fun refreshDeviceInfo() {
        val profile = currentProfile()
        val apiBlock = buildString {
            appendLine("=== Máy thật (Android API, luôn đúng) ===")
            appendLine("MODEL=${Build.MODEL}")
            appendLine("DEVICE=${Build.DEVICE}")
            appendLine("PRODUCT=${Build.PRODUCT}")
            appendLine("MANUFACTURER=${Build.MANUFACTURER}")
            appendLine("ANDROID=${Build.VERSION.RELEASE} SDK=${Build.VERSION.SDK_INT}")
            appendLine("FINGERPRINT=${Build.FINGERPRINT}")
        }
        if (!Shell.available()) {
            val resolved = if (profile == DeviceProfile.AUTO) {
                DeviceProfile.detectFromModel(Build.MODEL)
            } else profile
            binding.txtDeviceInfo.text = buildString {
                appendLine(apiBlock.trim())
                appendLine()
                appendLine("Hồ sơ chọn: ${profile.label(this@MainActivity)}")
                appendLine(
                    resolved.spoofModel?.let { "Target spoof: $it / ${resolved.spoofDevice}" }
                        ?: "Target: (không spoof)"
                )
                appendLine("spoof_capable=NO — cần Magisk; shell: ${Prefs.privilegeMode(this@MainActivity).label(this@MainActivity)}")
                appendLine("${shellNotReadyMessage()} — chưa đọc được getprop")
                if (lastSpoofNote.isNotBlank()) appendLine(lastSpoofNote)
            }.trim()
            return
        }
        val modeLabel = Prefs.privilegeMode(this).label(this)
        Thread {
            val id = CoverCommands.readIdentityBlock()
            val resolved = CoverCommands.resolveProfile(profile)
            val block = buildString {
                appendLine(apiBlock.trim())
                appendLine()
                appendLine("=== Props qua $modeLabel (getprop) ===")
                appendLine(id.text())
                appendLine()
                appendLine("Hồ sơ chọn: ${profile.label(this@MainActivity)}")
                appendLine(
                    resolved.spoofModel?.let { "Target spoof: $it / ${resolved.spoofDevice}" }
                        ?: "Target: (không spoof)"
                )
                if (lastSpoofNote.isNotBlank()) appendLine(lastSpoofNote)
                appendLine()
                appendLine("Lưu ý: không root thì MODEL trong props KHÔNG đổi — đó là đúng, không phải lỗi UI.")
            }
            runOnUiThread { binding.txtDeviceInfo.text = block.trim() }
        }.start()
    }

    private fun refreshStatus() {
        if (!Shell.available()) {
            binding.txtStatus.text = shellNotReadyMessage()
            return
        }
        Thread {
            val result = CoverCommands.getStatus(currentProfile())
            LogStore.append("status", result.text())
            runOnUiThread { binding.txtStatus.text = result.text() }
        }.start()
    }

    private fun shareDeviceInfo() {
        val text = binding.txtDeviceInfo.text?.toString().orEmpty()
        LogStore.append("device-info", text)
        exportLog(share = true)
    }

    private fun exportLog(share: Boolean) {
        val device = binding.txtDeviceInfo.text?.toString().orEmpty()
        val status = binding.txtStatus.text?.toString().orEmpty()
        Thread {
            val extra = buildString {
                appendLine(device)
                appendLine(status)
            }
            val result = LogStore.export(this, extra)
            runOnUiThread {
                if (!result.ok || result.shareUri == null) {
                    toast("Xuất log lỗi: ${result.message}")
                    return@runOnUiThread
                }
                lastExportUri = result.shareUri
                toast(result.message)
                binding.txtLog.text = "Đã xuất ${result.fileName}\n${result.message}"
                if (share) {
                    try {
                        startActivity(LogStore.shareIntent(this, result.shareUri))
                    } catch (t: Throwable) {
                        toast("Không mở được chia sẻ: ${t.message}")
                    }
                }
            }
        }.start()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
