package com.leekleak.trafficlight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leekleak.trafficlight.database.AppPreferenceRepo
import org.koin.compose.koinInject


val LocalSizeMetric = compositionLocalOf { false }
val LocalSpeedMetric = compositionLocalOf { false }
val LocalBlurEnabled = compositionLocalOf { true }


@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val appPreferenceRepo: AppPreferenceRepo = koinInject()
    val theme by appPreferenceRepo.theme.collectAsState(Theme.AutoMaterial)
    val speedMetric by appPreferenceRepo.speedMetric.collectAsState(false)
    val sizeMetric by appPreferenceRepo.sizeMetric.collectAsState(false)
    val blurEnabled by appPreferenceRepo.blur.collectAsStateWithLifecycle(true)
    val isDark = theme.isDark()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    CompositionLocalProvider(
        LocalSpeedMetric provides speedMetric,
        LocalSizeMetric provides sizeMetric,
        LocalBlurEnabled provides blurEnabled
    ) {
        MaterialTheme (theme.getColors()) { content() }
    }
}