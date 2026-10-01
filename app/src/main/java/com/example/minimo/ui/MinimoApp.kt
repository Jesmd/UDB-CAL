package com.example.minimo.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.minimo.AppContainer
import com.example.minimo.R
import com.example.minimo.ui.courses.CoursesScreen
import com.example.minimo.ui.courses.CoursesViewModel
import com.example.minimo.ui.detail.COURSE_ID_ARG
import com.example.minimo.ui.detail.CourseDetailScreen
import com.example.minimo.ui.detail.CourseDetailViewModel
import com.example.minimo.ui.portal.PortalScreen
import com.example.minimo.ui.portal.PortalViewModel
import com.example.minimo.ui.settings.SettingsScreen
import com.example.minimo.ui.sync.SyncScreen
import com.example.minimo.ui.sync.SyncViewModel
import com.example.minimo.ui.settings.SettingsViewModel

private const val PORTAL_ROUTE = "portal"
private const val SYNC_ROUTE = "sync"
private const val COURSE_DETAIL_ROUTE = "course/{$COURSE_ID_ARG}"

private enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Courses("courses", R.string.nav_courses, Icons.AutoMirrored.Filled.List),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun MinimoApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.storage_save_error)
    LaunchedEffect(container) {
        container.courseRepository.saveFailures.collect { snackbarHostState.showSnackbar(saveErrorMessage) }
    }

    Scaffold(
        // Each screen handles its own insets (top bars, bottom bar).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Courses.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.Courses.route) {
                CoursesScreen(
                    viewModel = viewModel(factory = CoursesViewModel.factory(container)),
                    onOpenCourse = { navController.navigate("course/$it") },
                    onSync = { navController.navigate(SYNC_ROUTE) },
                )
            }
            composable(TopLevelDestination.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel(factory = SettingsViewModel.factory(container)),
                    onOpenPortal = { navController.navigate(PORTAL_ROUTE) },
                )
            }
            composable(SYNC_ROUTE) {
                SyncScreen(
                    viewModel = viewModel(factory = SyncViewModel.factory(container)),
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }
            composable(PORTAL_ROUTE) {
                PortalScreen(
                    viewModel = viewModel(factory = PortalViewModel.factory(container)),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = COURSE_DETAIL_ROUTE,
                arguments = listOf(navArgument(COURSE_ID_ARG) { type = NavType.StringType }),
            ) {
                CourseDetailScreen(
                    viewModel = viewModel(factory = CourseDetailViewModel.factory(container)),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
