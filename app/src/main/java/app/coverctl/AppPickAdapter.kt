package app.coverctl

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView

data class PickApp(
    val packageName: String,
    val label: String,
    val subtitle: String = "",
    val icon: Drawable?
)

class AppPickAdapter(
    private val apps: List<PickApp>,
    private val selected: MutableSet<String>
) : BaseAdapter() {
    override fun getCount(): Int = apps.size
    override fun getItem(position: Int): PickApp = apps[position]
    override fun getItemId(position: Int): Long = apps[position].packageName.hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_pick, parent, false)
        val app = apps[position]
        view.findViewById<ImageView>(R.id.imgIcon).setImageDrawable(
            app.icon ?: parent.context.packageManager.defaultActivityIcon
        )
        view.findViewById<TextView>(R.id.txtLabel).text = app.label
        view.findViewById<TextView>(R.id.txtPackage).text =
            app.subtitle.ifBlank { app.packageName }
        view.findViewById<CheckBox>(R.id.checkSelect).isChecked =
            selected.contains(app.packageName)
        return view
    }

    companion object {
        fun loadIcon(pm: PackageManager, packageName: String): Drawable? =
            try {
                pm.getApplicationIcon(packageName)
            } catch (_: Exception) {
                null
            }
    }
}
