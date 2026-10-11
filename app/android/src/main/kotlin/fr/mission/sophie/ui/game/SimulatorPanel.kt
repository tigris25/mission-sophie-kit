package fr.mission.sophie.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mission.sophie.GameController
import fr.mission.sophie.engine.Role
import fr.mission.sophie.ui.Palette
import fr.mission.sophie.ui.parseColor

/** Scan simulator: one button per role, injected as if a real tag had been read. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulatorPanel(c: GameController, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val content by c.content.collectAsState()
    Column(modifier.padding(8.dp), horizontalAlignment = Alignment.End) {
        Button(
            { open = !open },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x80FFFFFF), contentColor = Palette.night),
        ) { Text(if (open) "SIM ✕" else "SIM", fontSize = 14.sp) }
        if (open) {
            FlowRow(
                Modifier.widthIn(max = 720.dp).background(Color(0xE0101530), RoundedCornerShape(16.dp)).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                content.raw.mainPowers.forEach { p ->
                    SimButton(p.name, parseColor(p.color)) { c.simulate(Role.PowerRole(p.id).key) }
                }
                SimButton("Faux badge", Palette.invisibles) { c.simulate(Role.FakeBadge.key) }
                SimButton("Cœur", Color(0xFFFF6FA0)) { c.simulate(Role.HeartBadge.key) }
                SimButton("Inconnu", Color.Gray) { c.simulate(null) }
            }
        }
    }
}

@Composable
private fun SimButton(label: String, color: Color, onClick: () -> Unit) {
    Button(onClick, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Palette.night),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        modifier = Modifier.height(34.dp)) {
        Text(label, fontSize = 13.sp)
    }
}
