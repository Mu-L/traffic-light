package com.leekleak.trafficlight.integrations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object IPerf3Provider {
    init {
        System.loadLibrary("traffic_light")
    }

    suspend fun runIperfSuspend(arguments: Array<String>, callback: IperfCallback) {
        withContext(Dispatchers.IO) {
            runIperf(arguments, callback)
        }
    }

    @JvmStatic
    private external fun runIperf(arguments: Array<String>, callback: IperfCallback)
}

interface IperfCallback {
    fun onOutput(line: String)
    fun onError(error: String)
    fun onComplete()
}
