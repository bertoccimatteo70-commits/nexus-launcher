package com.nexus.core.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.util.LruCache
import androidx.core.content.ContextCompat
import com.nexus.core.common.AppCategorizer
import com.nexus.core.model.AppKey
import com.nexus.core.model.LauncherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Reads the activities actually installed on the device through LauncherApps (the
 * same API other launchers use), keeps them fresh on package changes and loads icons
 * through a bounded memory cache. No fake or demo applications are ever injected.
 */
class AppCatalogRepository(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val packageManager = appContext.packageManager
    private val user: UserHandle = Process.myUserHandle()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _apps = MutableStateFlow<List<LauncherApp>>(emptyList())
    val apps: StateFlow<List<LauncherApp>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val iconCache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }
    private val iconSemaphore = Semaphore(3)

    private val packagesReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    init {
        refresh()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addDataScheme("package")
        }
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                packagesReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    fun refresh() {
        scope.launch {
            _loading.value = true
            val loaded = runCatching { loadApps() }
            loaded.onFailure { _apps.value = emptyList() }
            loaded.onSuccess { _apps.value = it }
            _loading.value = false
        }
    }

    suspend fun reload() {
        withContext(Dispatchers.IO) {
            _loading.value = true
            runCatching { loadApps() }.onSuccess { _apps.value = it }
            _loading.value = false
        }
    }

    suspend fun icon(appKey: String, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val safeSize = sizePx.coerceIn(24, 256)
        val cacheKey = "$appKey@$safeSize"
        iconCache.get(cacheKey)?.let { return@withContext it }
        iconSemaphore.withPermit {
            iconCache.get(cacheKey)?.let { return@withPermit it }
            val bitmap = runCatching { loadIconBitmap(appKey, safeSize) }.getOrNull()
            if (bitmap != null) iconCache.put(cacheKey, bitmap)
            bitmap
        }
    }

    fun clearIconCache() {
        iconCache.evictAll()
    }

    private fun loadApps(): List<LauncherApp> {
        val activities = launcherApps.getActivityList(null, user)
        return activities.asSequence()
            .filter { it.componentName.packageName != appContext.packageName }
            .map { activity ->
                val packageName = activity.componentName.packageName
                val activityName = activity.componentName.className
                val label = activity.label?.toString()?.trim().orEmpty().ifBlank { packageName }
                val application = activity.applicationInfo
                val platformCategory = runCatching { application.category }.getOrNull()
                LauncherApp(
                    key = "$packageName/$activityName",
                    packageName = packageName,
                    activityName = activityName,
                    label = label,
                    category = AppCategorizer.categorize(packageName, label, platformCategory),
                    isSystem = (application.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .distinctBy { it.key }
            .sortedWith { first, second ->
                first.label.compareTo(second.label, ignoreCase = true)
            }
            .toList()
    }

    private fun loadIconBitmap(appKey: String, sizePx: Int): Bitmap? {
        val parsed = AppKey.parse(appKey) ?: return null
        val component = ComponentName(parsed.packageName, parsed.activityName)
        val drawable: Drawable = runCatching { packageManager.getActivityIcon(component) }
            .recoverCatching { packageManager.getApplicationIcon(parsed.packageName) }
            .getOrNull() ?: return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }

    private fun cacheSizeKb(): Int =
        (Runtime.getRuntime().maxMemory() / 1024 / 16)
            .toInt()
            .coerceIn(3 * 1024, 12 * 1024)
}
