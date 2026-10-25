package app.fugitive.backend.local

import app.fugitive.core.data.backend.GameBackend
import app.fugitive.core.data.di.AppScope
import org.koin.dsl.bind
import org.koin.dsl.module

val localBackendModule = module {
    single { LocalBackend(get(AppScope), get()) } bind GameBackend::class
}
