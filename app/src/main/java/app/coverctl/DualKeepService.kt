package app.coverctl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Samsung hủy dual khi tắt màn — vòng lặp re-apply như flip-ctl keep-loop.
 */
class DualKeepService : Service() {
    companion object {
        const val EXTRA_STATE_ID = "state_id"
        private const val NOTIF_ID = 43
        private const val CHANNEL = "dual_keep"

        fun start(context: Context, stateId: Int) {
            Prefs.setKeepDual(context, true)
            Prefs.setKeepDualStateId(context, stateId)
            val i = Intent(context, DualKeepService::class.java).putExtra(EXTRA_STATE_ID, stateId)
            try {
                context.startForegroundService(i)
            } catch (_: RuntimeException) {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            Prefs.setKeepDual(context, false)
            context.stopService(Intent(context, DualKeepService::class.java))
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var stateId: Int = 4
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            threadTick()
            handler.postDelayed(this, 2500L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stateId = intent?.getIntExtra(EXTRA_STATE_ID, Prefs.keepDualStateId(this)) ?: 4
        startAsForeground()
        if (!running) {
            running = true
            handler.post(tick)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_keep_channel), NotificationManager.IMPORTANCE_MIN)
        )
        val notif = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.notif_keep_title))
            .setContentText(getString(R.string.notif_keep_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun threadTick() {
        Thread {
            Shell.mode = Prefs.privilegeMode(this)
            if (!Shell.available()) return@Thread
            val stateDump = Shell.exec("cmd device_state state 2>&1").text()
            if (stateDump.contains("Override state")) return@Thread
            // Camera dual preview — đứng yên
            val cam = Shell.exec(
                "dumpsys activity activities 2>/dev/null | grep topResumedActivity | grep -qE 'com\\.(sec|samsung)\\.android\\.(app\\.)?camera' && echo CAM || echo OK"
            ).stdout
            if (cam.contains("CAM")) return@Thread

            val policy = Shell.exec("dumpsys window policy 2>/dev/null").text()
            val awake = policy.contains("INTERACTIVE_STATE_AWAKE")
            val locked = Regex("""^\s+showing=true\s*$""", RegexOption.MULTILINE).containsMatchIn(policy)
            if (!awake || locked) return@Thread

            Shell.exec("cmd device_state state $stateId >/dev/null 2>&1")
            val now = ScreenModes.currentStateId()
            // Chỉ khởi động overlay nếu chưa chạy — tránh recreate window chặn touch
            if (now == stateId && !CoverNavOverlayService.running) {
                CoverNavOverlayService.start(this, ScreenModes.Mode.DUAL.id)
            }
        }.start()
    }
}
