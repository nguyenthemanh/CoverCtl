package app.coverctl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

class ModeSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val mode = ScreenModes.Mode.fromId(intent?.getStringExtra(EXTRA_MODE))
        val pending = goAsync()
        thread {
            try {
                Shell.mode = Prefs.privilegeMode(context)
                ScreenModes.apply(context, mode, keepDual = Prefs.keepDual(context))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
    }
}
