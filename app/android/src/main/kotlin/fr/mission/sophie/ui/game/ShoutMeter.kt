package fr.mission.sophie.ui.game

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import fr.mission.sophie.ui.BigButton
import fr.mission.sophie.ui.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt

/** Microphone loudness mapped to 0..1 (about -50 dBFS → 0, -8 dBFS → 1), smoothed. Needs RECORD_AUDIO. */
object MicLevel {
    private const val RATE = 22050
    private const val FLOOR_DB = -50.0
    private const val TOP_DB = -8.0

    /** Reads the microphone until the coroutine is cancelled, calling [onLevel] ~20 times per second. */
    @SuppressLint("MissingPermission")
    suspend fun listen(onLevel: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 4096))
        } catch (e: Exception) {
            return@withContext
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@withContext }
        val buf = ShortArray(1024)
        var smooth = 0f
        try {
            rec.startRecording()
            while (isActive) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
                val rms = sqrt(sum / n).coerceAtLeast(1.0)
                val db = 20 * log10(rms / 32768.0)
                val level = ((db - FLOOR_DB) / (TOP_DB - FLOOR_DB)).toFloat().coerceIn(0f, 1f)
                // Fast attack, slower release, so the gauge feels alive but readable.
                smooth = if (level > smooth) smooth + (level - smooth) * 0.6f else smooth + (level - smooth) * 0.15f
                onLevel(smooth)
            }
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
        }
    }
}

fun hasMicPermission(ctx: android.content.Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * The "cri-mètre": a big gauge driven by the microphone. When the level stays above [threshold] for
 * [holdSec] seconds, [onDone] is called once. Without microphone permission it explains that the
 * game master validates instead (and offers to grant it).
 */
@Composable
fun ShoutMeter(
    threshold: Float,
    holdSec: Float,
    label: String,
    micMissingText: String,
    color: Color,
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasMicPermission(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var level by remember { mutableFloatStateOf(0f) }
    var held by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }

    if (granted) {
        LaunchedEffect(Unit) {
            var last = System.nanoTime()
            MicLevel.listen { l ->
                level = l
                val now = System.nanoTime()
                val dt = (now - last) / 1e9f
                last = now
                // The charge fills while loud enough and drains twice as fast when the shout stops.
                held = if (l >= threshold) held + dt else (held - dt * 2).coerceAtLeast(0f)
                if (!done && held >= holdSec) { done = true }
            }
        }
        LaunchedEffect(done) {
            if (done) { delay(150); onDone() }
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center)
        if (!granted) {
            Text(micMissingText, fontSize = 22.sp, color = Palette.cream, textAlign = TextAlign.Center)
            BigButton("Autoriser le micro", height = 60.dp, textSize = 20.sp) { ask.launch(Manifest.permission.RECORD_AUDIO) }
            return@Column
        }
        Box(Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 24.dp)) {
            Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(Color(0xFF1B2250), cornerRadius = r)
                val w = size.width * level
                if (w > 0f) drawRoundRect(
                    Brush.horizontalGradient(listOf(Color(0xFF6EE07A), Color(0xFFFFE45C), Color(0xFFFF5252)), 0f, size.width),
                    size = Size(w, size.height), cornerRadius = r,
                )
                val x = size.width * threshold
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 6f)
                drawRoundRect(Color.White.copy(alpha = 0.5f), cornerRadius = r, style = Stroke(4f))
            }
        }
        // Hold progress: crystals that fill while the shout is held.
        val filled = ((held / holdSec) * 5).toInt().coerceIn(0, 5)
        Text("✦".repeat(filled) + "✧".repeat(5 - filled), fontSize = 36.sp, color = color)
        Text(
            when {
                done -> "Bravo !"
                level >= threshold -> "Tenez le cri !"
                level > threshold * 0.6f -> "Plus fort !"
                else -> "Criez !"
            },
            fontSize = 30.sp, color = Palette.cream, fontWeight = FontWeight.Bold,
        )
    }
}
