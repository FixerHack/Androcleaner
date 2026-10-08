package app.androcleaner.core.storage

import app.androcleaner.core.db.FileHashDao
import app.androcleaner.core.db.FileHashEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class DuplicateGroup(val hash: String, val files: List<IndexedFile>) {
    val fileSize: Long get() = files.first().size

    /** Space freed by keeping one copy. */
    val wastedBytes: Long get() = fileSize * (files.size - 1)

    /** The copy we suggest keeping: camera originals first, then the oldest, then the shortest path. */
    val suggestedKeep: IndexedFile
        get() = files.minWith(
            compareBy<IndexedFile>({ if ("/DCIM/Camera/" in it.path) 0 else 1 }, { it.lastModified }, { it.path.length }),
        )
}

/**
 * Finds byte-identical files: group by size → hash of first/last block → full MD5.
 * Full hashes are cached in Room, so repeated scans only hash new or changed files.
 */
@Singleton
class DuplicateFinder @Inject constructor(private val cache: FileHashDao) {

    suspend fun find(index: FileIndex, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): List<DuplicateGroup> =
        withContext(Dispatchers.IO) {
            val sameSize = index.files
                .filter { it.size >= MIN_SIZE }
                .groupBy { it.size }
                .values
                .filter { it.size > 1 }

            val samePrefix = sameSize.flatMap { group ->
                group.groupBy { partialHash(it) }.values.filter { it.size > 1 }
            }
            val candidates = samePrefix.flatten().filter { it.size > PARTIAL_BYTES * 2 }
            val cached = candidates.chunked(500)
                .flatMap { chunk -> cache.get(chunk.map { it.path }) }
                .associateBy { it.path }

            val fresh = ArrayList<FileHashEntity>()
            var done = 0
            val fullHashes = HashMap<String, String>()
            for (file in candidates) {
                coroutineContext.ensureActive()
                val hit = cached[file.path]?.takeIf { it.size == file.size && it.modified == file.lastModified }
                val hash = hit?.hash ?: fullHash(file)?.also {
                    fresh += FileHashEntity(file.path, file.size, file.lastModified, it)
                }
                if (hash != null) fullHashes[file.path] = hash
                onProgress(++done, candidates.size)
            }
            if (fresh.isNotEmpty()) cache.put(fresh)

            samePrefix.flatMap { group ->
                if (group.first().size <= PARTIAL_BYTES * 2) {
                    // The partial hash already covered the whole file.
                    listOf(DuplicateGroup(partialHash(group.first()) ?: "", group))
                } else {
                    group.groupBy { fullHashes[it.path] }
                        .filterKeys { it != null }
                        .filterValues { it.size > 1 }
                        .map { (hash, files) -> DuplicateGroup(hash!!, files) }
                }
            }.sortedByDescending { it.wastedBytes }
        }

    private fun partialHash(file: IndexedFile): String? = runCatching {
        val md = MessageDigest.getInstance("MD5")
        RandomAccessFile(file.path, "r").use { raf ->
            val head = ByteArray(minOf(PARTIAL_BYTES.toLong(), raf.length()).toInt())
            raf.readFully(head)
            md.update(head)
            if (raf.length() > PARTIAL_BYTES * 2) {
                val tail = ByteArray(PARTIAL_BYTES)
                raf.seek(raf.length() - PARTIAL_BYTES)
                raf.readFully(tail)
                md.update(tail)
            } else if (raf.length() > PARTIAL_BYTES) {
                val rest = ByteArray((raf.length() - PARTIAL_BYTES).toInt())
                raf.readFully(rest)
                md.update(rest)
            }
        }
        md.digest().toHex()
    }.getOrNull()

    private fun fullHash(file: IndexedFile): String? = runCatching {
        val md = MessageDigest.getInstance("MD5")
        File(file.path).inputStream().buffered(BUFFER).use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                md.update(buffer, 0, read)
            }
        }
        md.digest().toHex()
    }.getOrNull()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val MIN_SIZE = 1024L
        const val PARTIAL_BYTES = 16 * 1024
        const val BUFFER = 256 * 1024
    }
}
