package fr.mission.sophie.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mission.sophie.data.MediaRepo
import fr.mission.sophie.ui.Palette
import fr.mission.sophie.ui.PowerGem
import fr.mission.sophie.ui.SophieAvatar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Particle(val angle: Float, val speed: Float, val size: Float, val color: Color, val delay: Float)

/** Radiating sparkles around the center, [t] from 0 to 1 over the whole animation. */
@Composable
private fun Sparkles(t: Float, color: Color, count: Int = 70, modifier: Modifier = Modifier) {
    val particles = remember(color) {
        val r = Random(7)
        List(count) {
            Particle(r.nextFloat() * 2 * PI.toFloat(), 0.3f + r.nextFloat() * 0.7f, 3f + r.nextFloat() * 7f,
                if (r.nextBoolean()) color else Palette.cream, r.nextFloat() * 0.4f)
        }
    }
    Canvas(modifier) {
        val maxR = size.maxDimension * 0.6f
        particles.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val d = maxR * p.speed * local
            val a = (1f - local)
            drawCircle(p.color.copy(alpha = a), p.size * (1f - local * 0.5f), Offset(center.x + cos(p.angle) * d, center.y + sin(p.angle) * d))
        }
    }
}

/** Level-up: Sophie lights up, the power gem appears, halo and sparkles, "Pouvoir X/8". */
@Composable
fun LevelUpAnimation(powerId: String, color: Color, counter: String, title: String, media: MediaRepo, durationMs: Int) {
    val t = remember(powerId) { Animatable(0f) }
    LaunchedEffect(powerId) { t.animateTo(1f, tween(durationMs, easing = LinearEasing)) }
    val v = t.value
    val gemIn = ((v - 0.15f) / 0.25f).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val pulse = 0.5f + 0.5f * sin(v * 12f)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f * (0.6f + 0.4f * pulse)), Color.Transparent), center, size.minDimension * 0.7f))
            rotate(v * 120f) {
                for (i in 0 until 12) {
                    val a = i * PI.toFloat() / 6
                    val len = size.minDimension * (0.3f + 0.25f * gemIn)
                    drawLine(color.copy(alpha = 0.35f * gemIn), center, Offset(center.x + cos(a) * len, center.y + sin(a) * len), strokeWidth = 10f)
                }
            }
        }
        Sparkles(v, color, modifier = Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                SophieAvatar(media, Modifier.size(200.dp).alpha(0.6f + 0.4f * v), power = v)
                // The gem lands at the bottom right so it never hides Sophie's face.
                PowerGem(powerId, color, lit = true, modifier = Modifier.align(Alignment.BottomEnd).offset(24.dp, 12.dp).size(96.dp).scale(0.3f + 0.7f * gemIn).alpha(gemIn), glow = gemIn)
            }
            Text(title, color = color, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(gemIn))
            Text(counter, color = Palette.cream, fontSize = 30.sp, modifier = Modifier.alpha(((v - 0.4f) / 0.2f).coerceIn(0f, 1f)))
        }
    }
}

/** Corruption: the gem cracks, flickers and goes dark. */
@Composable
fun CorruptionAnimation(powerId: String, color: Color, durationMs: Int) {
    val t = remember(powerId) { Animatable(0f) }
    LaunchedEffect(powerId) { t.animateTo(1f, tween(durationMs, easing = LinearEasing)) }
    val v = t.value
    val flicker = if (v in 0.3f..0.75f) (if (((v * 40).toInt() % 2) == 0) 1f else 0.3f) else 1f
    val lit = v < 0.75f
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Palette.invisiblesDark.copy(alpha = 0.7f * v))
            val r = Random((v * 30).toInt())
            repeat(8) {
                val y = r.nextFloat() * size.height
                drawRect(Palette.invisibles.copy(alpha = 0.15f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 4f + r.nextFloat() * 20f))
            }
        }
        Box(contentAlignment = Alignment.Center) {
            PowerGem(powerId, color, lit = lit, modifier = Modifier.size(180.dp).alpha(flicker), glow = if (lit) 0.6f else 0f)
            Canvas(Modifier.size(180.dp)) {
                val crack = (v / 0.6f).coerceIn(0f, 1f)
                val pts = listOf(Offset(0.5f, 0.05f), Offset(0.42f, 0.3f), Offset(0.58f, 0.45f), Offset(0.4f, 0.62f), Offset(0.55f, 0.8f), Offset(0.48f, 0.97f))
                val n = (crack * (pts.size - 1)).toInt()
                for (i in 0 until n) {
                    drawLine(Color.Black, Offset(pts[i].x * size.width, pts[i].y * size.height), Offset(pts[i + 1].x * size.width, pts[i + 1].y * size.height), strokeWidth = 9f)
                }
                if (crack >= 1f) drawCircle(Palette.danger.copy(alpha = 0.6f), size.minDimension / 2, style = Stroke(8f))
            }
        }
    }
}

/** Victory: all gems circle around Sophie, confetti rain. */
@Composable
fun FinaleAnimation(powers: List<Pair<String, Color>>, media: MediaRepo, message: String) {
    val inf = rememberInfiniteTransition(label = "finale")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "rot")
    val fall by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "fall")
    val confetti = remember {
        val r = Random(3)
        val colors = powers.map { it.second } + Palette.cream
        List(120) { Triple(r.nextFloat(), r.nextFloat(), colors[r.nextInt(colors.size)]) }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Palette.sophie.copy(alpha = 0.5f), Color.Transparent), center, size.minDimension * 0.8f))
            confetti.forEach { (x, phase, c) ->
                val y = ((fall + phase) % 1f) * (size.height + 40f) - 20f
                val sway = sin((fall + phase) * 2 * PI.toFloat() * 2) * 20f
                rotate(phase * 360f + fall * 720f, Offset(x * size.width + sway, y)) {
                    drawRect(c, Offset(x * size.width + sway, y), androidx.compose.ui.geometry.Size(14f, 8f))
                }
            }
        }
        Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            SophieAvatar(media, Modifier.size(150.dp), power = 1f)
            powers.forEachIndexed { i, (id, color) ->
                val a = Math.toRadians((rot + i * 360.0 / powers.size))
                val x = (cos(a) * 115).toFloat()
                val y = (sin(a) * 115).toFloat()
                PowerGem(id, color, lit = true, glow = 0.8f, modifier = Modifier.padding(0.dp).offset(x.dp, y.dp).size(52.dp))
            }
        }
        Text(message, color = Palette.cream, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
}
