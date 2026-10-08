package app.androcleaner.core.storage

import app.androcleaner.core.db.FileHashDao
import app.androcleaner.core.db.FileHashEntity
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

class DuplicateFinderTest {

    @get:Rule val tmp = TemporaryFolder()

    private class FakeDao : FileHashDao {
        val stored = HashMap<String, FileHashEntity>()
        override suspend fun get(paths: List<String>) = paths.mapNotNull(stored::get)
        override suspend fun put(items: List<FileHashEntity>) = items.forEach { stored[it.path] = it }
    }

    private fun file(path: String, bytes: ByteArray) =
        File(tmp.root, path).apply { parentFile!!.mkdirs(); writeBytes(bytes) }

    private fun index() = runBlocking {
        StorageScanner().scan(tmp.root).filterIsInstance<ScanEvent.Finished>().first().index
    }

    @Test
    fun `finds identical files and ignores same-size different files`() = runBlocking {
        val big = Random(1).nextBytes(100_000)
        val small = Random(2).nextBytes(5_000)
        file("DCIM/Camera/a.jpg", big)
        file("Download/a (1).jpg", big)
        file("Download/other.jpg", Random(3).nextBytes(100_000)) // same size, different content
        file("x/s1.bin", small)
        file("y/s2.bin", small)
        // Same head and tail, different middle: must not be reported.
        file("m/1.bin", big.copyOf().also { it[50_000] = 1 })
        file("m/2.bin", big.copyOf().also { it[50_000] = 2 })

        val dao = FakeDao()
        val groups = DuplicateFinder(dao).find(index())

        val names = groups.map { g -> g.files.map { it.name }.sorted() }.toSet()
        assertEquals(setOf(listOf("a (1).jpg", "a.jpg"), listOf("s1.bin", "s2.bin")), names)
        val photo = groups.first { it.fileSize == 100_000L }
        assertEquals("a.jpg", photo.suggestedKeep.name) // camera original is kept
        assertEquals(100_000L, photo.wastedBytes)
    }

    @Test
    fun `uses cached hashes on second run`() = runBlocking {
        val big = Random(4).nextBytes(80_000)
        file("a/1.bin", big)
        file("b/2.bin", big)
        val dao = FakeDao()
        DuplicateFinder(dao).find(index())
        assertEquals(2, dao.stored.size)
        // Corrupt cache entries; a valid cache hit means files aren't re-hashed.
        dao.stored.replaceAll { _, v -> v.copy(hash = "cached") }
        val groups = DuplicateFinder(dao).find(index())
        assertEquals("cached", groups.single().hash)
    }
}
