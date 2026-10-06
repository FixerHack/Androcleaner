package app.androcleaner.core.trash

import android.content.Context
import android.media.MediaScannerConnection
import app.androcleaner.core.storage.TrashLocations
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class TrashEntry(
    val id: String,
    val originalPath: String,
    val trashedPath: String,
    val size: Long,
    val trashedAt: Long,
    val isDirectory: Boolean = false,
) {
    val name: String get() = originalPath.substringAfterLast('/')
    val expiresAt: Long get() = trashedAt + TrashRepository.RETENTION_MS
}

data class DeleteResult(val freedBytes: Long, val failedPaths: List<String>)

/**
 * Androcleaner's own recycle bin: user files are moved to /sdcard/.androcleaner-trash
 * (a rename on the same volume, so it is instant) and purged after 30 days.
 */
@Singleton
class TrashRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val manifest get() = File(TrashLocations.root, "manifest.json")

    private val _entries = MutableStateFlow<List<TrashEntry>>(emptyList())
    val entries: StateFlow<List<TrashEntry>> = _entries.asStateFlow()

    suspend fun load() = io {
        _entries.value = readManifest().sortedByDescending { it.trashedAt }
    }

    /** Moves files or directories to the trash. */
    suspend fun moveToTrash(paths: List<String>): DeleteResult = io {
        val root = TrashLocations.root.apply { mkdirs() }
        File(root, ".nomedia").createNewFile()
        val current = readManifest().toMutableList()
        val failed = mutableListOf<String>()
        var moved = 0L
        val now = System.currentTimeMillis()

        for (path in paths) {
            val source = File(path)
            if (!source.exists()) continue
            val id = UUID.randomUUID().toString()
            val target = File(root, "$id/${source.name}")
            target.parentFile?.mkdirs()
            val size = sizeOf(source)
            if (source.renameTo(target)) {
                moved += size
                current += TrashEntry(id, path, target.absolutePath, size, now, isDirectory = target.isDirectory)
            } else {
                File(root, id).delete()
                failed += path
            }
        }
        writeManifest(current)
        notifyMediaStore(paths)
        DeleteResult(moved, failed)
    }

    suspend fun restore(ids: Set<String>): List<String> = io {
        val failed = mutableListOf<String>()
        val restored = mutableListOf<String>()
        val remaining = readManifest().filterNot { entry ->
            if (entry.id !in ids) return@filterNot false
            val target = File(entry.originalPath)
            target.parentFile?.mkdirs()
            val ok = !target.exists() && File(entry.trashedPath).renameTo(target)
            if (ok) {
                File(TrashLocations.root, entry.id).deleteRecursively()
                restored += entry.originalPath
            } else {
                failed += entry.originalPath
            }
            ok
        }
        writeManifest(remaining)
        notifyMediaStore(restored)
        failed
    }

    suspend fun deleteForever(ids: Set<String>): Long = io {
        var freed = 0L
        val remaining = readManifest().filterNot { entry ->
            (entry.id in ids).also { if (it) freed += deleteEntryFiles(entry) }
        }
        writeManifest(remaining)
        freed
    }

    suspend fun purgeExpired() = io {
        val now = System.currentTimeMillis()
        val remaining = readManifest().filterNot { entry ->
            (entry.expiresAt < now).also { if (it) deleteEntryFiles(entry) }
        }
        writeManifest(remaining)
    }

    /** Permanently deletes files that are safe to remove without a trash step (cache, temp files). */
    suspend fun deletePermanently(paths: List<String>): DeleteResult = io {
        val failed = mutableListOf<String>()
        var freed = 0L
        for (path in paths) {
            val file = File(path)
            if (!file.exists()) continue
            val size = sizeOf(file)
            if (file.deleteRecursively()) freed += size else failed += path
        }
        notifyMediaStore(paths)
        DeleteResult(freed, failed)
    }

    private fun deleteEntryFiles(entry: TrashEntry): Long {
        File(TrashLocations.root, entry.id).deleteRecursively()
        return entry.size
    }

    private fun sizeOf(file: File): Long =
        if (file.isDirectory) file.walkBottomUp().filter { it.isFile }.sumOf { it.length() } else file.length()

    private fun readManifest(): List<TrashEntry> =
        runCatching { json.decodeFromString<List<TrashEntry>>(manifest.readText()) }.getOrDefault(emptyList())

    private fun writeManifest(entries: List<TrashEntry>) {
        if (entries.isEmpty() && !manifest.exists()) {
            _entries.value = emptyList()
            return
        }
        manifest.parentFile?.mkdirs()
        val tmp = File(manifest.parentFile, "manifest.json.tmp")
        tmp.writeText(json.encodeToString(entries))
        tmp.renameTo(manifest)
        _entries.value = entries.sortedByDescending { it.trashedAt }
    }

    private fun notifyMediaStore(paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }

    private suspend fun <T> io(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock { block() } }

    companion object {
        val RETENTION_MS: Long = TimeUnit.DAYS.toMillis(30)
    }
}
