package com.fersaiyan.cyanbridge.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fersaiyan.cyanbridge.chat.ChatEngine
import com.fersaiyan.cyanbridge.ui.accessibility.AccessibilityPrefs
import com.fersaiyan.cyanbridge.ui.screens.AssistantScreen
import com.fersaiyan.cyanbridge.ui.screens.HomeScreen
import com.fersaiyan.cyanbridge.ui.screens.MediaScreen
import com.fersaiyan.cyanbridge.ui.screens.SettingsScreen

/** Bottom navigation destinations. */
sealed class Screen(
        val route: String,
        val label: String,
        val selectedIcon: ImageVector,
        val unselectedIcon: ImageVector,
        val contentDesc: String,
        val voiceLabel: String,
) {
    data object Home :
            Screen(
                    "home",
                    "首页",
                    Icons.Filled.Home,
                    Icons.Outlined.Home,
                    "首页，眼镜连接状态",
                    "首页",
            )
    data object Assistant :
            Screen(
                    "assistant",
                    "助手",
                    Icons.Filled.Mic,
                    Icons.Outlined.Mic,
                    "语音助手，智能对话",
                    "语音助手",
            )
    data object Media :
            Screen(
                    "media",
                    "媒体",
                    Icons.Filled.Photo,
                    Icons.Outlined.Photo,
                    "媒体库，照片视频录音",
                    "媒体库",
            )
    data object Settings :
            Screen(
                    "settings",
                    "设置",
                    Icons.Filled.Settings,
                    Icons.Outlined.Settings,
                    "设置，模式切换与账户",
                    "设置",
            )
}

private val bottomNavItems = listOf(Screen.Home, Screen.Assistant, Screen.Media, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val voiceGuide by AccessibilityPrefs.voiceGuide.collectAsState()

    // Voice guide: announce page name on navigation change
    val currentRoute = currentDestination?.route
    LaunchedEffect(currentRoute) {
        if (voiceGuide && currentRoute != null) {
            val screen = bottomNavItems.find { it.route == currentRoute }
            if (screen != null) {
                ChatEngine.replayVoiceGuide("已切换到${screen.voiceLabel}页面")
            }
        }
    }

    Scaffold(
            bottomBar = {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected =
                                currentDestination?.hierarchy?.any { it.route == screen.route } ==
                                        true

                        NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                            imageVector =
                                                    if (selected) screen.selectedIcon
                                                    else screen.unselectedIcon,
                                            contentDescription = screen.contentDesc
                                    )
                                },
                                label = { Text(screen.label) },
                                modifier =
                                        Modifier.semantics {
                                            contentDescription = screen.contentDesc
                                        }
                        )
                    }
                }
            }
    ) { innerPadding ->
        NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Assistant.route) { AssistantScreen() }
            composable(Screen.Media.route) { MediaScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
