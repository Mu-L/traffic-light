package com.leekleak.trafficlight.ui.iperf

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.leekleak.iperfintegration.IPerf3Provider
import com.leekleak.iperfintegration.IperfCallback
import com.leekleak.trafficlight.R
import com.leekleak.trafficlight.ui.components.BackAction
import com.leekleak.trafficlight.ui.components.HazeScaffold
import com.leekleak.trafficlight.ui.navigation.NAVBAR_PADDING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun IperfScreen() {
    HazeScaffold(
        title = stringResource(R.string.today),
        backAction = BackAction.None,
        extraPadding = PaddingValues(bottom = NAVBAR_PADDING),
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var output by remember { mutableStateOf("") }
        TextButton(onClick = {
            scope.launch {
                IPerf3Provider.runTest(
                    arrayOf("-c", "192.168.178.58", "-p", "5201"),
                    object : IperfCallback {
                        override fun onOutput(line: String) {
                            output += line
                        }

                        override fun onError(error: String) {
                            output += error
                        }

                        override fun onComplete() {
                            scope.launch {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
        }) {
            Text("Clock me")
        }
        TextButton(onClick = {

            IPerf3Provider.stopTest()
            Toast.makeText(context, "Stopped", Toast.LENGTH_SHORT).show()
        }) {
            Text("Cancel")
        }
        Text(text = output)
    }
}