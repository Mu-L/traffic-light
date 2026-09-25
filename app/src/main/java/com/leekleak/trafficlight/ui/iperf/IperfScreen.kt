@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.leekleak.trafficlight.ui.iperf

import android.net.InetAddresses
import android.os.Build
import android.util.Patterns
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes.Companion.Cookie12Sided
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leekleak.iperfintegration.IPerf3Provider
import com.leekleak.iperfintegration.IperfCallback
import com.leekleak.trafficlight.R
import com.leekleak.trafficlight.database.IPerfEntry
import com.leekleak.trafficlight.ui.components.BackAction
import com.leekleak.trafficlight.ui.components.HazeScaffold
import com.leekleak.trafficlight.ui.navigation.NAVBAR_PADDING
import com.leekleak.trafficlight.ui.settings.FancyDialog
import com.leekleak.trafficlight.ui.theme.card
import com.leekleak.trafficlight.util.CategoryTitleSmallText
import com.leekleak.trafficlight.util.SearchField
import com.leekleak.trafficlight.util.iconToggleButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun IperfScreen(
    viewModel: IperfScreenVM
) {
    val myIp by viewModel.ipFlow.collectAsState(null)

    HazeScaffold(
        title = stringResource(R.string.iperf3),
        backAction = BackAction.None,
        scrollState = null,
        extraPadding = PaddingValues(bottom = NAVBAR_PADDING),
    ) { contentPadding ->
        var showServer by remember { mutableStateOf(false) }

        val entries by viewModel.iperfEntries.collectAsStateWithLifecycle()
        val selectedEntry = entries.firstOrNull { it.selected }

        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ButtonGroup(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    4.dp,
                    Alignment.CenterHorizontally
                ),
                expandedRatio = 0.05f,
                overflowIndicator = {}
            ) {
                iconToggleButton(
                    selected = !showServer,
                    fillWidth = true,
                    onSelect = { showServer = false }
                ) {
                    Icon(painterResource(R.drawable.arrow_downward_alt), null)
                    Text(stringResource(R.string.client))
                }
                iconToggleButton(
                    selected = showServer,
                    fillWidth = true,
                    onSelect = { showServer = true }
                ) {
                    Icon(painterResource(R.drawable.arrow_upward_alt), null)
                    Text(stringResource(R.string.server))
                }
            }

            AnimatedContent(showServer) {
                if (!it) {
                    ClientScreen(
                        selectedEntry = selectedEntry,
                        entries = entries,
                        selectEntry = viewModel::selectEntry,
                        deleteEntry = viewModel::deleteEntry,
                        myIp = myIp,
                    )
                } else {
                    ServerScreen(
                        myIp = myIp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientScreen(
    selectedEntry: IPerfEntry?,
    entries: List<IPerfEntry>,
    selectEntry: (IPerfEntry) -> Unit,
    deleteEntry: (IPerfEntry) -> Unit,
    myIp: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showEntrySelector by remember { mutableStateOf(false) }
    var showEntryCreator by remember { mutableStateOf(false) }
    var showEntryDeletion: IPerfEntry? by remember { mutableStateOf(null) }
    var output by remember { mutableStateOf("") }

    if (showEntrySelector) {
        EntrySelectorComponent(
            onDismissRequest = { showEntrySelector = false },
            entries = entries,
            selectEntry = selectEntry,
            setShowEntryDeletion = { showEntryDeletion = it },
            setShowEntryCreator = { showEntryCreator = true }
        )
    }

    showEntryDeletion?.let {
        EntryDeletionComponent(
            onDismissRequest = { showEntryDeletion = null },
            entry = it,
            deleteEntry = deleteEntry
        )
    }

    if (showEntryCreator) {
        EntryCreatorComponent(
            onDismissRequest = {
                showEntryCreator = false
            },
            entries = entries,
            selectEntry = selectEntry,
            myIp = myIp
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            shape = MaterialTheme.shapes.large,
            onClick = { showEntrySelector = true },
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 8.dp, bottom = 4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedEntry == null) {
                    Text(text = stringResource(R.string.no_server_selected))
                } else {
                    Column {
                        Text(selectedEntry.name)
                        Text(selectedEntry.ip + ":" + selectedEntry.port)
                    }
                }
                Icon(painterResource(R.drawable.arrow_drop_down), null)
            }
        }

        val iPerfRunning by IPerf3Provider.running.collectAsStateWithLifecycle()
        val iPerfStopping by IPerf3Provider.stopping.collectAsStateWithLifecycle()

        Button(
            modifier = Modifier.size(128.dp),
            onClick = {
                if (!iPerfRunning) {
                    scope.launch {
                        if (selectedEntry == null) return@launch
                        IPerf3Provider.runTest(
                            arrayOf("-c", selectedEntry.ip, "-p", selectedEntry.port),
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
                                            Toast.makeText(context, "Done", Toast.LENGTH_SHORT)
                                                .show()
                                        }
                                    }
                                }
                            }
                        )
                    }
                } else {
                    IPerf3Provider.stopTest()
                }
            },
            enabled = !iPerfStopping,
            shape = Cookie12Sided.toShape()
        ) {
            val modifier = Modifier.size(56.dp)
            AnimatedContent(iPerfRunning) {
                if (it) {
                    Icon(painterResource(R.drawable.stop), null, modifier)
                } else {
                    Icon(painterResource(R.drawable.play_arrow), null, modifier)
                }
            }
        }
        
        Text(text = output)
    }
}

@Composable
private fun ServerScreen(
    myIp: String?,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        myIp?.let { Text(text = it) }
    }
}

@Composable
private fun EntryDeletionComponent(
    onDismissRequest: () -> Unit,
    entry: IPerfEntry,
    deleteEntry: (IPerfEntry) -> Unit
) {
    FancyDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.remove_server),
        icon = painterResource(R.drawable.deleted),
        actionButton = {
            Button(onClick = {
                deleteEntry(entry)
                onDismissRequest()
            }) {
                Text(stringResource(R.string.remove))
            }
        }
    ) {
        Text(stringResource(R.string.remove_server_question))
    }
}

@Composable
private fun EntrySelectorComponent(
    onDismissRequest: () -> Unit,
    entries: List<IPerfEntry>,
    selectEntry: (IPerfEntry) -> Unit,
    setShowEntryDeletion: (IPerfEntry) -> Unit,
    setShowEntryCreator: () -> Unit
) {
    FancyDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.select_server),
        icon = painterResource(R.drawable.language),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(Modifier)
        entries.forEach {
            Column(
                Modifier
                    .fillMaxWidth()
                    .card(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .combinedClickable(
                        onClick = {
                            selectEntry(it)
                            onDismissRequest()
                        },
                        onLongClick = {
                            setShowEntryDeletion(it)
                        }
                    )
                    .padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Text(text = it.name)
                Text(text = it.ip + ":" + it.port)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .card(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = { setShowEntryCreator() })
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.add), null)
            Text(stringResource(R.string.add_server))
        }
    }
}

@Composable
private fun EntryCreatorComponent(
    onDismissRequest: () -> Unit,
    entries: List<IPerfEntry>,
    selectEntry: (IPerfEntry) -> Unit,
    myIp: String?
) {
    val nameFieldState = rememberTextFieldState()
    var nameFieldError: String? by remember { mutableStateOf(null) }
    val ipFieldState = rememberTextFieldState()
    var ipFieldError: String? by remember { mutableStateOf(null) }
    val portFieldState = rememberTextFieldState()
    var portFieldError: String? by remember { mutableStateOf(null) }

    val emptyNameError = stringResource(R.string.name_cannot_be_empty)
    val usedNameError = stringResource(R.string.name_already_used)
    val invalidIpError = stringResource(R.string.invalid_ip_address)
    val invalidPortError = stringResource(R.string.invalid_port)
    FancyDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.add_server),
        icon = painterResource(R.drawable.add),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        actionButton = {
            Button(onClick = {
                val name = nameFieldState.text.toString()
                val validName = name.isNotBlank() && entries.find { it.name == name } == null

                val ip = ipFieldState.text.toString()
                val validIp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    InetAddresses.isNumericAddress(ip)
                } else {
                    Patterns.IP_ADDRESS.matcher(ip).matches()
                }

                val port = portFieldState.text.toString().toIntOrNull()
                val validPort = port != null && port in 0..65535

                nameFieldError = if (!validName) {
                    if (name.isBlank()) {
                        emptyNameError
                    } else {
                        usedNameError
                    }
                } else null
                ipFieldError = if (!validIp) { invalidIpError } else null
                portFieldError = if (!validPort) { invalidPortError } else null
                if (validName && validIp && validPort) {
                    selectEntry(
                        IPerfEntry(
                            name = name,
                            ip = ip,
                            port = portFieldState.text.toString(),
                            selected = true
                        )
                    )
                    onDismissRequest()
                }
            }) {
                Text(stringResource(R.string.save))
            }
        }
    ) {
        Column {
            CategoryTitleSmallText(stringResource(R.string.server_name))
            SearchField(
                textFieldState = nameFieldState,
                placeholder = stringResource(R.string.my_server),
                isError = nameFieldError
            )
        }
        Column {
            CategoryTitleSmallText(stringResource(R.string.server_ip_address))
            val placeholderIP = myIp?.replaceAfterLast(".", "xxx")
            SearchField(
                textFieldState = ipFieldState,
                placeholder = placeholderIP ?: "192.168.xxx.xxx",
                isError = ipFieldError
            )
        }
        Column {
            CategoryTitleSmallText(stringResource(R.string.network_port))
            SearchField(
                textFieldState = portFieldState,
                placeholder = "5201",
                isError = portFieldError
            )
        }
    }
}