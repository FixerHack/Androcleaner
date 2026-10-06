package app.androcleaner.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val BrandViolet = Color(0xFF7C5CFF)
val BrandTeal = Color(0xFF00C9B7)
val BrandPink = Color(0xFFFF5CA8)

val BrandGradient = Brush.linearGradient(listOf(BrandViolet, BrandTeal))

/** Colors used for storage categories in charts and legends. */
object CategoryColors {
    val Images = Color(0xFF7C5CFF)
    val Videos = Color(0xFFFF5CA8)
    val Audio = Color(0xFFFFB547)
    val Documents = Color(0xFF3FA9F5)
    val Apks = Color(0xFF00C9B7)
    val Archives = Color(0xFFB07CFF)
    val Other = Color(0xFF9CC25A)

    /** Space used by the system and apps (not visible to the file scan). */
    val System = Color(0xFF6E6A85)
}

internal val LightBackground = Color(0xFFF7F6FB)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightSurfaceVariant = Color(0xFFEDEAF7)
internal val LightOnSurface = Color(0xFF1A1726)
internal val LightOnSurfaceVariant = Color(0xFF5E5A70)

internal val DarkBackground = Color(0xFF0E0B1A)
internal val DarkSurface = Color(0xFF17132A)
internal val DarkSurfaceVariant = Color(0xFF231E3A)
internal val DarkOnSurface = Color(0xFFEDEAF7)
internal val DarkOnSurfaceVariant = Color(0xFFA9A4C0)
