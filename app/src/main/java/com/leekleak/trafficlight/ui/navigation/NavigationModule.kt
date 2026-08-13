package com.leekleak.trafficlight.ui.navigation

import com.leekleak.trafficlight.model.PermissionManager
import com.leekleak.trafficlight.ui.history.History
import com.leekleak.trafficlight.ui.overview.Overview
import com.leekleak.trafficlight.ui.plans.DataPlanConfig
import com.leekleak.trafficlight.ui.plans.DataPlans
import com.leekleak.trafficlight.ui.settings.LibraryLicenseScreen
import com.leekleak.trafficlight.ui.settings.NotificationSettingsScreen
import com.leekleak.trafficlight.ui.settings.Settings
import com.leekleak.trafficlight.ui.settings.UsagePermissionRequest
import org.koin.core.annotation.KoinExperimentalAPI
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
    navigation<OverviewKey> { Overview() }
    navigation<DataPlansKey> { DataPlans() }
    navigation<HistoryKey> { History() }
    navigation<SettingsKey> { Settings(get()) }
    navigation<UsagePermissionRequestKey> { UsagePermissionRequest() }
    navigation<PlanConfigKey> { key -> DataPlanConfig(key.dataPlan)  }
    navigation<NotificationSettingsKey> { NotificationSettingsScreen(get()) }
    navigation<LibraryLicenseScreen> { LibraryLicenseScreen(get()) }
}