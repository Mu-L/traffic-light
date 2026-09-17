package com.leekleak.trafficlight.ui.navigation

import com.leekleak.trafficlight.model.PermissionManager
import com.leekleak.trafficlight.ui.history.History
import com.leekleak.trafficlight.ui.iperf.IperfScreen
import com.leekleak.trafficlight.ui.overview.Overview
import com.leekleak.trafficlight.ui.plans.DataPlanConfig
import com.leekleak.trafficlight.ui.plans.DataPlanConfigVM
import com.leekleak.trafficlight.ui.plans.DataPlans
import com.leekleak.trafficlight.ui.settings.LibraryLicenseScreen
import com.leekleak.trafficlight.ui.settings.NotificationSettingsScreen
import com.leekleak.trafficlight.ui.settings.Settings
import com.leekleak.trafficlight.ui.settings.UsagePermissionRequest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

@OptIn(KoinExperimentalAPI::class)
val navigationModule = module {

    single {
        val permissionManager: PermissionManager = get()
        permissionManager.update()
        val destination = if (permissionManager.usagePermissionFlow.value) OverviewKey else UsagePermissionRequestKey
        Navigator(startDestination = destination)
    }
    navigation<OverviewKey> { Overview(get(), get()) }
    navigation<DataPlansKey> { DataPlans(get(), get(), get(), get(), get()) }
    navigation<HistoryKey> { History(get(), get()) }
    navigation<IperfScreenKey> { IperfScreen(get()) }
    navigation<SettingsKey> { Settings(get(), get(), get(), get(), get()) }
    navigation<UsagePermissionRequestKey> { UsagePermissionRequest(get(), get()) }
    navigation<PlanConfigKey> { key ->
        val viewModel: DataPlanConfigVM = koinViewModel(key = key.dataPlan.hashedSubscriberID) { parametersOf(key.dataPlan) }
        DataPlanConfig(get(), viewModel)
    }
    navigation<NotificationSettingsKey> { NotificationSettingsScreen(get(), get(), get()) }
    navigation<LibraryLicenseScreen> { LibraryLicenseScreen(get()) }
}