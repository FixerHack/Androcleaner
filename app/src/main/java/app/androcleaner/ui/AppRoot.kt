package app.androcleaner.ui

import android.content.Context
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.androcleaner.R
import app.androcleaner.core.permissions.rememberPermissionStatus
import app.androcleaner.feature.apps.AppsScreen
import app.androcleaner.feature.onboarding.OnboardingScreen
import app.androcleaner.feature.scan.ScanScreen
import app.androcleaner.feature.soon.ComingSoonScreen
import app.androcleaner.feature.storage.StorageScreen
import app.androcleaner.feature.trash.TrashScreen
import app.androcleaner.ui.components.LocalSnackbar
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data object ScanRoute
@Serializable data object StorageRoute
@Serializable data object PhotosRoute
@Serializable data class AppsRoute(val sortByCache: Boolean = false)
@Serializable data object ProtectionRoute
@Serializable data object TrashRoute

private data class Tab(val route: Any, val routeClass: KClass<*>, val icon: ImageVector, val label: Int)

private val tabs = listOf(
    Tab(ScanRoute, ScanRoute::class, Icons.Rounded.AutoAwesome, R.string.tab_scan),
    Tab(StorageRoute, StorageRoute::class, Icons.Rounded.PieChart, R.string.tab_storage),
    Tab(PhotosRoute, PhotosRoute::class, Icons.Rounded.PhotoLibrary, R.string.tab_photos),
    Tab(AppsRoute(), AppsRoute::class, Icons.Rounded.GridView, R.string.tab_apps),
    Tab(ProtectionRoute, ProtectionRoute::class, Icons.Rounded.Shield, R.string.tab_protection),
)

private const val PREFS = "androcleaner"
private const val KEY_ONBOARDED = "onboarded"

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var onboarded by remember { mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false)) }
    val permissions = rememberPermissionStatus()

    if (!onboarded || !permissions.allFilesAccess) {
        OnboardingScreen(onContinue = {
            prefs.edit { putBoolean(KEY_ONBOARDED, true) }
            onboarded = true
        })
    } else {
        MainScaffold()
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = destination?.hasRoute(TrashRoute::class) != true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalSnackbar provides snackbar) {
            NavHost(
                navController = navController,
                startDestination = ScanRoute,
                modifier = Modifier.padding(padding),
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                composable<ScanRoute> {
                    ScanScreen(
                        onOpenTrash = { navController.navigate(TrashRoute) },
                        onOpenAppCache = {
                            navController.navigate(AppsRoute(sortByCache = true)) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable<StorageRoute> { StorageScreen() }
                composable<PhotosRoute> {
                    ComingSoonScreen(
                        Icons.Rounded.PhotoLibrary,
                        R.string.photos_title,
                        R.string.photos_desc,
                        listOf(R.string.photos_f1, R.string.photos_f2, R.string.photos_f3, R.string.photos_f4, R.string.photos_f5),
                    )
                }
                composable<AppsRoute> { AppsScreen() }
                composable<ProtectionRoute> {
                    ComingSoonScreen(
                        Icons.Rounded.Shield,
                        R.string.protection_title,
                        R.string.protection_desc,
                        listOf(R.string.protection_f1, R.string.protection_f2, R.string.protection_f3, R.string.protection_f4),
                    )
                }
                composable<TrashRoute> { TrashScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}
