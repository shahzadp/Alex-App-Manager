package io.github.shahzadp.appmanager.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import io.github.shahzadp.appmanager.data.CatalogItem
import io.github.shahzadp.appmanager.data.InstalledApps
import io.github.shahzadp.appmanager.data.Release
import io.github.shahzadp.appmanager.data.ReleaseAsset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

sealed interface InstallState {
    data class Downloading(
        val received: Long,
        val total: Long,
        val bytesPerSecond: Long,
    ) : InstallState {
        val progress: Float
            get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    data object Verifying : InstallState
    data class NeedsPermission(val apkPath: String) : InstallState
    data object Installing : InstallState
    data object Installed : InstallState
    data class Failed(val message: String) : InstallState
}

/**
 * Downloads a release asset into app-private storage and hands it to the
 * package installer. This is the part that replaces "open GitHub in a browser,
 * find the APK, download it, tap it": everything happens in-app.
 */
class InstallController(
    private val context: Context,
    private val installedApps: InstalledApps,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _states = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val states = _states.asStateFlow()

    /** Human-readable one-off messages for snackbars. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Emits whenever an install finishes so the catalogue can re-read versions. */
    val installEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    private val jobs = ConcurrentHashMap<String, Job>()
    private val sessions = ConcurrentHashMap<String, Int>()
    private val apkDir = File(context.filesDir, "apks").apply { mkdirs() }

    init {
        // Keep the installed-app list in step with reality: when a package is
        // removed (or added) anywhere on the device, re-read PackageManager so
        // the UI flips to Get immediately instead of waiting for a resume.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                installEvents.tryEmit(Unit)
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun install(item: CatalogItem, release: Release) {
        if (jobs[item.id]?.isActive == true) return
        val asset = release.apk(item)
        if (asset == null) {
            setState(item.id, InstallState.Failed("This release has no APK attached."))
            return
        }
        jobs[item.id] = scope.launch {
            try {
                val destination = File(apkDir, "${item.id}-${release.version}.apk")
                if (!destination.isUsableCopyOf(asset)) {
                    download(item, asset, destination)
                }
                verifyAndInstall(item, destination)
            } catch (cancelled: CancellationException) {
                _states.update { it - item.id }
                throw cancelled
            } catch (throwable: Throwable) {
                setState(item.id, InstallState.Failed(friendlyMessage(throwable)))
            } finally {
                jobs.remove(item.id)
            }
        }
    }

    fun cancel(item: CatalogItem) {
        jobs.remove(item.id)?.cancel()
        sessions.remove(item.id)?.let { sessionId ->
            runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
        }
        apkDir.listFiles()
            ?.filter { it.name.startsWith("${item.id}-") && it.name.endsWith(".part") }
            ?.forEach { it.delete() }
        _states.update { it - item.id }
    }

    fun clear(item: CatalogItem) {
        _states.update { it - item.id }
    }

    fun open(item: CatalogItem): Boolean {
        val pkg = item.packageName ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun uninstall(item: CatalogItem): Boolean {
        val pkg = item.packageName ?: return false
        // A finished download stays "Installed" long enough to be read; drop it
        // so the UI can show Get once the package is gone.
        if (jobs[item.id]?.isActive != true) {
            _states.update { it - item.id }
        }
        // ACTION_UNINSTALL_PACKAGE is what newer Android builds use to show the
        // system uninstaller; ACTION_DELETE is the older fallback.
        @Suppress("DEPRECATION")
        val actions = listOf(Intent.ACTION_UNINSTALL_PACKAGE, Intent.ACTION_DELETE)
        for (action in actions) {
            val intent = Intent(action).apply {
                data = Uri.parse("package:$pkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        // Last resort: the per-app details screen, which has its own uninstall button.
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(details) }.isSuccess
    }

    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    private suspend fun download(
        item: CatalogItem,
        asset: ReleaseAsset,
        destination: File,
    ) {
        withContext(Dispatchers.IO) {
            val part = File(destination.parentFile, destination.name + ".part")
            part.delete()

            var connection: HttpURLConnection? = null
            try {
                connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "AlexApps/0.1 (+https://github.com/shahzadp)")
                    setRequestProperty("Accept", "application/octet-stream")
                }

                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IOException("Download failed (HTTP $code).")
                }

                val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.size
                var received = 0L
                var lastTick = SystemClock.elapsedRealtime()
                var lastBytes = 0L
                var lastEmit = 0L

                connection.inputStream.use { input ->
                    part.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            received += read

                            val now = SystemClock.elapsedRealtime()
                            if (now - lastEmit >= 90L) {
                                val speed = if (now - lastTick > 0) {
                                    (received - lastBytes) * 1000L / (now - lastTick)
                                } else {
                                    0L
                                }
                                setState(
                                    item.id,
                                    InstallState.Downloading(received, total, speed),
                                )
                                lastEmit = now
                                lastTick = now
                                lastBytes = received
                            }
                        }
                    }
                }

                if (asset.size > 0 && part.length() != asset.size) {
                    throw IOException("The download stopped early. Try again.")
                }
                if (destination.exists()) destination.delete()
                if (!part.renameTo(destination)) {
                    part.copyTo(destination, overwrite = true)
                    part.delete()
                }
                setState(item.id, InstallState.Downloading(asset.size, asset.size, 0L))
            } finally {
                connection?.disconnect()
            }
        }
    }

    private suspend fun verifyAndInstall(item: CatalogItem, file: File) {
        setState(item.id, InstallState.Verifying)
        val pkg = item.packageName ?: throw IOException("No package name configured.")

        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, archiveFlags())
            ?: throw IOException("The downloaded file is not a valid APK.")
        if (archive.packageName != pkg) {
            throw IOException("Downloaded ${archive.packageName}, expected $pkg.")
        }

        val archiveCode = archive.longVersionCodeCompat()
        val current = installedApps.info(pkg)
        if (current != null) {
            if (archiveCode <= current.versionCode) {
                throw IOException("The installed copy is already at this version or newer.")
            }
            val archiveKeys = certificateHashes(archive)
            val installedKeys = certificateHashes(
                context.packageManager.getPackageInfo(pkg, signatureFlags()),
            )
            if (archiveKeys.isNotEmpty() &&
                installedKeys.isNotEmpty() &&
                archiveKeys != installedKeys
            ) {
                throw IOException(
                    "This build is signed with a different key than the installed app. " +
                        "Uninstall the installed copy first.",
                )
            }
        }

        if (!canInstallPackages()) {
            setState(item.id, InstallState.NeedsPermission(file.absolutePath))
            messages.tryEmit("Allow Alex App Manager to install unknown apps, then tap Install again.")
            return
        }

        commitSession(item, file)
    }

    private fun commitSession(item: CatalogItem, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(item.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }

        val sessionId = installer.createSession(params)
        sessions[item.id] = sessionId
        val session = installer.openSession(sessionId)
        try {
            session.openWrite("base.apk", 0, file.length()).use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output, 64 * 1024)
                }
                session.fsync(output)
            }

            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                putExtra(InstallResultReceiver.EXTRA_APP_ID, item.id)
                putExtra(InstallResultReceiver.EXTRA_PACKAGE, item.packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                item.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

            setState(item.id, InstallState.Installing)
            session.commit(pendingIntent.intentSender)
        } catch (throwable: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            sessions.remove(item.id)
            throw throwable
        }
    }

    /** Called by [InstallResultReceiver] on the main thread. */
    fun onSessionResult(intent: Intent) {
        val appId = intent.getStringExtra(InstallResultReceiver.EXTRA_APP_ID) ?: return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtraCompat(Intent.EXTRA_INTENT)
                if (confirm == null) {
                    setState(appId, InstallState.Failed("The installer returned no confirmation screen."))
                    return
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure {
                        setState(appId, InstallState.Failed("Could not open the system installer."))
                    }
                setState(appId, InstallState.Installing)
            }

            PackageInstaller.STATUS_SUCCESS -> {
                sessions.remove(appId)
                setState(appId, InstallState.Installed)
                installEvents.tryEmit(Unit)
                scope.launch {
                    delay(5_000)
                    _states.update { states ->
                        if (states[appId] == InstallState.Installed) states - appId else states
                    }
                }
            }

            else -> {
                sessions.remove(appId)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.takeIf { it.isNotBlank() }
                    ?: statusMessage(status)
                setState(appId, InstallState.Failed(message))
            }
        }
    }

    private fun setState(appId: String, state: InstallState) {
        _states.update { it + (appId to state) }
    }

    private fun friendlyMessage(throwable: Throwable): String {
        if (throwable is java.net.MalformedURLException) {
            return "This release has no usable download link. Pull to refresh and try again."
        }
        val raw = throwable.message
        return if (raw.isNullOrBlank()) "Something went wrong during the install." else raw
    }

    private fun statusMessage(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "The system blocked the install."
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled."
        PackageInstaller.STATUS_FAILURE_INVALID -> "The APK was rejected as invalid."
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "The package conflicts with an app that is already installed."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "There is not enough free storage."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This build is incompatible with this device."
        else -> "Install failed (status $status)."
    }

    private fun archiveFlags(): Int =
        if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun signatureFlags(): Int = archiveFlags()

    @Suppress("DEPRECATION")
    private fun certificateHashes(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            info.signatures.orEmpty()
        }
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.mapTo(mutableSetOf()) { signature ->
            digest.digest(signature.toByteArray()).joinToString("") { byte ->
                "%02x".format(byte)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableExtraCompat(name: String): Intent? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(name, Intent::class.java)
        } else {
            getParcelableExtra(name)
        }

    private fun File.isUsableCopyOf(asset: ReleaseAsset): Boolean =
        exists() && length() > 0 && (asset.size <= 0 || length() == asset.size)
}
