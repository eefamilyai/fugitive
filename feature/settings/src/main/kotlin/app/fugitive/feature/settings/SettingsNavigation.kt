package app.fugitive.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

@Serializable data object AppSettingsRoute

val settingsModule = module { viewModelOf(::AppSettingsViewModel) }

fun NavGraphBuilder.settingsScreen(onBack: () -> Unit, onReplayTutorial: () -> Unit) {
    composable<AppSettingsRoute> { AppSettingsScreen(onBack = onBack, onReplayTutorial = onReplayTutorial) }
}
