package app.coverctl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread

/**
 * Overlay trên display 1: thanh Back/Home/Recents + nút nổi mở CoverPanel.
 * SystemUI Samsung không vẽ NavigationBar trên cover — đây là cách điều hướng thật.
 */
class CoverNavOverlayService : Service() {
    companion object {
        const val EXTRA_MODE = "mode"
        const val NOTIF_ID = 42
        private const val CHANNEL = "cover_nav"

        fun start(context: Context, mode: String) {
            val i = Intent(context, CoverNavOverlayService::class.java).putExtra(EXTRA_MODE, mode)
            try {
                context.startForegroundService(i)
            } catch (_: RuntimeException) {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CoverNavOverlayService::class.java))
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
        }
    }

    private var windowManager: WindowManager? = null
    private var navBar: View? = null
    private var floatBtn: View? = null
    private var mode: String = "dual"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mode = intent?.getStringExtra(EXTRA_MODE) ?: Prefs.screenMode(this)
        startAsForeground()
        if (navBar == null) showOverlays()
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlays()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, getString(R.string.notif_cover_channel), NotificationManager.IMPORTANCE_LOW)
        ch.setSound(null, null)
        ch.enableVibration(false)
        nm.createNotificationChannel(ch)

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, CoverPanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (mode == "cover") getString(R.string.mode_cover) else getString(R.string.mode_dual)
        val notif = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(getString(R.string.notif_cover_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun showOverlays() {
        if (!Settings.canDrawOverlays(this)) return
        val dm = getSystemService(DisplayManager::class.java)
        val cover = dm.getDisplay(ScreenModes.COVER_DISPLAY) ?: return
        val ctx = createDisplayContext(cover)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        windowManager = ctx.getSystemService(WindowManager::class.java)

        addNavBar(ctx)
        addFloatButton(ctx)
    }

    private fun addNavBar(ctx: Context) {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6))
            background = GradientDrawable().apply {
                setColor(0xE6121212.toInt())
                cornerRadius = dp(ctx, 18).toFloat()
            }
            elevation = dp(ctx, 6).toFloat()
        }
        fun navBtn(label: String, action: () -> Unit): TextView {
            return TextView(ctx).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { action() }
            }
        }
        bar.addView(navBtn("◀") { shellKey("input keyevent 4") })
        bar.addView(navBtn("●") {
            thread {
                Shell.mode = Prefs.privilegeMode(this@CoverNavOverlayService)
                CoverCommands.openLauncherOnCover(DeviceProfile.fromId(Prefs.profileId(this@CoverNavOverlayService)))
            }
        })
        bar.addView(navBtn("▮") { shellKey("input keyevent 187") })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(ctx, 4)
        }
        windowManager?.addView(bar, params)
        navBar = bar
    }

    private fun addFloatButton(ctx: Context) {
        val size = dp(ctx, 48)
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xCC007AFF.toInt())
        }
        val view = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            background = circle
            setPadding(size / 5, size / 5, size / 5, size / 5)
            contentDescription = getString(R.string.btn_cover_panel)
        }
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(ctx, 8)
            y = dp(ctx, 100)
        }
        view.setOnTouchListener(DragOrTap(params) { openPanel() })
        windowManager?.addView(view, params)
        floatBtn = view
    }

    private fun openPanel() {
        val intent = Intent(this, CoverPanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opts = android.app.ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 26) {
            opts.launchDisplayId = ScreenModes.COVER_DISPLAY
        }
        startActivity(intent, opts.toBundle())
    }

    private fun shellKey(cmd: String) {
        thread {
            Shell.mode = Prefs.privilegeMode(this)
            Shell.exec(cmd)
        }
    }

    private fun removeOverlays() {
        try {
            navBar?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        try {
            floatBtn?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        navBar = null
        floatBtn = null
        windowManager = null
    }

    private fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    private inner class DragOrTap(
        private val params: WindowManager.LayoutParams,
        private val onTap: () -> Unit
    ) : View.OnTouchListener {
        private var downY = 0f
        private var startY = 0
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    startY = params.y
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downY
                    if (Math.abs(dy) > dp(v.context, 8)) dragging = true
                    if (dragging) {
                        val maxY = (windowManager?.currentWindowMetrics?.bounds?.height() ?: 720) - v.height
                        params.y = startY + dy.toInt()
                        params.y = params.y.coerceIn(0, maxY.coerceAtLeast(0))
                        windowManager?.updateViewLayout(v, params)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) onTap()
                    return true
                }
            }
            return false
        }
    }
}
