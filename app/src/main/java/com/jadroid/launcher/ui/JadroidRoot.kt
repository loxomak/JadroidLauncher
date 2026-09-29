package com.jadroid.launcher.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jadroid.launcher.di.AppContainer
import com.jadroid.launcher.ui.screens.AccountsScreen
import com.jadroid.launcher.ui.screens.InstanceDetailScreen
import com.jadroid.launcher.ui.screens.LibraryScreen
import com.jadroid.launcher.ui.screens.ModsScreen
import com.jadroid.launcher.ui.screens.SettingsScreen
import com.jadroid.launcher.ui.screens.VersionsScreen
import com.jadroid.launcher.ui.theme.JadroidTheme
import com.jadroid.launcher.ui.viewmodel.AccountsViewModel
import com.jadroid.launcher.ui.viewmodel.InstancesViewModel
import com.jadroid.launcher.ui.viewmodel.JadroidViewModelFactory
import com.jadroid.launcher.ui.viewmodel.ModSearchViewModel
import com.jadroid.launcher.ui.viewmodel.VersionsViewModel

val LocalJadroid = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided: wrap the tree in JadroidRoot")
}

enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Library("library", "Library", Icons.Filled.Home),
    Versions("versions", "Versions", Icons.AutoMirrored.Filled.List),
    Mods("mods", "Mods", Icons.Filled.Build),
    Accounts("accounts", "Accounts", Icons.Filled.Person),
    Settings("settings", "Settings", Icons.Filled.Settings)
}

const val INSTANCE_ROUTE = "instance"

@Composable
fun JadroidRoot(container: AppContainer) {
    val settings by container.settings.settings.collectAsState()

    JadroidTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
        val factory = remember(container) { JadroidViewModelFactory(container) }
        // Obtained at the activity scope so every screen shares the same task / mod state.
        val instancesViewModel: InstancesViewModel = viewModel(factory = factory)
        val accountsViewModel: AccountsViewModel = viewModel(factory = factory)
        val versionsViewModel: VersionsViewModel = viewModel(factory = factory)
        val modSearchViewModel: ModSearchViewModel = viewModel(factory = factory)

        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showBottomBar = currentRoute?.startsWith(INSTANCE_ROUTE) != true

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        Destination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = {
                                    if (currentRoute != destination.route) {
                                        navController.navigate(destination.route) {
                                            popUpTo(Destination.Library.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(destination.icon, contentDescription = destination.label) },
                                label = { Text(destination.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            JadroidNavHost(
                container = container,
                navController = navController,
                modifier = Modifier.padding(padding),
                instancesViewModel = instancesViewModel,
                accountsViewModel = accountsViewModel,
                versionsViewModel = versionsViewModel,
                modSearchViewModel = modSearchViewModel
            )
        }
    }
}

@Composable
private fun JadroidNavHost(
    container: AppContainer,
    navController: androidx.navigation.NavHostController,
    modifier: Modifier,
    instancesViewModel: InstancesViewModel,
    accountsViewModel: AccountsViewModel,
    versionsViewModel: VersionsViewModel,
    modSearchViewModel: ModSearchViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Destination.Library.route,
        modifier = modifier
    ) {
        composable(Destination.Library.route) {
            LibraryScreen(
                instancesViewModel = instancesViewModel,
                accountsViewModel = accountsViewModel,
                onOpenInstance = { instanceId -> navController.navigate("$INSTANCE_ROUTE/$instanceId") },
                onOpenVersions = { navController.navigate(Destination.Versions.route) },
                onOpenAccounts = { navController.navigate(Destination.Accounts.route) }
            )
        }
        composable(Destination.Versions.route) {
            VersionsScreen(
                versionsViewModel = versionsViewModel,
                instancesViewModel = instancesViewModel,
                onInstanceCreated = { instanceId ->
                    navController.navigate("$INSTANCE_ROUTE/$instanceId")
                }
            )
        }
        composable(Destination.Mods.route) {
            ModsScreen(
                modSearchViewModel = modSearchViewModel,
                instancesViewModel = instancesViewModel
            )
        }
        composable(Destination.Accounts.route) {
            AccountsScreen(accountsViewModel = accountsViewModel)
        }
        composable(Destination.Settings.route) {
            SettingsScreen(container = container)
        }
        composable("$INSTANCE_ROUTE/{instanceId}") { entry ->
            InstanceDetailScreen(
                instanceId = entry.arguments?.getString("instanceId"),
                instancesViewModel = instancesViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
