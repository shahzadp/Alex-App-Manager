@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.shahzadp.appmanager.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.shahzadp.appmanager.R
import io.github.shahzadp.appmanager.data.Versions
import io.github.shahzadp.appmanager.install.InstallState
import io.github.shahzadp.appmanager.ui.AppRow
import io.github.shahzadp.appmanager.ui.components.AppIcon
import io.github.shahzadp.appmanager.ui.components.BusyRow
import io.github.shahzadp.appmanager.ui.components.DownloadProgress
import io.github.shahzadp.appmanager.ui.components.TagPill
import io.github.shahzadp.appmanager.ui.components.cleanNotes
import io.github.shahzadp.appmanager.ui.components.formatBytes
import io.github.shahzadp.appmanager.ui.components.formatDate

@Composable
fun DetailScreen(
    row: AppRow,
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onPermission: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val item = row.item
    val accent = Color(item.accent)
    val size = row.release.apk(item)?.size ?: item.pinnedSize
    val repoUrl = item.repoUrl
    val releaseUrl = repoUrl?.let { "$it/releases" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = item.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (repoUrl != null) {
                        IconButton(onClick = { onOpenExternal(repoUrl) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_github),
                                contentDescription = "Open repository",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "hero") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(136.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(MaterialShapes.Cookie9Sided.toShape())
                                .background(accent.copy(alpha = 0.13f)),
                        )
                        AppIcon(item = item, size = 96.dp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        TagPill(item.section.label)
                        if (!item.isWeb) TagPill("v${row.release.version}")
                        if (size > 0) TagPill(formatBytes(size))
                        if (row.updateAvailable) TagPill("Update available")
                    }
                }
            }

            item(key = "actions") {
                ActionCard(
                    row = row,
                    onInstall = onInstall,
                    onCancel = onCancel,
                    onOpen = onOpen,
                    onPermission = onPermission,
                    onOpenExternal = onOpenExternal,
                )
            }

            if (item.caveat != null) {
                item(key = "caveat") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_sparkle),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = item.caveat,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (!item.isWeb) {
                item(key = "notes") {
                    val notes = cleanNotes(row.release.notes)
                    SectionCard(
                        title = "What's new",
                        subtitle = buildString {
                            append("v")
                            append(row.release.version)
                            formatDate(row.release.publishedAt)?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                    ) {
                        SelectionContainer {
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = if (notes.length > 700) 22 else Int.MAX_VALUE,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (notes.length > 700 && releaseUrl != null) {
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { onOpenExternal(releaseUrl) }) {
                                Text("Read the full release on GitHub")
                            }
                        }
                    }
                }
            }

            item(key = "about") {
                SectionCard(title = "About") {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        item.tags.forEach { TagPill(it) }
                    }
                }
            }

            item(key = "details") {
                SectionCard(title = "Details") {
                    DetailRow("Repository", item.repo ?: "—")
                    DetailRow("Package", item.packageName ?: "Web app")
                    DetailRow(
                        label = "Latest release",
                        value = if (item.isWeb) "Live site" else "v${row.release.version}",
                    )
                    if (!item.isWeb) {
                        DetailRow("Download size", formatBytes(size))
                        DetailRow(
                            label = "Installed",
                            value = row.installed?.versionName?.let { "v$it" } ?: "Not installed",
                        )
                    }
                    DetailRow("Minimum Android", item.minAndroid ?: "—")
                    DetailRow(
                        label = "Release source",
                        value = if (row.release.fromNetwork) "GitHub releases (live)" else "Bundled catalogue",
                    )
                }
            }

            item(key = "footer-actions") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (row.installed != null) {
                        Button(
                            onClick = onUninstall,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Uninstall ${item.name}")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (repoUrl != null) {
                        OutlinedButton(onClick = { onOpenExternal(repoUrl) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_github),
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("View on GitHub")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    row: AppRow,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onPermission: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val item = row.item
    val size = row.release.apk(item)?.size ?: item.pinnedSize

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(18.dp)) {
            when (val state = row.installState) {
                is InstallState.Downloading -> {
                    Text(
                        text = "Downloading from GitHub",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    DownloadProgress(state = state, onCancel = onCancel)
                }

                InstallState.Verifying -> BusyRow("Checking the APK's package and signature…")

                InstallState.Installing -> BusyRow(
                    "Finish in the system installer. Android will ask you to confirm.",
                )

                is InstallState.NeedsPermission -> {
                    Text(
                        text = "Android needs permission to install apps from Alex App Manager.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onPermission) { Text("Allow installs") }
                    TextButton(onClick = onInstall) { Text("I allowed it — try again") }
                }

                is InstallState.Failed -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onInstall) { Text("Try again") }
                }

                InstallState.Installed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Installed", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpen) { Text("Open") }
                }

                else -> DefaultActions(
                    row = row,
                    size = size,
                    onInstall = onInstall,
                    onOpen = onOpen,
                    onOpenExternal = onOpenExternal,
                )
            }
        }
    }
}

@Composable
private fun DefaultActions(
    row: AppRow,
    size: Long,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val item = row.item
    if (item.isWeb) {
        Text(
            text = "Opens in your browser so Android can install it as a standalone PWA. " +
                "The manager keeps no copy of the site.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = { item.webUrl?.let(onOpenExternal) }) {
            Icon(painterResource(R.drawable.ic_globe), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open in browser")
        }
        return
    }

    val installed = row.installed
    when {
        installed == null -> {
            Text(
                text = "Downloads the APK from GitHub and hands it to Android's installer. No browser, no file manager.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onInstall) {
                Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Install · ${formatBytes(size)}")
            }
        }

        row.updateAvailable -> {
            Text(
                text = "Installed v${installed.versionName ?: "?"} · latest v${row.release.version}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onInstall) {
                    Icon(painterResource(R.drawable.ic_update), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Update to v${row.release.version}")
                }
                FilledTonalButton(onClick = onOpen) { Text("Open") }
            }
        }

        else -> {
            Text(
                text = "Installed and up to date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpen) { Text("Open") }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
