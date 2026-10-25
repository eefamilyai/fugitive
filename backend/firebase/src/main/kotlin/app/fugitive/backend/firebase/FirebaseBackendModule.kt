package app.fugitive.backend.firebase

import app.fugitive.core.data.backend.GameBackend
import app.fugitive.core.data.di.AppScope
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/** Requires a [FirebaseConfig] and an app version code `Int` named "appVersion" to be provided by the app module. */
val firebaseBackendModule = module {
    single { FirebaseBackend(androidContext(), get(), get(AppScope), get(org.koin.core.qualifier.named("appVersion"))) } bind GameBackend::class
}
