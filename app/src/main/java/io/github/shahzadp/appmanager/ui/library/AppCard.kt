@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.shahzadp.appmanager.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.shahzadp.appmanager.R
import io.github.shahzadp.appmanager.data.Versions
import io.github.shahzadp.appmanager.install.InstallState
import io.github.shahzadp.appmanager.ui.AppRow
import io.github.shahzadp.appmanager.ui.components.AppIcon
import io.github.shahzadp.appmanager.ui.components.DownloadProgress
import io.github.shahzadp.appmanager.ui.components.MetaText
import io.github.shahzadp.appmanager.ui.components.Pill
import io.github.shahzadp.appmanager.ui.components.formatBytes
import kotlin.math.roundToInt

@Composable
fun AppCard(
    row: AppRow,
    onOpenDetails: () -> Unit,
    onAction: () -> Unit,
    onCancel: () -> Unit,
    onPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.978f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "cardPress",
    )

    Surface(
        onClick = onOpenDetails,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(item = row.item, size = 58.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.item.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (row.updateAvailable) {
                            Spacer(Modifier.width(7.dp))
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(8.dp),
                            ) {}
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = row.item.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(7.dp))
                    MetaText(metaLine(row))
                }
                Spacer(Modifier.width(10.dp))
                CardAction(
                    row = row,
                    onAction = onAction,
                    onPermission = onPermission,
                )
            }

            when (val state = row.installState) {
                is InstallState.Downloading -> {
                    Spacer(Modifier.height(12.dp))
                    DownloadProgress(state = state, onCancel = onCancel)
                }

                is InstallState.Failed -> {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onAction) { Text("Retry") }
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun CardAction(
    row: AppRow,
    onAction: () -> Unit,
    onPermission: () -> Unit,
) {
    val state = row.installState
    when {
        state is InstallState.Downloading -> Pill(
            text = "${(state.progress * 100).roundToInt()}%",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )

        state is InstallState.Verifying || state is InstallState.Installing ->
            LoadingIndicator(modifier = Modifier.size(26.dp))

        state is InstallState.NeedsPermission -> Pill(
            text = "Allow",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onPermission,
        )

        state is InstallState.Installed -> Pill(
            text = "Open",
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = painterResource(R.drawable.ic_chevron_right),
            onClick = onAction,
        )

        row.item.isWeb -> Pill(
            text = "Open",
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            onClick = onAction,
        )

        row.updateAvailable -> Pill(
            text = "Update",
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            icon = painterResource(R.drawable.ic_update),
            onClick = onAction,
        )

        row.installed == null -> Pill(
            text = "Get",
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = painterResource(R.drawable.ic_download),
            onClick = onAction,
        )

        else -> Pill(
            text = "Open",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onAction,
        )
    }
}

private fun metaLine(row: AppRow): String {
    if (row.item.isWeb) {
        return "PWA · opens in your browser"
    }
    val size = row.release.apk(row.item)?.size ?: row.item.pinnedSize
    val latest = "v${row.release.version} · ${formatBytes(size)}"
    val installed = row.installed?.versionName
    return when {
        installed == null -> latest
        Versions.isNewer(row.release.version, installed) -> "$latest · installed $installed"
        else -> "installed $installed · $latest"
    }
}
