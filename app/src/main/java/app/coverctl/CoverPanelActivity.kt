package app.coverctl

import android.content.Intent
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

/** Panel nhanh trên cover. */
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
            text = getString(R.string.app_name)
            setTextColor(Color.BLACK)
            textSize = 22f
            setPadding(0, 0, 0, dp(12))
        })
        col.addView(btn(R.string.btn_open_galaxy_wifi) {
            val launch = packageManager.getLaunchIntentForPackage("app.galaxywifi")
            if (launch != null) {
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                toast(getString(R.string.toast_install_galaxy_wifi))
            }
        })
        col.addView(btn(R.string.btn_overlay_perm) {
            ScreenModes.openOverlaySettings(this)
        })
        col.addView(btn(R.string.btn_app_settings) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        root.addView(col)
        setContentView(root)
    }

    private fun btn(labelRes: Int, onClick: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            text = getString(labelRes)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            setOnClickListener { onClick() }
        }

    private fun toast(msg: String) =
        Toast.makeText(this, msg.take(200), Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
