package app.galaxywifi

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Tất cả app trên cover.
 * Tự làm mới khi cài/gỡ app hoặc quay lại màn hình.
 * Nhấn giữ app → ghim vào ô 1–4 trên widget.
 */
class CoverAppDrawerActivity : AppCompatActivity() {
    private lateinit var grid: GridView
    private lateinit var countTxt: TextView
    private lateinit var search: EditText
    private var all = emptyList<DrawerApp>()
    private var shown = emptyList<DrawerApp>()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reloadApps()
        }
    }

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
        setContentView(R.layout.activity_cover_app_drawer)
        grid = findViewById(R.id.gridApps)
        countTxt = findViewById(R.id.txtAppCount)
        search = findViewById(R.id.editSearch)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter()
            }
        })

        grid.setOnItemClickListener { _, _, position, _ ->
            val app = shown.getOrNull(position) ?: return@setOnItemClickListener
            launchApp(app.packageName)
        }
        grid.setOnItemLongClickListener { _, _, position, _ ->
            val app = shown.getOrNull(position) ?: return@setOnItemLongClickListener true
            pickSlotForApp(app)
            true
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        try {
            unregisterReceiver(packageReceiver)
        } catch (_: Exception) {
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        reloadApps()
    }

    private fun reloadApps() {
        all = loadApps().sortedBy { it.label.lowercase() }
        applyFilter()
    }

    private fun applyFilter() {
        val q = search.text?.toString()?.trim().orEmpty().lowercase()
        shown = if (q.isEmpty()) all else all.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
        bindGrid()
    }

    private fun pickSlotForApp(app: DrawerApp) {
        val labels = Array(4) { i ->
            val pkg = Prefs.favoriteSlots(this).getOrNull(i).orEmpty()
            val name = if (pkg.isEmpty()) getString(R.string.fav_slot_empty)
            else WidgetAppIcons.labelFor(this, pkg)
            getString(R.string.fav_slot_fmt, i + 1, name)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.fav_pick_slot_for, app.label))
            .setItems(labels) { _, which ->
                Prefs.setFavoriteAt(this, which, app.packageName)
                MobileWifiWidgetProvider.refreshAll(this)
                Toast.makeText(
                    this,
                    getString(R.string.fav_slot_set, which + 1, app.label),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun bindGrid() {
        countTxt.text = getString(R.string.app_drawer_count, shown.size)
        grid.adapter = DrawerAdapter(shown)
    }

    private fun loadApps(): List<DrawerApp> {
        val pm = packageManager
        val seen = LinkedHashMap<String, DrawerApp>()
        fun addFrom(intent: Intent) {
            val list = if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            }
            for (ri in list) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName) continue
                if (seen.containsKey(pkg)) continue
                val label = ri.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg }
                val icon = try {
                    ri.loadIcon(pm)
                } catch (_: Exception) {
                    null
                }
                seen[pkg] = DrawerApp(pkg, label, icon)
            }
        }
        addFrom(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        addFrom(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))
        return seen.values.toList()
    }

    private fun launchApp(packageName: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: packageManager.getLeanbackLaunchIntentForPackage(packageName)
        if (launch == null) {
            Toast.makeText(this, R.string.app_drawer_launch_fail, Toast.LENGTH_SHORT).show()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opts = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 26) {
            opts.launchDisplayId = ScreenModes.COVER_DISPLAY
        }
        try {
            startActivity(launch, opts.toBundle())
        } catch (_: Exception) {
            try {
                startActivity(launch)
            } catch (_: Exception) {
                Toast.makeText(this, R.string.app_drawer_launch_fail, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private data class DrawerApp(
        val packageName: String,
        val label: String,
        val icon: Drawable?
    )

    private inner class DrawerAdapter(private val apps: List<DrawerApp>) : BaseAdapter() {
        override fun getCount(): Int = apps.size
        override fun getItem(position: Int): DrawerApp = apps[position]
        override fun getItemId(position: Int): Long = apps[position].packageName.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_cover_app, parent, false)
            val app = apps[position]
            v.findViewById<ImageView>(R.id.imgIcon).setImageDrawable(
                app.icon ?: packageManager.defaultActivityIcon
            )
            v.findViewById<TextView>(R.id.txtLabel).text = app.label
            return v
        }
    }
}
