package app.galaxywifi

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Hiện QR Wi‑Fi hotspot trên cover để thiết bị khác quét kết nối. */
class HotspotQrActivity : AppCompatActivity() {
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
        setContentView(R.layout.activity_hotspot_qr)

        val status = DeviceNetStatus.read(this)
        val ssid = status.apSsid.ifBlank { getString(R.string.widget_ap_unnamed) }
        val pass = status.apPassword
        val payload = wifiQrPayload(ssid, pass)

        findViewById<TextView>(R.id.txtQrSsid).text = ssid
        findViewById<TextView>(R.id.txtQrPass).text =
            if (pass.isNotBlank()) pass else getString(R.string.widget_pass_unknown)
        findViewById<ImageView>(R.id.imgQr).setImageBitmap(encodeQr(payload, 512))
        findViewById<TextView>(R.id.btnQrClose).setOnClickListener { finish() }
    }

    companion object {
        fun wifiQrPayload(ssid: String, password: String): String {
            val s = escape(ssid)
            return if (password.isBlank()) {
                "WIFI:T:nopass;S:$s;;"
            } else {
                "WIFI:T:WPA;S:$s;P:${escape(password)};;"
            }
        }

        private fun escape(v: String): String =
            v.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")

        fun encodeQr(text: String, size: Int): Bitmap {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            return bmp
        }
    }
}
