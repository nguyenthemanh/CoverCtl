package app.coverctl

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleHelper {
    const val SYSTEM = "system"
    const val VI = "vi"
    const val EN = "en"
    const val ZH = "zh"
    const val JA = "ja"

    data class Lang(val tag: String, val label: String)

    fun languages(): List<Lang> = listOf(
        Lang(SYSTEM, "System / Hệ thống / 系统 / システム"),
        Lang(VI, "Tiếng Việt"),
        Lang(EN, "English"),
        Lang(ZH, "中文"),
        Lang(JA, "日本語")
    )

    fun apply(tag: String) {
        val locales = if (tag == SYSTEM || tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun applySaved(context: Context) {
        apply(Prefs.languageTag(context))
    }
}
