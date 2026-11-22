package app.galaxywifi

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager

class GalaxyWifiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LocaleHelper.applySaved(this)
        watchNetworkAndBattery()
    }

    private fun watchNetworkAndBattery() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = refresh()
                    override fun onLost(network: Network) = refresh()
                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
                }
            )
        } catch (_: Exception) {
        }
        try {
            registerReceiver(
                object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        MobileWifiWidgetProvider.refreshAll(this@GalaxyWifiApp)
                    }
                },
                IntentFilter().apply {
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                    addAction(BatteryManager.ACTION_CHARGING)
                    addAction(BatteryManager.ACTION_DISCHARGING)
                }
            )
        } catch (_: Exception) {
        }
    }

    private fun refresh() = MobileWifiWidgetProvider.refreshAll(this)
}
