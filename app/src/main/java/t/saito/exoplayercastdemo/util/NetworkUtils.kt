package t.saito.exoplayercastdemo.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    private const val TAG = "NetworkUtils"

    /**
     * NetworkInterface経由でローカルIPアドレスを取得
     * WifiManager.connectionInfo.ipAddressは非推奨のため使用しない
     */
    fun getLocalIpAddress(context: Context): String? {
        try {
            // 方法1: NetworkInterfaceから取得（推奨）- wlan/ethを優先
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
                if (networkInterface.name.startsWith("wlan") ||
                    networkInterface.name.startsWith("eth")) {
                    networkInterface.inetAddresses.toList().forEach { inetAddress ->
                        if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                            Log.d(TAG, "Found IP on ${networkInterface.name}: ${inetAddress.hostAddress}")
                            return inetAddress.hostAddress
                        }
                    }
                }
            }

            // 方法2: 全インターフェースから探索（フォールバック）
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
                networkInterface.inetAddresses.toList().forEach { inetAddress ->
                    if (!inetAddress.isLoopbackAddress &&
                        inetAddress is Inet4Address &&
                        !inetAddress.hostAddress.orEmpty().startsWith("127.")) {
                        Log.d(TAG, "Found IP on ${networkInterface.name} (fallback): ${inetAddress.hostAddress}")
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get IP address", e)
        }
        Log.w(TAG, "Could not determine local IP address")
        return null
    }

    /**
     * Wi-Fiネットワークに接続されているか確認
     */
    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Castデバイスと同一ネットワーク上にいるか検証
     */
    fun validateNetworkForCast(context: Context): Boolean {
        if (!isWifiConnected(context)) {
            Log.w(TAG, "Wi-Fi not connected - cannot cast local media")
            return false
        }
        val ip = getLocalIpAddress(context)
        if (ip == null) {
            Log.w(TAG, "Could not get local IP address - cannot cast local media")
            return false
        }
        return true
    }
}
