package app.galaxywifi

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Token để MediaSessionManager đọc phiên đang phát.
 * Người dùng cần bật trong Cài đặt → Thông báo → Truy cập thông báo.
 */
class MediaNotifListener : NotificationListenerService() {
    override fun onListenerConnected() {
        MobileWifiWidgetProvider.refreshAll(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        MobileWifiWidgetProvider.refreshAll(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        MobileWifiWidgetProvider.refreshAll(this)
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context, MediaNotifListener::class.java)

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()
            val cn = component(context).flattenToString()
            return flat.split(':').any { it.equals(cn, ignoreCase = true) }
        }

        fun openSettings(context: Context): Boolean {
            return try {
                context.startActivity(
                    android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
