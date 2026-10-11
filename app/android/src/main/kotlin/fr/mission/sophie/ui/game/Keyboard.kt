package fr.mission.sophie.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mission.sophie.ui.Palette

private val ROWS = listOf("AZERTYUIOP", "QSDFGHJKLM", "WXCVBN")

/** Built-in AZERTY keyboard with big keys (the system keyboard is never used in game). */
@Composable
fun AzertyKeyboard(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onValidate: () -> Unit,
    validateLabel: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ROWS.forEachIndexed { i, row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { ch -> Key(ch.toString(), enabled = enabled) { onKey(ch) } }
                if (i == 2) {
                    Key("⌫", weight = 1.5f, color = Palette.dim, enabled = enabled) { onBackspace() }
                    Key(validateLabel, weight = 2.5f, color = Palette.sophie, textColor = Palette.night, enabled = enabled, textSize = 24) { onValidate() }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Key(
    label: String,
    weight: Float = 1f,
    color: Color = Color(0xFF2B3470),
    textColor: Color = Color.White,
    enabled: Boolean,
    textSize: Int = 30,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) color else color.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, fontSize = textSize.sp, fontWeight = FontWeight.Bold)
    }
}
