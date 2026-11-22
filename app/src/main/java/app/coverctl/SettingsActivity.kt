package app.coverctl

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import app.coverctl.databinding.ActivitySettingsBinding
import kotlin.concurrent.thread

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val langs = LocaleHelper.languages()
    private var languageReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.txtTitle.text = getString(R.string.settings_title)
        binding.txtVersion.text = getString(
            R.string.version_label,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )
        binding.txtGithub.text = getString(R.string.github_repo_label, BuildConfig.GITHUB_REPO)

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
                    if (tag == Prefs.languageTag(this@SettingsActivity)) return
                    Prefs.setLanguageTag(this@SettingsActivity, tag)
                    LocaleHelper.apply(tag)
                    recreate()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        binding.btnCheckUpdate.setOnClickListener { checkUpdate() }
        binding.btnOpenGithub.setOnClickListener {
            openUrl("https://github.com/${BuildConfig.GITHUB_REPO}")
        }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun checkUpdate() {
        binding.btnCheckUpdate.isEnabled = false
        binding.txtUpdateStatus.text = getString(R.string.update_checking)
        thread {
            try {
                val result = UpdateChecker.check(BuildConfig.VERSION_NAME)
                runOnUiThread {
                    binding.btnCheckUpdate.isEnabled = true
                    if (result.newer) {
                        binding.txtUpdateStatus.text = getString(R.string.update_available, result.tagName)
                        AlertDialog.Builder(this)
                            .setMessage(getString(R.string.update_available, result.tagName))
                            .setPositiveButton(R.string.update_open) { _, _ -> openUrl(result.htmlUrl) }
                            .setNegativeButton(R.string.update_later, null)
                            .show()
                    } else {
                        binding.txtUpdateStatus.text = getString(R.string.update_latest, result.tagName)
                        Toast.makeText(
                            this,
                            getString(R.string.update_latest, result.tagName),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
