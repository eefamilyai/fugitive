package app.fugitive.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

@Serializable data object OnboardingRoute
@Serializable data class HomeRoute(val joinCode: String? = null)
@Serializable data object HowToPlayRoute

val homeModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::OnboardingViewModel)
}

fun NavGraphBuilder.homeScreens(
    onOnboardingDone: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHowToPlay: () -> Unit,
    onBack: () -> Unit,
) {
    composable<OnboardingRoute> { OnboardingScreen(onDone = onOnboardingDone) }
    composable<HomeRoute>(
        deepLinks = listOf(navDeepLink<HomeRoute>(basePath = "fugitive://join")),
    ) { entry ->
        val code = runCatching { entry.arguments?.getString("joinCode") }.getOrNull()
        HomeScreen(initialJoinCode = code, onOpenSettings = onOpenSettings, onOpenHowToPlay = onOpenHowToPlay)
    }
    composable<HowToPlayRoute> { HowToPlayScreen(onBack = onBack) }
}
