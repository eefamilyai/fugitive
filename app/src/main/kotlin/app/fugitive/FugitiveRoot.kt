package app.fugitive

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.fugitive.core.data.prefs.AppPreferences
import app.fugitive.core.data.prefs.ThemeMode
import app.fugitive.core.data.prefs.Units
import app.fugitive.core.data.prefs.UserPreferences
import app.fugitive.core.data.service.TrackingService
import app.fugitive.core.data.session.GameSessionManager
import app.fugitive.core.data.session.SessionState
import app.fugitive.core.designsystem.theme.FugitiveTheme
import app.fugitive.core.ui.LocalUnitSystem
import app.fugitive.core.ui.UnitSystem
import app.fugitive.feature.game.GameRoute
import app.fugitive.feature.game.gameScreen
import app.fugitive.feature.home.HomeRoute
import app.fugitive.feature.home.HowToPlayRoute
import app.fugitive.feature.home.OnboardingRoute
import app.fugitive.feature.home.homeScreens
import app.fugitive.feature.lobby.GameSettingsRoute
import app.fugitive.feature.lobby.LobbyRoute
import app.fugitive.feature.lobby.lobbyScreens
import app.fugitive.feature.results.ResultsRoute
import app.fugitive.feature.results.resultsScreen
import app.fugitive.feature.settings.AppSettingsRoute
import app.fugitive.feature.settings.settingsScreen
import app.fugitive.model.LobbyPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

/** Which top-level area the current session belongs in. */
private enum class Section { HOME, LOBBY, GAME, RESULTS }

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun FugitiveRoot(initialPrefs: AppPreferences, pendingJoin: MutableStateFlow<String?>) {
    val prefsRepo: UserPreferences = koinInject()
    val sessions: GameSessionManager = koinInject()
    val prefs by prefsRepo.preferences.collectAsStateWithLifecycle(initialPrefs)
    val dark = when (prefs.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val nav = rememberNavController()
    val context = LocalContext.current

    FugitiveTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalUnitSystem provides if (prefs.units == Units.IMPERIAL) UnitSystem.IMPERIAL else UnitSystem.METRIC) {
            NavHost(
                navController = nav,
                startDestination = if (initialPrefs.onboarded) HomeRoute() else OnboardingRoute,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            ) {
                homeScreens(
                    onOnboardingDone = { nav.navigate(HomeRoute()) { popUpTo(0) { inclusive = true } } },
                    onOpenSettings = { nav.navigate(AppSettingsRoute) },
                    onOpenHowToPlay = { nav.navigate(HowToPlayRoute) },
                    onBack = { nav.popBackStack() },
                )
                lobbyScreens(onOpenSettings = { nav.navigate(GameSettingsRoute) }, onBack = { nav.popBackStack() })
                gameScreen(onOpenAppSettings = { nav.navigate(AppSettingsRoute) })
                resultsScreen()
                settingsScreen(
                    onBack = { nav.popBackStack() },
                    onReplayTutorial = { nav.navigate(OnboardingRoute) { popUpTo(0) { inclusive = true } } },
                )
            }
        }
    }

    // Invite links that arrive while the app is open.
    val join by pendingJoin.collectAsStateWithLifecycle()
    LaunchedEffect(join) {
        val code = join ?: return@LaunchedEffect
        if (sessions.current == null && prefs.onboarded) nav.navigate(HomeRoute(joinCode = code)) { popUpTo(0) { inclusive = true } }
        pendingJoin.value = null
    }

    // Session-driven routing: the active lobby's phase decides the screen.
    LaunchedEffect(Unit) {
        sessions.state.flatMapLatest { state ->
            val s = (state as? SessionState.Active)?.session ?: return@flatMapLatest flowOf(Section.HOME)
            s.lobby.map { l ->
                when {
                    l == null -> Section.LOBBY
                    l.game?.result != null || l.info.phase == LobbyPhase.RESULTS -> Section.RESULTS
                    l.info.phase == LobbyPhase.PLAYING && l.game != null -> Section.GAME
                    else -> Section.LOBBY
                }
            }
        }.distinctUntilChanged().collect { section -> route(nav, section, context) }
    }
}

private fun route(nav: NavHostController, section: Section, context: Context) {
    // Location sharing in the background is only kept alive during an active game.
    if (section == Section.GAME) TrackingService.start(context) else TrackingService.stop(context)
    val current = nav.currentDestination
    val target: Any = when (section) {
        Section.HOME -> HomeRoute()
        Section.LOBBY -> LobbyRoute
        Section.GAME -> GameRoute
        Section.RESULTS -> ResultsRoute
    }
    val onboarding = current?.route?.contains("OnboardingRoute") == true
    if (section == Section.HOME && (onboarding || current?.route?.contains("HomeRoute") == true)) return
    nav.navigate(target) {
        popUpTo(nav.graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
