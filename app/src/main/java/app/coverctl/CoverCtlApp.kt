package app.coverctl

import android.app.Application

class CoverCtlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LocaleHelper.applySaved(this)
    }
}
