package app.fugitive.feature.game

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

@Serializable data object GameRoute

val gameModule = module { viewModelOf(::GameViewModel) }

fun NavGraphBuilder.gameScreen(onOpenAppSettings: () -> Unit) {
    composable<GameRoute> { GameScreen(onOpenAppSettings = onOpenAppSettings) }
}
