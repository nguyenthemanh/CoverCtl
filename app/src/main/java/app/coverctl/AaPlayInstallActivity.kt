package app.coverctl

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Cài APK với attribution giống Google Play (ý tưởng UnknownAuto, MIT).
 * Không copy mã nguồn UnknownAuto — triển khai Kotlin gọn cho CoverCtl.
 */
class AaPlayInstallActivity : AppCompatActivity() {
    private lateinit var logView: TextView
    private lateinit var btnInstall: Button
    private var stagedApk: File? = null
    private var targetPackage: String? = null
    private var verifyPending = false
    private var sawPause = false

    private val pickApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            append("Đã hủy chọn file.")
            return@registerForActivityResult
        }
        Thread {
            val ok = stageUri(uri)
            runOnUiThread {
                if (ok) {
                    btnInstall.isEnabled = true
                    append("Sẵn sàng. Bấm Cài với dấu Play rồi xác nhận trên màn hình hệ thống.")
                } else {
                    btnInstall.isEnabled = false
                }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply {
            text = getString(R.string.aa_install_title)
            textSize = 20f
            setPadding(0, 0, 0, 24)
        }
        val hint = TextView(this).apply {
            text = getString(R.string.aa_install_hint)
            textSize = 13f
            setPadding(0, 0, 0, 24)
        }
        val btnPick = Button(this).apply {
            text = getString(R.string.aa_install_pick)
            setOnClickListener {
                pickApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*"))
            }
        }
        btnInstall = Button(this).apply {
            text = getString(R.string.aa_install_go)
            isEnabled = false
            setOnClickListener { startPlayInstall() }
        }
        val btnUa = Button(this).apply {
            text = getString(R.string.btn_aa_unknownauto)
            setOnClickListener {
                append(AndroidAutoCommands.openUnknownAuto(this@AaPlayInstallActivity))
            }
        }
        logView = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 32, 0, 0)
        }
        root.addView(title)
        root.addView(hint)
        root.addView(btnPick)
        root.addView(btnInstall)
        root.addView(btnUa)
        root.addView(logView)
        scroll.addView(root)
        setContentView(scroll)

        append("=== Cài APK như từ Play (Android Auto) ===")
        append("Máy: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        intent?.data?.let { uri ->
            Thread {
                val ok = stageUri(uri)
                runOnUiThread {
                    if (ok) btnInstall.isEnabled = true
                }
            }.start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (verifyPending) sawPause = true
    }

    override fun onResume() {
        super.onResume()
        if (verifyPending && sawPause) {
            verifyPending = false
            sawPause = false
            logView.postDelayed({ verifyResult() }, 700)
        }
    }

    private fun stageUri(uri: Uri): Boolean {
        return try {
            val out = File(cacheDir, "aa_play_install.apk")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            } ?: run {
                runOnUiThread { append("Không đọc được file.") }
                return false
            }
            val pi = packageManager.getPackageArchiveInfo(out.absolutePath, 0)
            if (pi?.packageName == null) {
                runOnUiThread { append("File không phải APK hợp lệ.") }
                return false
            }
            pi.applicationInfo?.sourceDir = out.absolutePath
            pi.applicationInfo?.publicSourceDir = out.absolutePath
            val label = pi.applicationInfo?.loadLabel(packageManager)?.toString() ?: pi.packageName
            stagedApk = out
            targetPackage = pi.packageName
            runOnUiThread {
                append("Đã nạp: $label")
                append("  package=${pi.packageName}")
                append("  version=${pi.versionName} (${versionCodeOf(pi)})")
            }
            true
        } catch (t: Throwable) {
            runOnUiThread { append("Lỗi nạp APK: ${t.message}") }
            false
        }
    }

    private fun versionCodeOf(pi: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode
        else @Suppress("DEPRECATION") pi.versionCode.toLong()

    private fun startPlayInstall() {
        val apk = stagedApk
        val pkg = targetPackage
        if (apk == null || pkg == null) {
            Toast.makeText(this, R.string.aa_install_need_apk, Toast.LENGTH_SHORT).show()
            return
        }
        if (!AndroidAutoCommands.ensureInstallPermission(this)) {
            append("Cần bật «Cài app không rõ nguồn» cho CoverCtl, rồi bấm Cài lại.")
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", apk)
        grantToInstallers(uri)
        val installer = systemInstallerActivity(uri)
        val usedShell = Shell.available() && Build.VERSION.SDK_INT >= 35 && installer != null
        verifyPending = true
        sawPause = false
        if (usedShell) {
            append("Shizuku: mở installer hệ thống với installer=Play…")
            val cmd = buildString {
                append("/system/bin/am start")
                append(" -n ${installer!!.flattenToString()}")
                append(" -a android.intent.action.INSTALL_PACKAGE")
                append(" -d ${shellQuote(uri.toString())}")
                append(" -t application/vnd.android.package-archive")
                append(" --es android.intent.extra.INSTALLER_PACKAGE_NAME ${AndroidAutoCommands.VENDING}")
                append(" --ez android.intent.extra.NOT_UNKNOWN_SOURCE true")
                append(" --ei android.intent.extra.INSTALL_REASON 1")
                append(" --es android.intent.extra.REFERRER_NAME android-app://${AndroidAutoCommands.VENDING}")
            }
            Thread {
                val r = Shell.exec(cmd)
                runOnUiThread {
                    append(r.text())
                    append("Xác nhận Install / Update trên máy (Play Protect → Cài không quét nếu hỏi).")
                }
            }.start()
            return
        }
        append("Mở installer hệ thống (EXTRA installer=Play)…")
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            putExtra("android.intent.extra.INSTALLER_PACKAGE_NAME", AndroidAutoCommands.VENDING)
            putExtra("android.content.pm.extra.VERIFICATION_INSTALLER_PACKAGE", AndroidAutoCommands.VENDING)
            putExtra("android.intent.extra.INSTALL_REASON", 1)
            putExtra(Intent.EXTRA_REFERRER_NAME, "android-app://${AndroidAutoCommands.VENDING}")
            putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${AndroidAutoCommands.VENDING}"))
            putExtra(
                "android.intent.extra.ORIGINATING_URI",
                Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
            )
            if (Build.VERSION.SDK_INT >= 34) {
                putExtra("android.content.pm.extra.REQUEST_UPDATE_OWNERSHIP", true)
            }
            installer?.packageName?.let { setPackage(it) }
        }
        try {
            startActivity(intent)
            append("Chạm Install trên hộp thoại hệ thống.")
        } catch (t: Throwable) {
            verifyPending = false
            append("Không mở được installer: ${t.message}")
            append("Thử UnknownAuto nếu lỗi tiếp.")
        }
    }

    private fun verifyResult() {
        val pkg = targetPackage ?: return
        val src = AndroidAutoCommands.installSource(this, pkg)
        if (src == null) {
            append("✘ Chưa thấy $pkg sau khi cài (hủy hoặc lỗi).")
            return
        }
        append("— Kết quả —")
        append("initiating=${src.initiating ?: "?"}")
        append("installing=${src.installing ?: "?"}")
        if (src.aaAcceptLikely) {
            append("✔ Android Auto khả năng chấp nhận (dấu Play đủ).")
        } else {
            append("✘ Thiếu dấu Play đầy đủ.")
            append("→ Mở UnknownAuto, Load cùng APK, Install lại (ổn định hơn trên mọi máy).")
        }
    }

    private fun grantToInstallers(uri: Uri) {
        val sys = systemInstallerActivity(uri)
        val pkgs = listOfNotNull(
            AndroidAutoCommands.GOOGLE_INSTALLER,
            AndroidAutoCommands.AOSP_INSTALLER,
            AndroidAutoCommands.VENDING,
            sys?.packageName
        ).distinct()
        for (p in pkgs) {
            try {
                grantUriPermission(p, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
        }
    }

    private fun systemInstallerActivity(uri: Uri): ComponentName? {
        val probe = Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(
            uri,
            "application/vnd.android.package-archive"
        )
        val handlers: List<ResolveInfo> = try {
            packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Throwable) {
            return null
        }
        var best: ComponentName? = null
        var bestRank = Int.MAX_VALUE
        for (ri in handlers) {
            val p = ri.activityInfo.packageName
            if (!isSystemApp(p)) continue
            val rank = when (p) {
                AndroidAutoCommands.GOOGLE_INSTALLER -> 0
                AndroidAutoCommands.AOSP_INSTALLER -> 1
                else -> 2
            }
            if (rank < bestRank) {
                best = ComponentName(p, ri.activityInfo.name)
                bestRank = rank
            }
        }
        return best
    }

    private fun isSystemApp(pkg: String): Boolean =
        try {
            (packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (_: Throwable) {
            false
        }

    private fun append(line: String) {
        logView.append(line)
        logView.append("\n")
        LogStore.append("AA-install", line)
    }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
