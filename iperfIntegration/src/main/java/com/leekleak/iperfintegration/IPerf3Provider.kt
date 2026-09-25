package com.leekleak.iperfintegration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.Executors
import kotlin.coroutines.resume

object IPerf3Provider {
    init {
        System.loadLibrary("iperf_integration")
    }

    private val testDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "iperf-test").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    val running: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val stopping: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val json = Json { ignoreUnknownKeys = true }

    suspend fun runTest(arguments: Array<String>, callback: IperfCallback) =
        suspendCancellableCoroutine { cont ->
            val wrappedCallback = object : IperfCallbackInternal {
                override fun onOutput(output: String) {
                    val results: List<IntervalResult> = json.decodeFromString(
                        ListSerializer(IntervalResultSerializer), output
                    )
                    callback.onOutput(results)
                }

                override fun onComplete() {
                    callback.onComplete()
                    running.value = false
                    stopping.value = false
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

            running.value = true
            CoroutineScope(testDispatcher).launch {
                runTestInternal(arguments, wrappedCallback)
            }

            cont.invokeOnCancellation {
                stopTestInternal()
                running.value = false
                stopping.value = false
            }
        }

    fun stopTest() {
        stopping.value = true
        stopTestInternal()
    }

    @JvmStatic
    private external fun runTestInternal(arguments: Array<String>, callback: IperfCallbackInternal)
    @JvmStatic
    private external fun stopTestInternal()
}

private interface IperfCallbackInternal {
    fun onOutput(output: String)
    fun onError(error: String)
    fun onComplete()
}

interface IperfCallback {
    fun onOutput(results: List<IntervalResult>)
    fun onError(error: String)
    fun onComplete()
}
