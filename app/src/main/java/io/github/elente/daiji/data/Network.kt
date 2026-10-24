package io.github.elente.daiji.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

object Network {
    private const val UNKNOWN_SSID = "<unknown ssid>"
    const val WIFI = "Wi-Fi"
    const val ETHERNET = "Ethernet"
    const val OFFLINE = "Offline"
    const val ONLINE = "Online"

    /**
     * The Wi-Fi network's name, or the connection type. Android 10 and later hide the name unless the app
     * holds ACCESS_FINE_LOCATION (granted over ADB on a TV) and location is on, so "Wi-Fi" is the fallback.
     */
    fun label(context: Context): String {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return OFFLINE
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return OFFLINE
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> wifiName(context) ?: WIFI
            else -> ONLINE
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiName(context: Context): String? {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        val ssid = runCatching { wifi.connectionInfo?.ssid }.getOrNull() ?: return null
        return ssid.removeSurrounding("\"").takeUnless { it.isBlank() || it == UNKNOWN_SSID }
    }
}
