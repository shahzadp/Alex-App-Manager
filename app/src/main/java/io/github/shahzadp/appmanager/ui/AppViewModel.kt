package io.github.shahzadp.appmanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shahzadp.appmanager.AlexApps
import io.github.shahzadp.appmanager.data.CatalogItem
import io.github.shahzadp.appmanager.data.FetchOutcome
import io.github.shahzadp.appmanager.data.InstalledApp
import io.github.shahzadp.appmanager.data.Release
import io.github.shahzadp.appmanager.data.ThemeMode
import io.github.shahzadp.appmanager.data.Versions
import io.github.shahzadp.appmanager.install.InstallState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class AppRow(
    val item: CatalogItem,
    val release: Release,
    val installed: InstalledApp?,
    val installState: InstallState?,
) {
    val updateAvailable: Boolean
        get() = !item.isWeb &&
            installed != null &&
            Versions.isNewer(release.version, installed.versionName)

    val isInstalled: Boolean get() = installed != null
}

data class LibraryState(
    val rows: List<AppRow> = emptyList(),
    val refreshing: Boolean = false,
    val error: String? = null,
    val lastRefresh: Long = 0L,
) {
    val updateCount: Int get() = rows.count { it.updateAvailable }
    val installedCount: Int get() = rows.count { !it.item.isWeb && it.isInstalled }
    val activeDownloads: Int get() = rows.count {
        it.installState is InstallState.Downloading ||
            it.installState is InstallState.Verifying ||
            it.installState is InstallState.Installing
    }

    fun row(id: String): AppRow? = rows.firstOrNull { it.item.id == id }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val container = requireNotNull(AlexApps.container)

    val catalog = container.catalog.catalog
    val items: List<CatalogItem> = catalog.items
    val profileUrl: String = catalog.profileUrl

    private val releases = MutableStateFlow(
        items.associate { it.id to container.releases.load(it) },
    )
    private val installed = MutableStateFlow(container.installed.snapshot(items))
    private val refreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<String?>(null)
    private val installStates = container.installs.states

    private val theme = MutableStateFlow(container.prefs.themeMode)
    val themeMode: StateFlow<ThemeMode> = theme.asStateFlow()

    val snackbarMessages = container.installs.messages

    private data class Base(
        val releases: Map<String, Release>,
        val installed: Map<String, InstalledApp>,
        val refreshing: Boolean,
        val states: Map<String, InstallState>,
    )

    private val base = combine(
        releases,
        installed,
        refreshing,
        installStates,
    ) { releases, installed, refreshing, states ->
        Base(releases, installed, refreshing, states)
    }

    val state: StateFlow<LibraryState> = combine(base, refreshError) { snapshot, error ->
        LibraryState(
            rows = items.map { item ->
                AppRow(
                    item = item,
                    release = snapshot.releases[item.id] ?: container.releases.pinned(item),
                    installed = item.packageName?.let(snapshot.installed::get),
                    installState = snapshot.states[item.id],
                )
            },
            refreshing = snapshot.refreshing,
            error = error,
            lastRefresh = snapshot.releases.values.maxOfOrNull { it.fetchedAt } ?: 0L,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryState(rows = items.map { item ->
            AppRow(
                item = item,
                release = container.releases.load(item),
                installed = item.packageName?.let { installed.value[it] },
                installState = installStates.value[item.id],
            )
        }),
    )

    init {
        viewModelScope.launch {
            container.installs.installEvents.collect {
                installed.value = container.installed.snapshot(items)
            }
        }
        refresh(force = false)
    }

    fun refresh(force: Boolean = false) {
        if (refreshing.value) return
        val now = System.currentTimeMillis()
        val targets = items.filter { item ->
            item.repo != null && (force || now - container.releases.lastFetch(item) > CACHE_TTL_MS)
        }
        if (targets.isEmpty()) return

        viewModelScope.launch {
            refreshing.value = true
            refreshError.value = null
            val failures = mutableListOf<String>()
            val gate = Semaphore(3)
            coroutineScope {
                targets.map { item ->
                    async {
                        gate.withPermit {
                            when (val outcome = container.releases.fetch(item)) {
                                is FetchOutcome.Updated ->
                                    releases.update { it + (item.id to outcome.release) }

                                is FetchOutcome.Unchanged -> Unit

                                is FetchOutcome.Failed -> synchronized(failures) {
                                    failures += outcome.message
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
            if (failures.isNotEmpty()) {
                refreshError.value = if (failures.size == targets.size) {
                    failures.first()
                } else {
                    "${failures.size} of ${targets.size} release checks failed. ${failures.first()}"
                }
            }
            refreshing.value = false
            viewModelScope.launch(Dispatchers.IO) {
                container.icons.maybeRefresh(items, releases.value)
            }
        }
    }

    fun refreshInstalled() {
        installed.value = container.installed.snapshot(items)
    }

    fun install(item: CatalogItem) {
        val release = releases.value[item.id] ?: container.releases.pinned(item)
        container.installs.install(item, release)
    }

    fun cancel(item: CatalogItem) = container.installs.cancel(item)

    fun open(item: CatalogItem) {
        if (!container.installs.open(item)) {
            viewModelScope.launch {
                container.installs.messages.emit("${item.name} is not installed yet.")
            }
        }
    }

    fun uninstall(item: CatalogItem) {
        if (!container.installs.uninstall(item)) {
            viewModelScope.launch {
                container.installs.messages.emit("Couldn't open the uninstaller for ${item.name}.")
            }
        }
    }

    fun openPermissionSettings() = container.installs.openInstallPermissionSettings()

    fun setTheme(mode: ThemeMode) {
        container.prefs.themeMode = mode
        theme.value = mode
    }

    fun cycleTheme() {
        setTheme(
            when (theme.value) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            },
        )
    }

    fun dismissError() {
        refreshError.value = null
    }

    private companion object {
        const val CACHE_TTL_MS = 30 * 60 * 1000L
    }
}
