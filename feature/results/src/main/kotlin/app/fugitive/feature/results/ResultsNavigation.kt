package app.fugitive.feature.results

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

@Serializable data object ResultsRoute

val resultsModule = module { viewModelOf(::ResultsViewModel) }

fun NavGraphBuilder.resultsScreen() {
    composable<ResultsRoute> { ResultsScreen() }
}
