package app.coverctl

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.concurrent.thread

/** Panel chế độ màn trên cover (display 1). */
class CoverPanelActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        val root = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
            setBackgroundColor(0xFFF2F2F7.toInt())
        }
        col.addView(TextView(this).apply {
            text = getString(R.string.section_screen_mode)
            setTextColor(Color.BLACK)
            textSize = 22f
            setPadding(0, 0, 0, dp(12))
        })
        col.addView(modeBtn(R.string.mode_main) { applyMode(ScreenModes.Mode.MAIN) })
        col.addView(modeBtn(R.string.mode_cover) { applyMode(ScreenModes.Mode.COVER) })
        col.addView(modeBtn(R.string.mode_dual) { applyMode(ScreenModes.Mode.DUAL) })

        val keep = MaterialSwitch(this).apply {
            text = getString(R.string.btn_keep_dual)
            isChecked = Prefs.keepDual(this@CoverPanelActivity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setKeepDual(this@CoverPanelActivity, checked)
                if (checked && Prefs.screenMode(this@CoverPanelActivity) == ScreenModes.Mode.DUAL.id) {
                    applyMode(ScreenModes.Mode.DUAL)
                } else if (!checked) {
                    DualKeepService.stop(this@CoverPanelActivity)
                }
            }
        }
        col.addView(keep)
        col.addView(modeBtn(R.string.btn_restore_nav) {
            thread {
                Shell.mode = Prefs.privilegeMode(this@CoverPanelActivity)
                ScreenModes.grantOverlay(this@CoverPanelActivity)
                CoverNavOverlayService.start(this@CoverPanelActivity, Prefs.screenMode(this@CoverPanelActivity))
                CoverCommands.restoreCoverNavigation(DeviceProfile.fromId(Prefs.profileId(this@CoverPanelActivity)))
                runOnUiThread { toast(getString(R.string.toast_nav_overlay_on)) }
            }
        })
        col.addView(modeBtn(R.string.btn_doctor) {
            thread {
                Shell.mode = Prefs.privilegeMode(this@CoverPanelActivity)
                val msg = ScreenModes.doctor(this@CoverPanelActivity)
                runOnUiThread { toast(msg) }
            }
        })
        root.addView(col)
        setContentView(root)
    }

    private fun modeBtn(labelRes: Int, onClick: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            text = getString(labelRes)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            setOnClickListener { onClick() }
        }

    private fun applyMode(mode: ScreenModes.Mode) {
        thread {
            Shell.mode = Prefs.privilegeMode(this)
            val r = ScreenModes.apply(this, mode, keepDual = Prefs.keepDual(this))
            runOnUiThread {
                toast(r.text().lines().lastOrNull { it.isNotBlank() } ?: mode.id)
                if (mode == ScreenModes.Mode.MAIN) finish()
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg.take(200), Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
