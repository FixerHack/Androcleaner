package app.androcleaner.feature.cache

import android.accessibilityservice.AccessibilityService
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.androcleaner.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Accessibility service that only ever acts while the user runs a cache clean from Androcleaner.
 * It never reads content from other apps; it looks only at the Settings app's own screens.
 */
@AndroidEntryPoint
class CacheCleanerService : AccessibilityService(), SettingsAutomation {

    @Inject lateinit var cleaner: AppCacheCleaner

    private var overlay: CleanOverlay? = null

    override fun onServiceConnected() {
        cleaner.service = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleaner.service = null
        cleaner.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleaner.service = null
        hideOverlay()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /**
     * Opens the app's info page and presses Storage → Clear cache. Returns false if a step wasn't found.
     * Must be called off the main thread: walking the node tree makes blocking IPC calls.
     */
    override suspend fun clearCacheOf(packageName: String, labels: SettingsLabels): Boolean {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            ),
        )

        var attempts = 0
        val storageEntry = awaitNode(STEP_TIMEOUT_MS) { root ->
            findByText(root, labels.storageEntry).also {
                // The entry may be below the fold on small screens.
                if (it == null && ++attempts % 4 == 0) scrollForward(root)
            }
        } ?: return false
        if (!click(storageEntry)) return false

        awaitNode(STEP_TIMEOUT_MS) { root -> findByText(root, labels.clearCache) } ?: return false
        // Right after opening, Settings is still computing sizes and keeps the button disabled.
        // If it never becomes enabled, the cache is already empty — that still counts as done.
        val enabledButton = awaitNode(ENABLE_TIMEOUT_MS) { root ->
            findByText(root, labels.clearCache)?.takeIf { clickableAncestor(it)?.isEnabled == true }
        }
        if (enabledButton != null) click(enabledButton)
        delay(AFTER_CLICK_MS)
        return true
    }

    override fun returnToApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    }

    override fun showOverlay(onStop: () -> Unit) {
        if (overlay == null) overlay = CleanOverlay(localizedContext(), this, onStop).also { it.show() }
    }

    override fun updateOverlay(appLabel: String, index: Int, total: Int) {
        overlay?.update(appLabel, index, total)
    }

    override fun hideOverlay() {
        overlay?.hide()
        overlay = null
    }

    /** Service contexts ignore the per-app language, so apply it by hand for the overlay texts. */
    private fun localizedContext(): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
        val locales = getSystemService(LocaleManager::class.java).applicationLocales
        if (locales.isEmpty) return this
        val config = Configuration(resources.configuration).apply { setLocales(locales) }
        return createConfigurationContext(config)
    }

    private suspend fun awaitNode(
        timeoutMs: Long,
        find: (AccessibilityNodeInfo) -> AccessibilityNodeInfo?,
    ): AccessibilityNodeInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            rootInActiveWindow?.let { root -> find(root)?.let { return it } }
            delay(POLL_MS)
        }
        return null
    }

    private fun findByText(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var fuzzy: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = (node.text ?: node.contentDescription)?.let(SettingsLabels::normalize)
            if (text != null) {
                if (text in labels) return node
                if (fuzzy == null && labels.any { text.startsWith(it) }) fuzzy = node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return fuzzy
    }

    private fun scrollForward(root: AccessibilityNodeInfo) {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable) {
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                return
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null && !current.isClickable) current = current.parent
        return current
    }

    private fun click(node: AccessibilityNodeInfo): Boolean =
        clickableAncestor(node)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true

    private companion object {
        const val STEP_TIMEOUT_MS = 6_000L
        const val ENABLE_TIMEOUT_MS = 3_000L
        const val POLL_MS = 150L
        const val AFTER_CLICK_MS = 450L
    }
}
