package app.fugitive

import android.app.Application
import android.content.Intent
import app.fugitive.backend.firebase.FirebaseConfig
import app.fugitive.backend.firebase.firebaseBackendModule
import app.fugitive.backend.local.localBackendModule
import app.fugitive.core.data.AppInfo
import app.fugitive.core.data.alerts.GameAlerts
import app.fugitive.core.data.di.dataModule
import app.fugitive.core.data.service.Notifications
import app.fugitive.feature.game.gameModule
import app.fugitive.feature.home.homeModule
import app.fugitive.feature.lobby.lobbyModule
import app.fugitive.feature.results.resultsModule
import app.fugitive.feature.settings.settingsModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

class FugitiveApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FugitiveApplication)
            modules(
                appModule,
                dataModule,
                localBackendModule,
                firebaseBackendModule,
                homeModule, lobbyModule, gameModule, resultsModule, settingsModule,
                // Plugin modules (extra game modes, abilities, backends, …) go here.
            )
        }
        Notifications.smallIcon = R.drawable.ic_stat_fugitive
        Notifications.launchIntentFactory = { ctx -> Intent(ctx, MainActivity::class.java) }
        Notifications.ensureChannels(this)
        get<GameAlerts>().start()
    }

    private val appModule = module {
        single {
            FirebaseConfig(
                configured = BuildConfig.FIREBASE_CONFIGURED,
                emulatorHost = BuildConfig.FIREBASE_EMULATOR_HOST,
            )
        }
        single(named("appVersion")) { BuildConfig.VERSION_CODE }
        single {
            AppInfo(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                privacyPolicyUrl = getString(R.string.privacy_policy_url),
                supportEmail = getString(R.string.support_email),
                onlineConfigured = BuildConfig.FIREBASE_CONFIGURED || BuildConfig.FIREBASE_EMULATOR_HOST.isNotBlank(),
            )
        }
    }
}
