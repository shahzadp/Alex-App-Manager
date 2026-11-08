@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.shahzadp.appmanager.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shahzadp.appmanager.R
import io.github.shahzadp.appmanager.data.ThemeMode
import io.github.shahzadp.appmanager.ui.components.FilterPill
import io.github.shahzadp.appmanager.ui.detail.DetailScreen
import io.github.shahzadp.appmanager.ui.library.LibraryFilter
import io.github.shahzadp.appmanager.ui.library.LibraryScreen

private enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    LIBRARY("Library", R.drawable.ic_grid),
    UPDATES("Updates", R.drawable.ic_update),
}

@Composable
fun AlexAppsApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message -> snackbar.showSnackbar(message) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshInstalled()
    }

    LaunchedEffect(detailId, state.rows) {
        val current = detailId
        if (current != null && state.row(current) == null) detailId = null
    }

    val openUrl: (String) -> Unit = remember(context) {
        { url: String ->
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    val detailRow = detailId?.let(state::row)

    if (detailRow != null) {
        BackHandler { detailId = null }
        DetailScreen(
            row = detailRow,
            onBack = { detailId = null },
            onInstall = { viewModel.install(detailRow.item) },
            onCancel = { viewModel.cancel(detailRow.item) },
            onOpen = { viewModel.open(detailRow.item) },
            onUninstall = { viewModel.uninstall(detailRow.item) },
            onPermission = { viewModel.openPermissionSettings() },
            onOpenExternal = openUrl,
        )
        return
    }

    BackHandler(enabled = tab != Tab.LIBRARY) {
        tab = Tab.LIBRARY
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = when (tab) {
                            Tab.LIBRARY -> "Alex App Manager"
                            Tab.UPDATES -> "Updates"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = {
                    Text(
                        text = subtitleFor(tab, state),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh(force = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { openUrl(viewModel.profileUrl) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_github),
                            contentDescription = "GitHub profile",
                        )
                    }
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            BottomPillNav(
                selected = tab,
                updates = state.updateCount,
                onSelect = { tab = it },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (tab) {
            Tab.LIBRARY, Tab.UPDATES -> LibraryScreen(
                state = state,
                filter = filter,
                updatesOnly = tab == Tab.UPDATES,
                onFilter = { filter = it },
                onOpen = { detailId = it.item.id },
                onAction = { row ->
                    when {
                        row.item.isWeb -> row.item.webUrl?.let(openUrl)
                        row.installed != null && !row.updateAvailable -> viewModel.open(row.item)
                        else -> viewModel.install(row.item)
                    }
                },
                onCancel = { viewModel.cancel(it.item) },
                onPermission = { viewModel.openPermissionSettings() },
                onRefresh = { viewModel.refresh(force = true) },
                onDismissError = { viewModel.dismissError() },
                onOpenProfile = { openUrl(viewModel.profileUrl) },
                contentPadding = padding,
            )
        }
    }

    if (settingsOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { settingsOpen = false },
            sheetState = sheetState,
        ) {
            SettingsSheet(
                theme = theme,
                onTheme = viewModel::setTheme,
                onPermission = { viewModel.openPermissionSettings() },
                onRefresh = { viewModel.refresh(force = true) },
                onOpenProfile = { openUrl(viewModel.profileUrl) },
            )
        }
    }
}

private fun subtitleFor(tab: Tab, state: LibraryState): String = when (tab) {
    Tab.LIBRARY -> {
        val installed = state.installedCount
        val updates = state.updateCount
        when {
            updates > 0 -> "$installed installed Â· $updates update${if (updates == 1) "" else "s"}"
            installed > 0 -> "$installed of ${state.rows.count { !it.item.isWeb }} installed"
            else -> "Apps, games and the portfolio from GitHub"
        }
    }

    Tab.UPDATES -> if (state.updateCount == 0) {
        "All installed apps are on their latest release"
    } else {
        "${state.updateCount} app${if (state.updateCount == 1) "" else "s"} behind the latest release"
    }
}

@Composable
private fun BottomPillNav(
    selected: Tab,
    updates: Int,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                val container by animateColorAsState(
                    targetValue = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    label = "navContainer",
                )
                val content by animateColorAsState(
                    targetValue = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "navContent",
                )
                Surface(
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    color = container,
                    contentColor = content,
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(tab.icon),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = content,
                        )
                        if (active) {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = content,
                                maxLines = 1,
                            )
                        }
                        if (tab == Tab.UPDATES && updates > 0) {
                            Spacer(Modifier.width(5.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                            ) {
                                Text(
                                    text = updates.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSheet(
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    onPermission: () -> Unit,
    onRefresh: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "How Alex App Manager looks and how it installs releases.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(22.dp))
        Text("Appearance", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterPill(
                    selected = mode == theme,
                    label = mode.label,
                    onClick = { onTheme(mode) },
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("Installs", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Android asks for permission before a downloaded APK can be installed. " +
                "Alex App Manager downloads the file itself, checks its package name and signing key, " +
                "then hands it straight to the system installer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onPermission) {
            Text("Install permission settings")
        }

        Spacer(Modifier.height(22.dp))
        Text("Releases", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Version checks use GitHub's public release API and are cached for 30 minutes. " +
                "The bundled catalogue keeps working without a connection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Check for updates now")
        }

        Spacer(Modifier.height(22.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Alex App Manager 0.2.2", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "github.com/shahzadp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onOpenProfile) { Text("GitHub") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Apps, games and the portfolio from github.com/shahzadp. Built with Jetpack Compose " +
                "and Material 3 Expressive.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}



