package com.leekleak.trafficlight.ui.iperf

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class IperfScreenVM(
    private val connectivityManager: ConnectivityManager,
    private val wifiManager: WifiManager,
): ViewModel() {
    val ipFlow: Flow<String> = callbackFlow {
        var networkCallback: ConnectivityManager.NetworkCallback? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            networkCallback =
                object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                    override fun onLinkPropertiesChanged(
                        network: Network,
                        linkProperties: LinkProperties
                    ) {
                        super.onLinkPropertiesChanged(network, linkProperties)
                        trySend(linkProperties.linkAddresses.joinToString("\n"))
                    }
                }
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } else {
            trySend(Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress))
        }
        awaitClose {
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        }
    }
}