package com.vigia.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vigia.R
import com.vigia.ui.screens.PlaceholderScreen
import com.vigia.ui.screens.RobotSelectScreen

private data class Tab(val route: Route, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Route.Supervision, R.string.tab_supervision, Icons.Filled.Home),
    Tab(Route.Missions, R.string.tab_missions, Icons.AutoMirrored.Filled.List),
    Tab(Route.Alerts, R.string.tab_alerts, Icons.Filled.Warning),
    Tab(Route.History, R.string.tab_history, Icons.Filled.DateRange),
)

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController, startDestination = Route.RobotSelect) {
        composable<Route.RobotSelect> {
            RobotSelectScreen(
                onSelected = {
                    navController.navigate(Route.Main) { popUpTo<Route.RobotSelect> { inclusive = true } }
                },
            )
        }
        composable<Route.Main> { MainScreen() }
    }
}

// Las pestañas tienen su propio NavController para que cada una conserve su estado
@Composable
private fun MainScreen(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hasRoute(tab.route::class) == true,
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
        },
    ) { padding ->
        NavHost(navController, startDestination = Route.Supervision, modifier = Modifier.padding(padding)) {
            composable<Route.Supervision> { PlaceholderScreen(stringResource(R.string.tab_supervision)) }
            composable<Route.Missions> { PlaceholderScreen(stringResource(R.string.tab_missions)) }
            composable<Route.Alerts> { PlaceholderScreen(stringResource(R.string.tab_alerts)) }
            composable<Route.History> { PlaceholderScreen(stringResource(R.string.tab_history)) }
        }
    }
}
