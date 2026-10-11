package fr.mission.sophie.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.mission.sophie.GameController
import fr.mission.sophie.engine.GameEvent
import fr.mission.sophie.engine.Phase
import fr.mission.sophie.engine.Speaker
import fr.mission.sophie.ui.Palette

/** Hidden game-master menu. Never shows a code or an answer (the screen may be mirrored). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MjMenu(c: GameController, onClose: () -> Unit, onExit: () -> Unit) {
    val state by c.game.collectAsState()
    val settings by c.settings.collectAsState()
    val s = state ?: return
    var confirmRestart by remember { mutableStateOf(false) }
    var free by remember { mutableStateOf("") }
    val step = c.engine.currentStep(s)
    val stepLabel = when {
        s.phase == Phase.INTRO -> "Introduction"
        s.phase == Phase.GATHERING -> "Rassemblement (${s.selected.size} pendentifs)"
        s.phase == Phase.BONUS_MENU -> "Épreuves bonus"
        s.bonusPower != null -> "Bonus : ${c.engine.currentPower(s)?.name}"
        step == "@trap" -> "Le piège"
        step == "@final" -> "Séquence finale"
        else -> c.engine.currentPower(s)?.let { "Épreuve ${c.engine.powersOf(s).indexOf(it) + 1} : ${it.name}" } ?: "-"
    }
    fun act(e: GameEvent) { c.dispatch(e); onClose() }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color(0xF20B1026)).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Menu du Maître du jeu", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.cream, modifier = Modifier.weight(1f))
                TextButton(onClose) { Text("Fermer ✕", fontSize = 20.sp) }
            }
            Text("$stepLabel · ${phaseLabel(s.phase)} · pouvoirs ${s.acquired.size}/${c.engine.powersOf(s).size}" +
                (if (s.jokerAllowed) " · joker actif" else "") + (if (s.idlePaused) " · attente en pause" else ""),
                color = Color.LightGray, fontSize = 16.sp)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MjButton("Passer l'étape (succès)") { act(GameEvent.MjForceSuccess) }
                MjButton("Forcer le code / le cri", enabled = s.phase in setOf(Phase.CODE_ENTRY, Phase.WAITING, Phase.SHOUT)) { act(GameEvent.MjForceCode) }
                MjButton("Joker : autre pendentif", enabled = s.phase in setOf(Phase.CALL, Phase.WAITING, Phase.CODE_ENTRY)) { act(GameEvent.MjAllowJoker) }
                MjButton("Envoyer l'indice") { act(GameEvent.MjSendHint) }
                MjButton("Message de Sophie") { act(GameEvent.MjSay(Speaker.SOPHIE)) }
                MjButton("Moquerie des Invisibles", color = Palette.invisibles) { act(GameEvent.MjSay(Speaker.INVISIBLES)) }
                MjButton(if (s.idlePaused) "Relancer les messages d'attente" else "Pause des messages d'attente") {
                    act(GameEvent.MjPauseIdle(!s.idlePaused))
                }
                MjButton("Valider / passer le piège", enabled = s.phase in setOf(Phase.TRAP_WAIT, Phase.CORRUPTION, Phase.ECHO_INTRO, Phase.ECHO_PAUSE, Phase.ECHO_PLAYBACK, Phase.ECHO_INPUT)) {
                    act(GameEvent.MjValidateRepair)
                }
                MjButton("Étape précédente", color = Palette.cream) { act(GameEvent.MjBack) }
                MjButton("Redémarrer la partie", color = Palette.danger) { confirmRestart = true }
                if (s.phase == Phase.FINALE || s.phase == Phase.BONUS_MENU) MjButton("Épreuves bonus", color = Palette.cream) { act(GameEvent.OpenBonus) }
                MjButton("Quitter vers l'accueil", color = Color.Gray) { onClose(); onExit() }
            }

            Text("Message libre", fontSize = 18.sp, color = Palette.cream)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(free, { free = it }, Modifier.weight(1f), label = { Text("Texte à afficher") })
                Spacer(Modifier.width(8.dp))
                MjButton("Sophie", enabled = free.isNotBlank()) { act(GameEvent.MjSay(Speaker.SOPHIE, free.trim())) }
                Spacer(Modifier.width(8.dp))
                MjButton("Invisibles", color = Palette.invisibles, enabled = free.isNotBlank()) { act(GameEvent.MjSay(Speaker.INVISIBLES, free.trim())) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Volumes", fontSize = 18.sp, color = Palette.cream, modifier = Modifier.weight(1f))
                Text("Muet", fontSize = 16.sp)
                androidx.compose.material3.Switch(settings.muted, { v -> c.updateSettings { it.copy(muted = v) } })
            }
            VolumeRow("Voix", settings.voiceVolume) { v -> c.updateSettings { it.copy(voiceVolume = v) } }
            VolumeRow("Musique", settings.musicVolume) { v -> c.updateSettings { it.copy(musicVolume = v) } }
            VolumeRow("Effets", settings.sfxVolume) { v -> c.updateSettings { it.copy(sfxVolume = v) } }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Redémarrer la partie ?") },
            text = { Text("Toute la progression sera perdue.") },
            confirmButton = { TextButton({ confirmRestart = false; c.newGame(); onClose() }) { Text("Redémarrer") } },
            dismissButton = { TextButton({ confirmRestart = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun MjButton(label: String, color: Color = Palette.sophie, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Palette.night)) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun VolumeRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.width(100.dp), fontSize = 16.sp)
        Slider(value, onChange, Modifier.weight(1f))
        Text("${(value * 100).toInt()} %", Modifier.width(60.dp), fontSize = 16.sp)
    }
}

fun phaseLabel(p: Phase): String = when (p) {
    Phase.INTRO -> "intro"
    Phase.GATHERING -> "rassemblement"
    Phase.SHOUT -> "cri-mètre"
    Phase.BONUS_MENU -> "menu des bonus"
    Phase.CALL -> "appel"
    Phase.WAITING -> "épreuve en cours"
    Phase.CODE_ENTRY -> "saisie du code"
    Phase.LEVEL_UP -> "level-up"
    Phase.REWARD -> "récompense"
    Phase.TRAP_WAIT -> "attente du faux badge"
    Phase.CORRUPTION -> "corruption"
    Phase.ECHO_INTRO -> "écho : explication"
    Phase.ECHO_PAUSE, Phase.ECHO_PLAYBACK -> "écho : lecture"
    Phase.ECHO_INPUT -> "écho : reproduction"
    Phase.RESTORED -> "pouvoir restauré"
    Phase.FINAL_INPUT -> "pose des pendentifs"
    Phase.FINAL_LOCKOUT -> "blocage"
    Phase.HEART -> "attente du Cœur"
    Phase.FINALE -> "finale"
}
