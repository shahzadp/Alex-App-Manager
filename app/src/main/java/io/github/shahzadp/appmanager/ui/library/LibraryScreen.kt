@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.shahzadp.appmanager.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.shahzadp.appmanager.R
import io.github.shahzadp.appmanager.data.Section
import io.github.shahzadp.appmanager.ui.AppRow
import io.github.shahzadp.appmanager.ui.LibraryState
import io.github.shahzadp.appmanager.ui.components.EmptyState
import io.github.shahzadp.appmanager.ui.components.ErrorBanner
import io.github.shahzadp.appmanager.ui.components.FilterPill
import io.github.shahzadp.appmanager.ui.components.HeroBanner

enum class LibraryFilter(val label: String) {
    ALL("All"),
    APPS("Apps"),
    GAMES("Games"),
    WEB("Web"),
}

@Composable
fun LibraryScreen(
    state: LibraryState,
    filter: LibraryFilter,
    updatesOnly: Boolean,
    onFilter: (LibraryFilter) -> Unit,
    onOpen: (AppRow) -> Unit,
    onAction: (AppRow) -> Unit,
    onCancel: (AppRow) -> Unit,
    onPermission: () -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onOpenProfile: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val rows = remember(state.rows, filter, updatesOnly) {
        state.rows.filter { row ->
            when {
                updatesOnly -> row.updateAvailable
                filter == LibraryFilter.ALL -> true
                filter == LibraryFilter.APPS -> row.item.section == Section.APP
                filter == LibraryFilter.GAMES -> row.item.section == Section.GAME
                filter == LibraryFilter.WEB -> row.item.section == Section.WEB
                else -> true
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!updatesOnly) {
                item(key = "hero") {
                    HeroBanner(
                        installed = state.installedCount,
                        updates = state.updateCount,
                        total = state.rows.size,
                    )
                }
            }

            if (state.error != null) {
                item(key = "error") {
                    ErrorBanner(
                        message = state.error,
                        onRetry = { onRefresh() },
                        onDismiss = onDismissError,
                    )
                }
            }

            if (updatesOnly) {
                item(key = "updates-heading") {
                    Column(Modifier.padding(top = 4.dp)) {
                        Text(
                            text = "Updates",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = if (rows.isEmpty()) {
                                "Every installed app is on its latest release."
                            } else {
                                "${rows.size} app${if (rows.size == 1) "" else "s"} behind the latest release."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item(key = "filters") {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LibraryFilter.entries.forEach { option ->
                            FilterPill(
                                selected = filter == option,
                                label = option.label,
                                onClick = { onFilter(option) },
                            )
                        }
                    }
                }
            }

            items(items = rows, key = { it.item.id }) { row ->
                AppCard(
                    row = row,
                    onOpenDetails = { onOpen(row) },
                    onAction = { onAction(row) },
                    onCancel = { onCancel(row) },
                    onPermission = onPermission,
                )
            }

            if (rows.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = if (updatesOnly) "Nothing to update" else "Nothing here",
                        body = if (updatesOnly) {
                            "Everything installed is already on the newest release."
                        } else {
                            "That filter has no entries yet. Try another category."
                        },
                    )
                }
            }

            item(key = "footer") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TextButton(onClick = onOpenProfile) {
                        Icon(
                            painter = painterResource(R.drawable.ic_github),
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("github.com/shahzadp")
                    }
                    Text(
                        text = "Alex App Manager · apps, games and the portfolio from GitHub",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Box(Modifier.height(4.dp))
                }
            }
        }
    }
}
