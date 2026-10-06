package app.androcleaner.core.storage

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageScannerTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun scan(root: File): FileIndex = runBlocking {
        StorageScanner().scan(root).filterIsInstance<ScanEvent.Finished>().first().index
    }

    private fun file(path: String, bytes: Int = 10) =
        File(tmp.root, path).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(bytes)) }

    private fun dir(path: String) = File(tmp.root, path).apply { mkdirs() }

    @Test
    fun `indexes files with sizes and categories`() {
        file("DCIM/Camera/a.jpg", 100)
        file("Music/song.mp3", 50)
        file("Download/app.apk", 30)

        val index = scan(tmp.root)

        assertEquals(3, index.files.size)
        assertEquals(180L, index.totalBytes)
        assertEquals(100L, index.bytesByCategory[FileCategory.IMAGES])
        assertEquals(30L, index.bytesByCategory[FileCategory.APKS])
    }

    @Test
    fun `reports only topmost empty directories and never protected ones`() {
        dir("old/a/b/c") // nested empty tree -> report "old" only
        dir("Music") // protected standard folder
        dir("DCIM/empty") // empty child of a protected folder
        file("keep/x.txt")
        dir("keep/empty")

        val rel = scan(tmp.root).emptyDirectories.map { it.removePrefix(tmp.root.absolutePath + "/") }

        assertEquals(listOf("DCIM/empty", "keep/empty", "old"), rel)
    }

    @Test
    fun `skips Android data and trash folder`() {
        file("Android/data/com.example/cache.bin")
        file("${TrashLocations.DIRECTORY_NAME}/id/x.jpg")
        file("a.txt")

        val index = scan(tmp.root)

        assertEquals(listOf("a.txt"), index.files.map { it.name })
        assertEquals(emptyList<String>(), index.emptyDirectories.map { it.substringAfterLast('/') })
    }

    @Test
    fun `without removes files and whole directories`() {
        file("a/1.txt")
        file("a/2.txt")
        file("b/3.txt")
        val index = scan(tmp.root)

        val result = index.without(listOf("${tmp.root.absolutePath}/a"))

        assertEquals(listOf("3.txt"), result.files.map { it.name })
    }
}
