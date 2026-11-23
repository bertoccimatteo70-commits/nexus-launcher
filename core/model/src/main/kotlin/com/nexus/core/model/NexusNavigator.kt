package com.nexus.core.model

/**
 * Feature modules never own global navigation. They invoke these callbacks, which the
 * app shell wires to real destinations. All callbacks are safe no-ops by default so a
 * feature can be previewed or tested in isolation.
 */
data class NexusNavigator(
    val openHome: () -> Unit = {},
    val openSearch: (String) -> Unit = {},
    val openDrawer: () -> Unit = {},
    val openDashboard: () -> Unit = {},
    val openTools: (ToolDestination?) -> Unit = {},
    val openSettings: (String) -> Unit = {},
    val openDefaultLauncherSettings: () -> Unit = {},
    val requestDefaultLauncher: () -> Unit = {},
)
