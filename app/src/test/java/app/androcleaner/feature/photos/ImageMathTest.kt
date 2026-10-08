package app.androcleaner.feature.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMathTest {

    private fun grid(w: Int, h: Int, f: (x: Int, y: Int) -> Int) = IntArray(w * h) { f(it % w, it / w) }

    @Test
    fun `dHash of a left-to-right gradient has no bits set`() {
        // Each pixel is darker than its right neighbour → no "brighter than right" bits.
        assertEquals(0L, ImageMath.dHash(grid(9, 8) { x, _ -> x * 10 }))
    }

    @Test
    fun `dHash of a right-to-left gradient has all bits set`() {
        assertEquals(-1L, ImageMath.dHash(grid(9, 8) { x, _ -> 255 - x * 10 }))
    }

    @Test
    fun `similar images have close hashes`() {
        val a = ImageMath.dHash(grid(9, 8) { x, y -> (x * 17 + y * 31) % 256 })
        val b = ImageMath.dHash(grid(9, 8) { x, y -> ((x * 17 + y * 31) % 256 + 3).coerceAtMost(255) })
        assertTrue(java.lang.Long.bitCount(a xor b) <= 4)
    }

    @Test
    fun `flat image has zero sharpness`() {
        assertEquals(0f, ImageMath.laplacianVariance(grid(32, 32) { _, _ -> 128 }, 32, 32), 0.001f)
    }

    @Test
    fun `checkerboard is much sharper than a smooth gradient`() {
        val sharp = ImageMath.laplacianVariance(grid(32, 32) { x, y -> if ((x + y) % 2 == 0) 0 else 255 }, 32, 32)
        val smooth = ImageMath.laplacianVariance(grid(32, 32) { x, _ -> x * 8 }, 32, 32)
        assertTrue(sharp > 1000f)
        assertTrue(smooth < 1f)
    }
}
