package fr.mission.sophie.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import fr.mission.sophie.data.MediaRepo
import fr.mission.sophie.engine.Speaker
import fr.mission.sophie.ui.Symbols.drawSymbol

object Palette {
    val night = Color(0xFF0B1026)
    val deep = Color(0xFF1F2A5A)
    val cream = Color(0xFFF0E2B6)
    val sophie = Color(0xFF6FA8FF)
    val invisibles = Color(0xFFB07CFF)
    val invisiblesDark = Color(0xFF2A1240)
    val danger = Color(0xFFFF6B6B)
    val dim = Color(0xFF3A4270)
    val background = Brush.verticalGradient(listOf(Color(0xFF070B1C), Color(0xFF141C44), Color(0xFF1F1446)))
}

val SophieColors: ColorScheme = darkColorScheme(
    primary = Palette.sophie,
    onPrimary = Palette.night,
    secondary = Palette.cream,
    background = Palette.night,
    surface = Color(0xFF151B3D),
    onSurface = Color.White,
    onBackground = Color.White,
    error = Palette.danger,
)

@Composable
fun SophieTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = SophieColors) {
    // Screens are drawn on a custom gradient, not on a Surface: default text color must be light.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White, content = content)
}

fun parseColor(hex: String, fallback: Color = Palette.cream): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    fallback
}

/** Text that shrinks until it fits its box (min [minSize]). */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    maxSize: TextUnit = 30.sp,
    minSize: TextUnit = 18.sp,
    color: Color = Color.White,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Center,
) {
    var size by remember(text, maxSize) { mutableStateOf(maxSize) }
    var ready by remember(text, maxSize) { mutableStateOf(false) }
    Text(
        text = text,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        style = TextStyle(fontSize = size, color = color, fontWeight = weight, textAlign = align, lineHeight = size * 1.2f),
        onTextLayout = { r ->
            if ((r.hasVisualOverflow || r.didOverflowHeight) && size > minSize) size = (size.value * 0.92f).sp
            else ready = true
        },
    )
}

/** A power gem: colored disc with the pendant symbol. Grey when not acquired. */
@Composable
fun PowerGem(id: String, color: Color, lit: Boolean, modifier: Modifier = Modifier, glow: Float = 0f) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        if (glow > 0f) drawCircle(color.copy(alpha = 0.35f * glow), radius = r * (1f + 0.35f * glow))
        drawCircle(if (lit) color else Palette.dim, radius = r)
        drawCircle(Color.White.copy(alpha = if (lit) 0.35f else 0.1f), radius = r, style = Stroke(width = r * 0.08f))
        drawSymbol(id, if (lit) Palette.night else Color(0xFF5A6290), 0.62f)
    }
}

/** Message bubble: avatar + text, color depends on the speaker. [compact] is used over busy screens. */
@Composable
fun MessageBubble(speaker: Speaker, text: String, media: MediaRepo, modifier: Modifier = Modifier, compact: Boolean = false, showAvatar: Boolean = true) {
    val sophie = speaker == Speaker.SOPHIE
    val bg = if (sophie) Color(0xF5112A5C) else Color(0xF52A1240)
    val accent = if (sophie) Palette.sophie else Palette.invisibles
    val avatar = if (compact) 56.dp else 84.dp
    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(3.dp, accent, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = if (compact) 4.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAvatar) {
            if (sophie) SophieAvatar(media, Modifier.size(avatar), speaking = true) else InvisiblesAvatar(media, Modifier.size(avatar), mocking = true)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            if (!compact) Text(if (sophie) "Sophie" else "Les Invisibles", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (compact) {
                AutoSizeText(text, Modifier.fillMaxWidth().height(64.dp), maxSize = 26.sp, minSize = 16.sp, align = TextAlign.Start)
            } else {
                AutoSizeText(text, maxSize = 28.sp, minSize = 18.sp, align = TextAlign.Start, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun BigButton(label: String, modifier: Modifier = Modifier, color: Color = Palette.sophie, height: Dp = 72.dp, enabled: Boolean = true, textSize: TextUnit = 26.sp, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Palette.night),
    ) { Text(label, fontSize = textSize, fontWeight = FontWeight.Bold, maxLines = 1) }
}

/** PIN dialog. The PIN is never displayed. */
@Composable
fun PinDialog(expected: String, title: String = "Code PIN", onDismiss: () -> Unit, onOk: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val submit = { if (pin == expected) onOk() else { error = true; pin = "" } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8); error = false },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
                    isError = error,
                    label = { Text("PIN") },
                )
                if (error) Text("Code incorrect", color = Palette.danger)
            }
        },
        confirmButton = { TextButton({ submit() }) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.cream)
        content()
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) = Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
