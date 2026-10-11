package fr.mission.sophie.ui

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import fr.mission.sophie.data.MediaRepo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Milliseconds since the composable appeared, updated every frame. */
@Composable
fun rememberTimeMs(): Long {
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) t = withFrameMillis { it } - start
    }
    return t
}

/** Deterministic pseudo-random in [0,1) for an integer seed. */
private fun hash(n: Int): Float {
    var x = n * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0x7fffffff) / 2147483647f
}

/**
 * A replaceable picture: animated GIF/WebP when provided (Android 9+), static image otherwise.
 * Returns false (and draws nothing) when there is no file, so the caller can draw its fallback.
 */
@Composable
private fun MediaPicture(
    media: MediaRepo, id: String, modifier: Modifier, crop: Boolean, tint: ColorFilter? = null,
    alignment: Alignment = Alignment.Center,
): Boolean {
    val animated: Drawable? = remember(id) { media.animatedImage(id) }
    if (animated != null) {
        DisposableEffect(animated) {
            (animated as? Animatable)?.start()
            onDispose { (animated as? Animatable)?.stop() }
        }
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(animated)
                }
            },
            modifier = modifier,
        )
        return true
    }
    val bmp: ImageBitmap? = remember(id) { media.image(id)?.asImageBitmap() }
    if (bmp != null) {
        Image(bmp, null, modifier, alignment = alignment, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit, colorFilter = tint)
        return true
    }
    return false
}

/**
 * Sophie: the "sophie" picture (or a drawn star) with a breathing halo, a gentle float and orbiting sparkles.
 * [power] 0..1 dims her when her powers are broken; [speaking] makes the halo pulse faster.
 */
@Composable
fun SophieAvatar(media: MediaRepo, modifier: Modifier = Modifier, power: Float = 1f, speaking: Boolean = false) {
    val t = rememberTimeMs() / 1000f
    val breath = 0.5f + 0.5f * sin(t * 2 * PI.toFloat() / (if (speaking) 0.7f else 2.6f))
    val float = sin(t * 2 * PI.toFloat() / 3.2f)
    val hasPicture = remember { media.hasImage("sophie") }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val glow = (0.25f + 0.45f * breath) * (0.35f + 0.65f * power)
            drawCircle(Brush.radialGradient(listOf(Palette.sophie.copy(alpha = glow), Color.Transparent), center, r), r)
            // Three sparkles orbiting.
            repeat(3) { i ->
                val a = t * 1.3f + i * 2.094f
                val d = r * 0.86f
                drawCircle(Palette.cream.copy(alpha = 0.35f + 0.5f * power * breath), r * 0.045f,
                    Offset(center.x + cos(a) * d, center.y + sin(a) * d * 0.9f))
            }
        }
        val inner = Modifier.fillMaxSize(0.8f).graphicsLayer {
            translationY = float * size.height * 0.025f
            val s = 1f + 0.03f * breath * (if (speaking) 1.5f else 1f)
            scaleX = s; scaleY = s
            alpha = 0.55f + 0.45f * power
        }
        if (hasPicture) {
            // Portraits: keep the face (upper part of the picture) inside the circle.
            MediaPicture(media, "sophie", inner.clip(CircleShape), crop = true, alignment = androidx.compose.ui.BiasAlignment(0f, -0.45f))
        } else {
            Canvas(inner) {
                val s = size.minDimension / 2 * (0.75f + 0.25f * breath) * (0.6f + 0.4f * power)
                val path = Path().apply {
                    moveTo(center.x, center.y - s); lineTo(center.x + s * 0.22f, center.y - s * 0.22f)
                    lineTo(center.x + s, center.y); lineTo(center.x + s * 0.22f, center.y + s * 0.22f)
                    lineTo(center.x, center.y + s); lineTo(center.x - s * 0.22f, center.y + s * 0.22f)
                    lineTo(center.x - s, center.y); lineTo(center.x - s * 0.22f, center.y - s * 0.22f); close()
                }
                drawPath(path, Palette.cream.copy(alpha = 0.5f + 0.5f * power))
                drawCircle(Color.White, radius = s * 0.18f)
            }
        }
    }
}

/**
 * The Invisibles: the "invisibles" picture (their symbol) or a drawn hooded shadow, surrounded by drifting
 * purple smoke, with periodic glitches (jitter, color split, flicker). [mocking] makes them shake and glitch more.
 */
@Composable
fun InvisiblesAvatar(media: MediaRepo, modifier: Modifier = Modifier, mocking: Boolean = false) {
    val ms = rememberTimeMs()
    val t = ms / 1000f
    // Glitch bursts: 180 ms windows, every ~0.6 s when mocking, ~2.5 s otherwise.
    val period = if (mocking) 600 else 2500
    val slot = (ms / period).toInt()
    val inBurst = (ms % period) < 180 && hash(slot) < (if (mocking) 0.8f else 0.6f)
    val jitterSeed = (ms / 45).toInt()
    val jx = if (inBurst) (hash(jitterSeed) - 0.5f) * 0.12f else 0f
    val jy = if (inBurst) (hash(jitterSeed + 7) - 0.5f) * 0.05f else 0f
    val shake = if (mocking) sin(t * 2 * PI.toFloat() * 5f) * 4f else 0f
    val hasPicture = remember { media.hasImage("invisibles") }

    Box(modifier, contentAlignment = Alignment.Center) {
        // Purple smoke rising behind.
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(Palette.invisibles.copy(alpha = 0.35f), Color.Transparent), center, r), r)
            repeat(7) { i ->
                val speed = 0.12f + hash(i) * 0.1f
                val life = (t * speed + hash(i + 20)) % 1f
                val x = center.x + (hash(i + 40) - 0.5f) * r * 1.2f + sin(t * 1.5f + i) * r * 0.08f
                val y = center.y + r * 0.7f - life * r * 1.6f
                drawCircle(Palette.invisibles.copy(alpha = 0.22f * (1f - life)), r * (0.18f + 0.25f * life), Offset(x, y))
            }
        }
        val layer = Modifier.fillMaxSize(0.8f).graphicsLayer {
            translationX = jx * size.width
            translationY = jy * size.height
            rotationZ = shake
            alpha = if (inBurst && hash(jitterSeed + 3) < 0.4f) 0.45f else 1f
        }
        if (hasPicture) {
            Box(layer, contentAlignment = Alignment.Center) {
                if (inBurst) {
                    // Color-split ghosts (static pictures only; animated ones just jitter).
                    MediaPicture(media, "invisibles", Modifier.fillMaxSize().graphicsLayer { translationX = -size.width * 0.04f; alpha = 0.6f },
                        crop = false, tint = ColorFilter.tint(Color(0xFFFF3B6B), androidx.compose.ui.graphics.BlendMode.SrcIn))
                    MediaPicture(media, "invisibles", Modifier.fillMaxSize().graphicsLayer { translationX = size.width * 0.04f; alpha = 0.6f },
                        crop = false, tint = ColorFilter.tint(Color(0xFF3BE8FF), androidx.compose.ui.graphics.BlendMode.SrcIn))
                }
                MediaPicture(media, "invisibles", Modifier.fillMaxSize(), crop = false)
            }
        } else {
            Canvas(layer) {
                // Blink now and then; when mocking, the eye looks from side to side.
                val blink = if (hash((t * 0.5f).toInt()) < 0.35f && (t % 2f) < 0.14f) 0.12f else 1f
                val look = if (mocking) sin(t * 2 * PI.toFloat() * 0.8f) * 0.1f else 0f
                drawInvisiblesLogo(center, size.minDimension / 2, eyeOpen = blink, look = look)
            }
        }
    }
}

/** Light and dark tones of the Invisibles' eye symbol on the game's dark background. */
val InvisiblesLight = Color(0xFFDCC8FF)
val InvisiblesDark = Color(0xFF14081F)

/**
 * The Invisibles' symbol: an eye inside a disc, inside a ring. Drawn with vectors so it scales and animates.
 * [eyeOpen] 0..1 closes the eye vertically, [look] moves the iris sideways (fraction of the radius).
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInvisiblesLogo(
    c: Offset,
    r: Float,
    eyeOpen: Float = 1f,
    look: Float = 0f,
    light: Color = InvisiblesLight,
    dark: Color = InvisiblesDark,
) {
    // Ring: thin dark edge, light band, then the dark disc.
    drawCircle(dark, r, c)
    drawCircle(light, r * 0.965f, c)
    drawCircle(dark, r * 0.82f, c)

    fun almond(w: Float, h: Float): Path = Path().apply {
        val k = h * eyeOpen / 0.75f // cubic control height so that the peak reaches h
        moveTo(c.x - w, c.y)
        cubicTo(c.x - w * 0.45f, c.y - k, c.x + w * 0.45f, c.y - k, c.x + w, c.y)
        cubicTo(c.x + w * 0.45f, c.y + k, c.x - w * 0.45f, c.y + k, c.x - w, c.y)
        close()
    }
    val outer = almond(r * 0.8f, r * 0.48f)
    val inner = almond(r * 0.64f, r * 0.27f)
    val ix = c.x + look * r
    // Light almond, dark corners (inner almond minus the space around the iris), dark iris ring, light pupil.
    drawPath(outer, light)
    val around = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(ix, c.y), r * 0.425f)) }
    drawPath(Path().apply { op(inner, around, androidx.compose.ui.graphics.PathOperation.Difference) }, dark)
    clipPath(outer) {
        drawCircle(dark, r * 0.325f, Offset(ix, c.y))
        drawCircle(light, r * 0.21f, Offset(ix, c.y))
    }
}

/** Static logo (lists, admin). */
@Composable
fun InvisiblesLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) { drawInvisiblesLogo(center, size.minDimension / 2) }
}
