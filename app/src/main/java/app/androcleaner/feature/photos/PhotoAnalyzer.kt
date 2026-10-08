package app.androcleaner.feature.photos

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import app.androcleaner.core.db.PhotoFeatureDao
import app.androcleaner.core.db.PhotoFeatureEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class Photo(
    val id: Long,
    val uri: Uri,
    val path: String,
    val size: Long,
    val takenAt: Long,
    val relativePath: String,
    val isScreenshot: Boolean,
)

data class AnalyzedPhoto(val photo: Photo, val dHash: Long, val sharpness: Float)

data class SimilarGroup(val photos: List<AnalyzedPhoto>) {
    /** Sharpest photo, larger file as a tie-breaker. */
    val best: AnalyzedPhoto get() = photos.maxWith(compareBy({ it.sharpness }, { it.photo.size }))
    val extraBytes: Long get() = photos.sumOf { it.photo.size } - best.photo.size
}

data class PhotoReport(
    val photos: List<AnalyzedPhoto>,
    val similar: List<SimilarGroup>,
    val blurry: List<AnalyzedPhoto>,
    val screenshots: List<Photo>,
)

/**
 * Offline photo analysis: a 64-bit difference hash (dHash) for visual similarity and the
 * variance of the Laplacian as a sharpness score. Both are computed from the system
 * thumbnail and cached in Room.
 */
@Singleton
class PhotoAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: PhotoFeatureDao,
) {

    fun queryPhotos(): List<Photo> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val photos = ArrayList<Photo>()
        context.contentResolver.query(collection, projection, null, null, "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val data = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val size = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val taken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val modified = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val relative = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val bucket = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val path = c.getString(data) ?: continue
                if ("/.androcleaner-trash/" in path) continue
                val rel = c.getString(relative).orEmpty()
                val bucketName = c.getString(bucket).orEmpty()
                val mediaId = c.getLong(id)
                photos += Photo(
                    id = mediaId,
                    uri = ContentUris.withAppendedId(collection, mediaId),
                    path = path,
                    size = c.getLong(size),
                    takenAt = c.getLong(taken).takeIf { it > 0 } ?: TimeUnit.SECONDS.toMillis(c.getLong(modified)),
                    relativePath = rel,
                    isScreenshot = SCREENSHOT_DIRS.any { it in rel.lowercase() || it == bucketName.lowercase() },
                )
            }
        }
        return photos
    }

    suspend fun analyze(onProgress: (done: Int, total: Int) -> Unit): PhotoReport = withContext(Dispatchers.IO) {
        val photos = queryPhotos()
        val cached = cache.all().associateBy { it.mediaId }
        val camera = photos.filterNot { it.isScreenshot }
        val done = AtomicInteger(0)
        val fresh = ArrayList<PhotoFeatureEntity>()

        val analyzed = coroutineScope {
            camera.chunked(CHUNK).map { chunk ->
                async(Dispatchers.Default.limitedParallelism(PARALLELISM)) {
                    chunk.mapNotNull { photo ->
                        val hit = cached[photo.id]?.takeIf { it.size == photo.size && it.modified == photo.takenAt }
                        val features = hit ?: computeFeatures(photo)?.also { synchronized(fresh) { fresh += it } }
                        onProgress(done.incrementAndGet(), camera.size)
                        features?.let { AnalyzedPhoto(photo, it.dHash, it.sharpness) }
                    }
                }
            }.awaitAll().flatten()
        }
        if (fresh.isNotEmpty()) cache.put(fresh)

        PhotoReport(
            photos = analyzed,
            similar = findSimilar(analyzed),
            blurry = analyzed.filter { it.sharpness < BLURRY_THRESHOLD }.sortedBy { it.sharpness },
            screenshots = photos.filter { it.isScreenshot },
        )
    }

    private fun computeFeatures(photo: Photo): PhotoFeatureEntity? = runCatching {
        val thumb = context.contentResolver.loadThumbnail(photo.uri, Size(THUMB, THUMB), null)
        val features = PhotoFeatureEntity(
            mediaId = photo.id,
            modified = photo.takenAt,
            size = photo.size,
            dHash = ImageMath.dHash(thumb),
            sharpness = ImageMath.sharpness(thumb),
        )
        thumb.recycle()
        features
    }.getOrNull()

    /** Bursts (close in time and look) plus near-identical copies regardless of time. */
    private fun findSimilar(photos: List<AnalyzedPhoto>): List<SimilarGroup> {
        val sorted = photos.sortedBy { it.photo.takenAt }
        val parent = IntArray(sorted.size) { it }
        fun root(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        fun union(a: Int, b: Int) {
            parent[root(a)] = root(b)
        }
        fun distance(a: Int, b: Int) = java.lang.Long.bitCount(sorted[a].dHash xor sorted[b].dHash)

        // Bursts: sliding time window over photos sorted by date.
        for (i in sorted.indices) {
            var j = i + 1
            while (j < sorted.size && sorted[j].photo.takenAt - sorted[i].photo.takenAt <= BURST_WINDOW_MS) {
                if (distance(i, j) <= BURST_DISTANCE) union(i, j)
                j++
            }
        }
        // Copies at any time: split the hash into bands; hashes within COPY_DISTANCE bits
        // share at least one identical band (pigeonhole), so only compare inside buckets.
        for (band in 0 until BANDS) {
            val shift = band * BAND_BITS
            val mask = (1L shl BAND_BITS) - 1
            sorted.indices.groupBy { (sorted[it].dHash ushr shift) and mask }.values
                .filter { it.size in 2..MAX_BUCKET }
                .forEach { bucket ->
                    for (a in bucket.indices) for (b in a + 1 until bucket.size) {
                        if (distance(bucket[a], bucket[b]) <= COPY_DISTANCE) union(bucket[a], bucket[b])
                    }
                }
        }
        return sorted.indices.groupBy(::root).values
            .filter { it.size > 1 }
            .map { members -> SimilarGroup(members.map { sorted[it] }.sortedBy { it.photo.takenAt }) }
            .sortedByDescending { it.extraBytes }
    }

    private companion object {
        const val THUMB = 256
        const val CHUNK = 64
        const val PARALLELISM = 4
        const val BLURRY_THRESHOLD = 40f
        const val BURST_DISTANCE = 12
        const val COPY_DISTANCE = 4
        const val BANDS = COPY_DISTANCE + 1
        const val BAND_BITS = 64 / BANDS

        /** Huge buckets are flat images (all-black, sky) — not useful matches. */
        const val MAX_BUCKET = 500
        val BURST_WINDOW_MS = TimeUnit.MINUTES.toMillis(5)
        val SCREENSHOT_DIRS = listOf("screenshots", "screen recordings", "знімки екрана", "скриншоты")
    }
}

/** Pure image math, kept separate so it can be unit tested on plain pixel arrays. */
object ImageMath {
    fun dHash(bitmap: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        val pixels = IntArray(72)
        small.getPixels(pixels, 0, 9, 0, 0, 9, 8)
        if (small !== bitmap) small.recycle()
        return dHash(pixels.map(::luma).toIntArray())
    }

    /** [gray] is a 9×8 row-major luma grid; bit set when a pixel is brighter than its right neighbour. */
    fun dHash(gray: IntArray): Long {
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (gray[y * 9 + x] > gray[y * 9 + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun sharpness(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return laplacianVariance(pixels.map(::luma).toIntArray(), w, h)
    }

    /** Variance of the 4-neighbour Laplacian: low for blurry images, high for sharp ones. */
    fun laplacianVariance(gray: IntArray, w: Int, h: Int): Float {
        if (w < 3 || h < 3) return 0f
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val lap = (gray[i - 1] + gray[i + 1] + gray[i - w] + gray[i + w] - 4 * gray[i]).toDouble()
                sum += lap
                sumSq += lap * lap
                n++
            }
        }
        val mean = sum / n
        return (sumSq / n - mean * mean).toFloat()
    }

    private fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
