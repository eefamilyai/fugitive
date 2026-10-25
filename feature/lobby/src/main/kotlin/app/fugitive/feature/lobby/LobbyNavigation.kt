package app.fugitive.feature.lobby

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

@Serializable data object LobbyRoute
@Serializable data object GameSettingsRoute

val lobbyModule = module {
    viewModelOf(::LobbyViewModel)
    viewModelOf(::GameSettingsViewModel)
}

fun NavGraphBuilder.lobbyScreens(onOpenSettings: () -> Unit, onBack: () -> Unit) {
    composable<LobbyRoute> { LobbyScreen(onOpenGameSettings = onOpenSettings) }
    composable<GameSettingsRoute> { GameSettingsScreen(onBack = onBack) }
}
