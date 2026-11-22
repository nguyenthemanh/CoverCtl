package app.galaxywifi

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Chọn 1 app cho ô ưu tiên 1–4 trên widget. */
class PickFavoriteActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SLOT = "slot"
    }

    private var slot = 0
    private var all = emptyList<AppRow>()
    private var shown = emptyList<AppRow>()
    private lateinit var grid: GridView
    private lateinit var countTxt: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        slot = intent.getIntExtra(EXTRA_SLOT, 0).coerceIn(0, 3)
        setContentView(R.layout.activity_cover_app_drawer)
        findViewById<TextView>(R.id.txtDrawerTitle)?.text =
            getString(R.string.fav_pick_title, slot + 1)
        grid = findViewById(R.id.gridApps)
        countTxt = findViewById(R.id.txtAppCount)
        val search = findViewById<EditText>(R.id.editSearch)

        all = loadApps().sortedBy { it.label.lowercase() }
        shown = all
        bindGrid()

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim().orEmpty().lowercase()
                shown = if (q.isEmpty()) all else all.filter {
                    it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
                }
                bindGrid()
            }
        })

        grid.setOnItemClickListener { _, _, position, _ ->
            val app = shown.getOrNull(position) ?: return@setOnItemClickListener
            Prefs.setFavoriteAt(this, slot, app.packageName)
            MobileWifiWidgetProvider.refreshAll(this)
            Toast.makeText(
                this,
                getString(R.string.fav_slot_set, slot + 1, app.label),
                Toast.LENGTH_SHORT
            ).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun bindGrid() {
        countTxt.text = getString(R.string.app_drawer_count, shown.size)
        grid.adapter = Adapter(shown)
    }

    private fun loadApps(): List<AppRow> {
        val pm = packageManager
        val seen = LinkedHashMap<String, AppRow>()
        fun addFrom(intent: Intent) {
            for (ri in pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || seen.containsKey(pkg)) continue
                val label = ri.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg }
                val icon = try {
                    ri.loadIcon(pm)
                } catch (_: Exception) {
                    null
                }
                seen[pkg] = AppRow(pkg, label, icon)
            }
        }
        addFrom(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        addFrom(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))
        return seen.values.toList()
    }

    private data class AppRow(val packageName: String, val label: String, val icon: Drawable?)

    private inner class Adapter(private val apps: List<AppRow>) : BaseAdapter() {
        override fun getCount(): Int = apps.size
        override fun getItem(position: Int): AppRow = apps[position]
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
