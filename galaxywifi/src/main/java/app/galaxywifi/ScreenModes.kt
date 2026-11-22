package app.galaxywifi

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build

object ScreenModes {
    const val COVER_DISPLAY = 1

    fun launchOnCover(context: Context, intent: Intent): Boolean {
        val i = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opts = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 26) {
            opts.launchDisplayId = COVER_DISPLAY
        }
        return try {
            context.startActivity(i, opts.toBundle())
            true
        } catch (_: Exception) {
            try {
                context.startActivity(i)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun launchSelfOnCover(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        return launchOnCover(context, launch)
    }
}
