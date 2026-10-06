package app.androcleaner.core.storage

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.nio.file.Files
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

sealed interface ScanEvent {
    data class Progress(val filesScanned: Int, val bytesScanned: Long, val currentDirectory: String) : ScanEvent
    data class Finished(val index: FileIndex) : ScanEvent
}

/**
 * Walks shared storage (/sdcard) iteratively and builds a [FileIndex].
 * Needs "All files access"; without it only media folders are visible.
 */
@Singleton
class StorageScanner @Inject constructor() {

    fun scan(root: File = Environment.getExternalStorageDirectory()): Flow<ScanEvent> = flow {
        val rootPath = root.absolutePath
        val skipped = setOf(
            "$rootPath/Android/data",
            "$rootPath/Android/obb",
            "$rootPath/${TrashLocations.DIRECTORY_NAME}",
        )

        val files = ArrayList<IndexedFile>(16_384)
        // directory -> (direct file count, child directories)
        val dirFileCounts = HashMap<String, Int>()
        val dirChildren = HashMap<String, MutableList<String>>()

        var bytes = 0L
        var lastEmit = 0L
        val stack = ArrayDeque<File>().apply { addLast(root) }

        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dir = stack.removeLast()
            val dirPath = dir.absolutePath
            val children = dir.listFiles() ?: continue
            var fileCount = 0
            val subdirs = ArrayList<String>()

            for (child in children) {
                if (child.isDirectory) {
                    if (child.absolutePath in skipped || Files.isSymbolicLink(child.toPath())) {
                        fileCount++ // treat as non-empty so parents are never reported empty
                        continue
                    }
                    subdirs += child.absolutePath
                    stack.addLast(child)
                } else {
                    fileCount++
                    val size = child.length()
                    bytes += size
                    files += IndexedFile(
                        path = child.absolutePath,
                        size = size,
                        lastModified = child.lastModified(),
                        category = FileCategory.fromFileName(child.name),
                    )
                }
            }
            dirFileCounts[dirPath] = fileCount
            dirChildren[dirPath] = subdirs

            val now = System.currentTimeMillis()
            if (now - lastEmit > PROGRESS_INTERVAL_MS) {
                lastEmit = now
                emit(ScanEvent.Progress(files.size, bytes, dirPath.removePrefix(rootPath)))
            }
        }

        emit(
            ScanEvent.Finished(
                FileIndex(
                    root = rootPath,
                    files = files,
                    emptyDirectories = findEmptyDirectories(rootPath, dirFileCounts, dirChildren),
                    scannedAt = System.currentTimeMillis(),
                ),
            ),
        )
    }.flowOn(Dispatchers.IO)

    private fun findEmptyDirectories(
        rootPath: String,
        fileCounts: Map<String, Int>,
        children: Map<String, List<String>>,
    ): List<String> {
        val subtreeEmpty = HashMap<String, Boolean>()
        // Iterative post-order so deep trees don't overflow the stack.
        val order = ArrayList<String>()
        val stack = ArrayDeque<String>().apply { addLast(rootPath) }
        while (stack.isNotEmpty()) {
            val d = stack.removeLast()
            order += d
            children[d]?.forEach { stack.addLast(it) }
        }
        for (d in order.asReversed()) {
            subtreeEmpty[d] = (fileCounts[d] ?: 1) == 0 &&
                children[d].orEmpty().all { subtreeEmpty[it] == true }
        }

        val protected = PROTECTED_DIRECTORIES.map { "$rootPath/$it" }.toSet() + rootPath
        fun reportable(d: String) = subtreeEmpty[d] == true && d !in protected
        // Report only the topmost empty directory of each empty subtree.
        return order.filter { reportable(it) && !reportable(it.substringBeforeLast('/')) }.sorted()
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 80L

        /** Standard folders apps and the system expect to exist. */
        val PROTECTED_DIRECTORIES = listOf(
            "Alarms", "Android", "Android/media", "Audiobooks", "DCIM", "DCIM/Camera", "Documents",
            "Download", "Movies", "Music", "Notifications", "Pictures", "Pictures/Screenshots",
            "Podcasts", "Recordings", "Ringtones",
        )
    }
}
