package app.androcleaner.core.storage

import java.io.File

data class IndexedFile(
    val path: String,
    val size: Long,
    val lastModified: Long,
    val category: FileCategory,
) {
    val name: String get() = path.substringAfterLast('/')
    val parent: String get() = path.substringBeforeLast('/')
}

/** Snapshot of everything found on shared storage during the last scan. */
data class FileIndex(
    val root: String,
    val files: List<IndexedFile>,
    /** Directories with no files anywhere below them, topmost only. */
    val emptyDirectories: List<String>,
    val scannedAt: Long,
) {
    val totalBytes: Long = files.sumOf { it.size }

    val bytesByCategory: Map<FileCategory, Long> =
        files.groupingBy { it.category }.fold(0L) { acc, f -> acc + f.size }

    fun sizeUnder(directory: String): Long {
        val prefix = directory.trimEnd('/') + "/"
        return files.sumOf { if (it.path.startsWith(prefix)) it.size else 0L }
    }

    /** Returns a copy without the given paths (files or whole directories). */
    fun without(paths: Collection<String>): FileIndex {
        if (paths.isEmpty()) return this
        val exact = paths.toHashSet()
        val prefixes = paths.map { it.trimEnd('/') + "/" }
        fun removed(p: String) = p in exact || prefixes.any { p.startsWith(it) }
        return copy(
            files = files.filterNot { removed(it.path) },
            emptyDirectories = emptyDirectories.filterNot { removed(it) },
        )
    }

    fun relativePath(path: String): String = path.removePrefix(root).trimStart('/')

    companion object {
        fun isUnder(path: String, directory: File): Boolean =
            path.startsWith(directory.absolutePath.trimEnd('/') + "/")
    }
}
