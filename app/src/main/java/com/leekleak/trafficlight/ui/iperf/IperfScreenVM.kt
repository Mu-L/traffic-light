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
import androidx.lifecycle.viewModelScope
import com.leekleak.iperfintegration.IPerf3Provider
import com.leekleak.trafficlight.database.IPerfEntry
import com.leekleak.trafficlight.database.IPerfEntryDao
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IperfScreenVM(
    private val connectivityManager: ConnectivityManager,
    private val wifiManager: WifiManager,
    private val iPerfEntryDao: IPerfEntryDao,
    val iPerf3Provider: IPerf3Provider,
): ViewModel() {
    val ipFlow: Flow<String?> = callbackFlow {
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
                        trySend(linkProperties.linkAddresses.map { it.address }
                            .filterIsInstance<java.net.Inet4Address>()
                            .mapNotNull { it.hostAddress }
                            .joinToString("\n")
                        )
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        trySend(null)
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

    val iperfEntries = iPerfEntryDao.allEntries.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        emptyList()
    )

    fun selectEntry(entry: IPerfEntry) {
        viewModelScope.launch {
            val selected = iperfEntries.value.find { it.selected }
            if (selected != null) {
                iPerfEntryDao.upsert(selected.copy(selected = false))
            }
            iPerfEntryDao.upsert(entry.copy(selected = true))
        }
    }

    fun deleteEntry(entry: IPerfEntry) {
        viewModelScope.launch {
            iPerfEntryDao.delete(entry)
        }
    }
}