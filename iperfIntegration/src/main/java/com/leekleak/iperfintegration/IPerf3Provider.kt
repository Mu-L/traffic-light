package com.leekleak.iperfintegration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object IPerf3Provider {
    init {
        System.loadLibrary("traffic_light")
    }

    suspend fun runTest(arguments: Array<String>, callback: IperfCallback) =
        suspendCancellableCoroutine { cont ->
            val wrappedCallback = object : IperfCallback {
                override fun onOutput(line: String) {
                    callback.onOutput(line)
                }

                override fun onComplete() {
                    callback.onComplete()
                    if (cont.isActive) {
                        cont.resume(Unit)
                    }
                }

                override fun onError(error: String) {
                    callback.onError(error)
                    if (cont.isActive) {
                        cont.cancel()
                    }
                }
            }

            val job = CoroutineScope(Dispatchers.IO).launch {
                runTestInternal(arguments, wrappedCallback)
            }

            cont.invokeOnCancellation {
                stopTest()
                job.cancel()
            }

            job.start()
        }

    @JvmStatic
    private external fun runTestInternal(arguments: Array<String>, callback: IperfCallback)
    @JvmStatic
    external fun stopTest()
}

interface IperfCallback {
    fun onOutput(line: String)
    fun onError(error: String)
    fun onComplete()
}
