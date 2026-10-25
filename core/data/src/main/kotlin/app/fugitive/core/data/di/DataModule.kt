package app.fugitive.core.data.di

import app.fugitive.core.data.alerts.GameAlerts
import app.fugitive.core.data.backend.BackendRegistry
import app.fugitive.core.data.backend.GameBackend
import app.fugitive.core.data.prefs.UserPreferences
import app.fugitive.core.data.session.GameSessionManager
import app.fugitive.core.location.FusedLocationSource
import app.fugitive.core.location.LocationSource
import app.fugitive.core.location.LocationSources
import app.fugitive.core.location.PlatformLocationSource
import app.fugitive.core.location.SimulatedLocationSource
import app.fugitive.engine.BuiltIns
import app.fugitive.engine.GameEngine
import app.fugitive.engine.ability.Ability
import app.fugitive.engine.mode.GameMode
import app.fugitive.engine.preset.BuiltInPresets
import app.fugitive.engine.preset.GamePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Qualifier for the application-wide coroutine scope. */
val AppScope = named("appScope")

/**
 * Core wiring. Extension points use Koin's multi-binding: any module may
 * declare `single<GameMode> { MyMode() } bind GameMode::class` (or a backend,
 * ability, preset or location source) and it is picked up automatically.
 */
val dataModule = module {
    single(AppScope) {
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                kotlinx.coroutines.CoroutineExceptionHandler { _, e -> android.util.Log.e("Fugitive", "Uncaught in app scope", e) },
        )
    }

    single {
        GameEngine(
            modes = BuiltIns.modes() + getAll<GameMode>(),
            abilities = BuiltIns.abilities() + getAll<Ability>(),
            presets = BuiltInPresets.all + getAll<GamePreset>(),
        )
    }

    single { UserPreferences(androidContext()) }

    single { FusedLocationSource(androidContext()) }
    single { PlatformLocationSource(androidContext()) }
    single { SimulatedLocationSource() }
    single { LocationSources(get(), get(), get(), getAll<LocationSource>()) }

    single { BackendRegistry(getAll<GameBackend>()) }
    single { GameSessionManager(get(AppScope), get(), get(), get(), get()) }
    single { GameAlerts(androidContext(), get(AppScope), get(), get()) }
}
