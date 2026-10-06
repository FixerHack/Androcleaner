package app.androcleaner.feature.scan

import android.content.pm.PackageManager
import app.androcleaner.core.storage.FileCategory
import app.androcleaner.core.storage.FileIndex
import javax.inject.Inject

enum class JunkType {
    /** Gallery thumbnail caches; the system regenerates them. */
    THUMBNAILS,

    /** Temp files, logs, crash dumps, desktop OS litter. */
    TEMP_FILES,

    /** APK installers in storage. */
    APK_FILES,

    /** Folders left behind by apps that are no longer installed. */
    LEFTOVERS,

    EMPTY_FOLDERS,
}

data class JunkItem(
    val path: String,
    val size: Long,
    val preselected: Boolean,
    /** Goes to Androcleaner trash instead of being deleted right away. */
    val useTrash: Boolean,
    val note: JunkNote? = null,
)

enum class JunkNote { APK_INSTALLED, APK_NEWER_INSTALLED, APK_NOT_INSTALLED, APK_UNREADABLE, APP_UNINSTALLED }

data class JunkGroup(val type: JunkType, val items: List<JunkItem>) {
    val totalBytes: Long = items.sumOf { it.size }
}

class JunkAnalyzer @Inject constructor(private val packageManager: PackageManager) {

    fun analyze(index: FileIndex, installed: Map<String, Long>): List<JunkGroup> = listOf(
        JunkGroup(JunkType.THUMBNAILS, thumbnails(index)),
        JunkGroup(JunkType.TEMP_FILES, tempFiles(index)),
        JunkGroup(JunkType.APK_FILES, apkFiles(index, installed)),
        JunkGroup(JunkType.LEFTOVERS, leftovers(index, installed.keys)),
        JunkGroup(JunkType.EMPTY_FOLDERS, index.emptyDirectories.map { JunkItem(it, 0, preselected = true, useTrash = false) }),
    ).filter { it.items.isNotEmpty() }

    private fun thumbnails(index: FileIndex) = index.files
        .filter { "/.thumbnails/" in it.path }
        .map { JunkItem(it.path, it.size, preselected = true, useTrash = false) }

    private fun tempFiles(index: FileIndex) = index.files
        .filter { f ->
            val name = f.name.lowercase()
            name.substringAfterLast('.', "") in TEMP_EXTENSIONS ||
                name in OS_LITTER ||
                name.startsWith("._")
        }
        .map { JunkItem(it.path, it.size, preselected = true, useTrash = false) }

    private fun apkFiles(index: FileIndex, installed: Map<String, Long>) = index.files
        .filter { it.category == FileCategory.APKS && it.name.endsWith(".apk", ignoreCase = true) }
        .map { f ->
            val archive = runCatching { packageManager.getPackageArchiveInfo(f.path, 0) }.getOrNull()
            val installedVersion = archive?.let { installed[it.packageName] }
            val note = when {
                archive == null -> JunkNote.APK_UNREADABLE
                installedVersion == null -> JunkNote.APK_NOT_INSTALLED
                installedVersion > archive.longVersionCode -> JunkNote.APK_NEWER_INSTALLED
                else -> JunkNote.APK_INSTALLED
            }
            JunkItem(
                path = f.path,
                size = f.size,
                preselected = note == JunkNote.APK_INSTALLED || note == JunkNote.APK_NEWER_INSTALLED,
                useTrash = note == JunkNote.APK_NOT_INSTALLED || note == JunkNote.APK_UNREADABLE,
                note = note,
            )
        }

    private fun leftovers(index: FileIndex, installed: Set<String>): List<JunkItem> {
        val root = index.root
        val candidates = HashSet<String>()
        // Android/media/<package>/ and top level folders named like a package (com.example.app/).
        for (f in index.files) {
            val rel = index.relativePath(f.path)
            val parts = rel.split('/')
            when {
                parts.size > 3 && parts[0] == "Android" && parts[1] == "media" -> candidates += "Android/media/${parts[2]}"
                parts.size > 1 && PACKAGE_NAME.matches(parts[0]) -> candidates += parts[0]
            }
        }
        return candidates
            .filter { it.substringAfterLast('/') !in installed }
            .map { rel ->
                val path = "$root/$rel"
                JunkItem(path, index.sizeUnder(path), preselected = false, useTrash = true, note = JunkNote.APP_UNINSTALLED)
            }
            .sortedByDescending { it.size }
    }

    private companion object {
        val TEMP_EXTENSIONS = setOf("tmp", "temp", "log", "dmp", "trace", "bak~", "crdownload", "part")
        val OS_LITTER = setOf(".ds_store", "thumbs.db", "desktop.ini")
        val PACKAGE_NAME = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+){2,}$")
    }
}
