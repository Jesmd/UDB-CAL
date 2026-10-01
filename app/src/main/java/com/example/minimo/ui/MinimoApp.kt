package com.example.minimo.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material3.SnackbarHostState
import com.example.minimo.AppContainer
import com.example.minimo.R
import com.example.minimo.ui.courses.CoursesScreen
import com.example.minimo.ui.courses.CoursesViewModel
import com.example.minimo.ui.detail.COURSE_ID_ARG
import com.example.minimo.ui.detail.CourseDetailScreen
import com.example.minimo.ui.detail.CourseDetailViewModel
import com.example.minimo.ui.glass.AmbientPhaseProvider
import com.example.minimo.ui.glass.GlassSnackbarHost
import com.example.minimo.ui.glass.GlassTab
import com.example.minimo.ui.glass.GlassTabBar
import com.example.minimo.ui.glass.LocalGlassBackdrop
import com.example.minimo.ui.glass.LocalTabBarInset
import com.example.minimo.ui.glass.TabBarBottomMargin
import com.example.minimo.ui.glass.TabBarHeight
import com.example.minimo.ui.glass.glassSource
import com.example.minimo.ui.glass.rememberGlassBackdrop
import com.example.minimo.ui.portal.PortalScreen
import com.example.minimo.ui.portal.PortalViewModel
import com.example.minimo.ui.settings.SettingsScreen
import com.example.minimo.ui.settings.SettingsViewModel
import com.example.minimo.ui.sync.SyncScreen
import com.example.minimo.ui.sync.SyncViewModel

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

private val IosEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private const val PUSH_MILLIS = 440

private fun String?.isTopLevel() = TopLevelDestination.entries.any { it.route == this }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
    initialState.destination.route.isTopLevel() && targetState.destination.route.isTopLevel()

/** Back and navigate only while the screen is settled, so a double tap cannot pop past the first screen. */
private fun NavController.isSettled() = currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

private fun NavController.goBack() {
    if (isSettled()) popBackStack()
}

private fun NavController.open(route: String) {
    if (isSettled()) navigate(route) { launchSingleTop = true }
}

@Composable
fun MinimoApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabIndex = TopLevelDestination.entries.indexOfFirst { it.route == currentRoute }
    val showTabBar = tabIndex >= 0

    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMessage = stringResource(R.string.storage_save_error)
    LaunchedEffect(container) {
        container.courseRepository.saveFailures.collect { snackbarHostState.showSnackbar(saveErrorMessage) }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabInset by animateDpAsState(
        targetValue = if (showTabBar) TabBarHeight + TabBarBottomMargin + navBottom + 12.dp else 0.dp,
        animationSpec = spring(stiffness = 400f),
        label = "tabInset",
    )
    val tabs = TopLevelDestination.entries.map { GlassTab(stringResource(it.labelRes), it.icon) }
    val backdrop = rememberGlassBackdrop()

    AmbientPhaseProvider {
        Box(Modifier.fillMaxSize()) {
            // Everything the tab bar floats over. Recorded only while the bar is on screen.
            Box(Modifier.fillMaxSize().then(if (showTabBar) Modifier.glassSource(backdrop) else Modifier)) {
                CompositionLocalProvider(LocalTabBarInset provides tabInset) {
                    MinimoNavHost(navController, container)
                }
            }

            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                AnimatedVisibility(
                    visible = showTabBar,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(spring(dampingRatio = 0.78f, stiffness = 380f)) { it } + fadeIn(),
                    exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
                ) {
                    GlassTabBar(
                        tabs = tabs,
                        selectedIndex = tabIndex.coerceAtLeast(0),
                        onSelect = { index ->
                            navController.navigate(TopLevelDestination.entries[index].route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = navBottom + TabBarBottomMargin),
                    )
                }
                GlassSnackbarHost(
                    snackbarHostState,
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = tabInset.coerceAtLeast(navBottom + 16.dp)),
                )
            }
        }
    }
}

@Composable
private fun MinimoNavHost(controller: NavHostController, container: AppContainer) {
    NavHost(
        navController = controller,
        startDestination = TopLevelDestination.Courses.route,
        // Tabs cross-fade with a soft scale; deeper screens push from the right like in iOS.
        enterTransition = {
            if (isTabSwitch()) tabEnter() else slideInHorizontally(tween(PUSH_MILLIS, easing = IosEasing)) { it }
        },
        exitTransition = {
            if (isTabSwitch()) tabExit() else slideOutHorizontally(tween(PUSH_MILLIS, easing = IosEasing)) { -it / 4 }
        },
        popEnterTransition = {
            if (isTabSwitch()) tabEnter() else slideInHorizontally(tween(PUSH_MILLIS, easing = IosEasing)) { -it / 4 }
        },
        popExitTransition = {
            if (isTabSwitch()) tabExit() else slideOutHorizontally(tween(PUSH_MILLIS, easing = IosEasing)) { it }
        },
    ) {
        composable(TopLevelDestination.Courses.route) {
            CoursesScreen(
                viewModel = viewModel(factory = CoursesViewModel.factory(container)),
                onOpenCourse = { controller.open("course/$it") },
                onSync = { controller.open(SYNC_ROUTE) },
            )
        }
        composable(TopLevelDestination.Settings.route) {
            SettingsScreen(
                viewModel = viewModel(factory = SettingsViewModel.factory(container)),
                onOpenPortal = { controller.open(PORTAL_ROUTE) },
            )
        }
        composable(SYNC_ROUTE) {
            SyncScreen(
                viewModel = viewModel(factory = SyncViewModel.factory(container)),
                onBack = { controller.goBack() },
                onDone = { controller.goBack() },
            )
        }
        composable(PORTAL_ROUTE) {
            PortalScreen(
                viewModel = viewModel(factory = PortalViewModel.factory(container)),
                onBack = { controller.goBack() },
            )
        }
        composable(
            route = COURSE_DETAIL_ROUTE,
            arguments = listOf(navArgument(COURSE_ID_ARG) { type = NavType.StringType }),
        ) {
            CourseDetailScreen(
                viewModel = viewModel(factory = CourseDetailViewModel.factory(container)),
                onBack = { controller.goBack() },
            )
        }
    }
}

private fun tabEnter(): EnterTransition =
    fadeIn(tween(260, delayMillis = 60)) + scaleIn(spring(dampingRatio = 0.85f, stiffness = 380f), initialScale = 0.96f)

private fun tabExit(): ExitTransition = fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 0.98f)
