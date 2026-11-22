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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Date
import kotlin.concurrent.thread

/**
 * Overlay trên display 1:
 * - Thanh trạng thái (không chặn touch)
 * - Nav Back/Home/Recents + cạnh OHO+
 * - FLAG_NOT_TOUCH_MODAL để icon app vẫn bấm được
 * - input -d 1 để phím gửi đúng cover
 */
class CoverNavOverlayService : Service() {
    companion object {
        const val EXTRA_MODE = "mode"
        const val NOTIF_ID = 42
        private const val CHANNEL = "cover_nav"
        @Volatile var running: Boolean = false
            private set

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
    private var statusBar: View? = null
    private var navBar: View? = null
    private var floatBtn: View? = null
    private var leftEdge: View? = null
    private var rightEdge: View? = null
    private var statusClock: TextView? = null
    private var statusInfo: TextView? = null
    private var mode: String = "dual"
    private var overlaysReady = false
    private val handler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            refreshStatusTexts()
            handler.postDelayed(this, 15_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mode = intent?.getStringExtra(EXTRA_MODE) ?: Prefs.screenMode(this)
        running = true
        startAsForeground()
        if (!overlaysReady) {
            showOverlays()
            overlaysReady = true
            handler.removeCallbacks(clockTick)
            handler.post(clockTick)
        } else {
            refreshStatusTexts()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        overlaysReady = false
        handler.removeCallbacks(clockTick)
        removeOverlays()
        super.onDestroy()
    }

    private fun overlayFlags(touchable: Boolean): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!touchable) {
            f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return f
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

        addStatusBar(ctx)
        if (Prefs.showNavBar(this)) addNavBar(ctx)
        if (Prefs.edgeGestures(this)) {
            addEdgeHandle(ctx, left = true)
            if (Prefs.edgeBothSides(this)) addEdgeHandle(ctx, left = false)
        }
        addFloatButton(ctx)
        refreshStatusTexts()
    }

    /** Thanh trạng thái cover — không nhận touch để không chặn icon. */
    private fun addStatusBar(ctx: Context) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4))
            background = GradientDrawable().apply {
                setColor(0xCC111111.toInt())
            }
        }
        statusClock = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        // Không hiện pin — góc phải hệ thống đã có biểu tượng pin
        statusInfo = null
        row.addView(statusClock)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            overlayFlags(touchable = false),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 0
        }
        windowManager?.addView(row, params)
        statusBar = row
    }

    private fun refreshStatusTexts() {
        statusClock?.text = DateFormat.getTimeFormat(this).format(Date())
    }

    private fun addEdgeHandle(ctx: Context, left: Boolean) {
        val width = dp(ctx, 14)
        val height = dp(ctx, 180)
        val handle = View(ctx).apply {
            background = GradientDrawable().apply {
                setColor(0x440071E3)
                cornerRadius = dp(ctx, 8).toFloat()
            }
            alpha = 0.45f
        }
        val params = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            overlayFlags(touchable = true),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (left) Gravity.CENTER_VERTICAL or Gravity.START
            else Gravity.CENTER_VERTICAL or Gravity.END
            x = dp(ctx, 1)
        }
        handle.setOnTouchListener(EdgeSwipeListener(left))
        windowManager?.addView(handle, params)
        if (left) leftEdge = handle else rightEdge = handle
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
        fun navBtn(label: String, action: () -> Unit): TextView =
            TextView(ctx).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
        bar.addView(navBtn("◀") { coverInput("keyevent 4") })
        bar.addView(navBtn("●") {
            thread {
                Shell.mode = Prefs.privilegeMode(this@CoverNavOverlayService)
                CoverCommands.openLauncherOnCover(
                    DeviceProfile.fromId(Prefs.profileId(this@CoverNavOverlayService))
                )
            }
        })
        bar.addView(navBtn("▮") { coverInput("keyevent 187") })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            overlayFlags(touchable = true),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(ctx, 4)
        }
        windowManager?.addView(bar, params)
        navBar = bar
    }

    private fun addFloatButton(ctx: Context) {
        val size = dp(ctx, 44)
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
            overlayFlags(touchable = true),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(ctx, 8)
            y = dp(ctx, 36)
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

    /** Gửi input đúng display cover (1). */
    private fun coverInput(args: String) {
        thread {
            Shell.mode = Prefs.privilegeMode(this)
            val d = ScreenModes.COVER_DISPLAY
            Shell.exec("input -d $d $args 2>/dev/null || input $args")
        }
    }

    private fun removeOverlays() {
        listOf(statusBar, navBar, floatBtn, leftEdge, rightEdge).forEach { v ->
            try {
                v?.let { windowManager?.removeView(it) }
            } catch (_: Exception) {
            }
        }
        statusBar = null
        navBar = null
        floatBtn = null
        leftEdge = null
        rightEdge = null
        statusClock = null
        statusInfo = null
        windowManager = null
    }

    private fun runGestureAction(action: String) {
        thread {
            Shell.mode = Prefs.privilegeMode(this)
            val d = ScreenModes.COVER_DISPLAY
            when (action) {
                "back" -> Shell.exec("input -d $d keyevent 4 2>/dev/null || input keyevent 4")
                "home" -> CoverCommands.openLauncherOnCover(
                    DeviceProfile.fromId(Prefs.profileId(this))
                )
                "recents" -> Shell.exec("input -d $d keyevent 187 2>/dev/null || input keyevent 187")
                "notif" -> Shell.exec("cmd statusbar expand-notifications")
                "qs" -> Shell.exec("cmd statusbar expand-settings")
                "screen_off" -> Shell.exec("input keyevent 26")
            }
        }
    }

    private inner class EdgeSwipeListener(private val fromLeft: Boolean) : View.OnTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var tracking = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    tracking = true
                    v.alpha = 0.85f
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 0.45f
                    if (!tracking) return true
                    tracking = false
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    // Cover thường ROTATION_180 — dùng khoảng cách tuyệt đối + hướng vào trong màn
                    val inward = if (fromLeft) dx > 0 else dx < 0
                    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (dist < dp(v.context, 24)) return true
                    // Nếu không rõ inward (xoay), vẫn chấp nhận vuốt đủ dài
                    if (!inward && dist < dp(v.context, 48)) return true
                    val longSwipe = dist > dp(v.context, 80)
                    val absDx = kotlin.math.abs(dx)
                    val absDy = kotlin.math.abs(dy)
                    val action = when {
                        absDy > absDx * 1.05f && dy < 0 -> if (longSwipe) "qs" else "home"
                        absDy > absDx * 1.05f && dy > 0 -> if (longSwipe) "screen_off" else "recents"
                        else -> if (longSwipe) "notif" else "back"
                    }
                    runGestureAction(action)
                    return true
                }
            }
            return true
        }
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
                        params.y = (startY + dy.toInt()).coerceIn(0, maxY.coerceAtLeast(0))
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
