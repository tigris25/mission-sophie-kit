package fr.mission.sophie.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mission.sophie.GameController
import fr.mission.sophie.engine.Phase
import fr.mission.sophie.engine.Role
import fr.mission.sophie.nfc.NfcStatus
import fr.mission.sophie.ui.admin.AdminScreen
import fr.mission.sophie.ui.game.GameScreen

enum class Screen { HOME, ADMIN, GAME }

@Composable
fun AppRoot(c: GameController) {
    SophieTheme {
        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
        val nfc by c.nfcStatus.collectAsState()
        var nfcDismissed by rememberSaveable { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(Palette.background)) {
            when (screen) {
                Screen.HOME -> HomeScreen(c, onAdmin = { screen = Screen.ADMIN }, onGame = { screen = Screen.GAME })
                Screen.ADMIN -> AdminScreen(c, onBack = { screen = Screen.HOME })
                Screen.GAME -> {
                    val g by c.game.collectAsState()
                    if (g != null) GameScreen(c, onExit = { screen = Screen.HOME })
                    else androidx.compose.runtime.LaunchedEffect(Unit) { screen = Screen.HOME }
                }
            }
        }
        if (nfc == NfcStatus.DISABLED && !nfcDismissed) NfcDisabledDialog(onDismiss = { nfcDismissed = true })
    }
}

@Composable
private fun NfcDisabledDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Le NFC est désactivé", fontSize = 26.sp) },
        text = { Text("Les pendentifs ne peuvent pas être lus. Activez le NFC dans les réglages du téléphone, puis revenez dans l'application.", fontSize = 20.sp) },
        confirmButton = { TextButton({ ctx.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) { Text("Ouvrir les réglages NFC", fontSize = 18.sp) } },
        dismissButton = { TextButton(onDismiss) { Text("Continuer sans NFC") } },
    )
}

@Composable
private fun HomeScreen(c: GameController, onAdmin: () -> Unit, onGame: () -> Unit) {
    val settings by c.settings.collectAsState()
    val bindings by c.bindings.collectAsState()
    val content by c.content.collectAsState()
    val nfc by c.nfcStatus.collectAsState()
    var askPin by remember { mutableStateOf(false) }
    var resumeChoice by remember { mutableStateOf(false) }
    val roles = Role.all(content.raw)
    val pendants = roles.count { it is Role.PowerRole && bindings.uidFor(it.key) != null }
    val badges = listOf(Role.FakeBadge, Role.HeartBadge).count { bindings.uidFor(it.key) != null }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SophieAvatar(c.media, Modifier.size(96.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(content.raw.ui.homeTitle, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Palette.cream)
                Text(content.raw.ui.homeSubtitle, fontSize = 22.sp, color = Palette.sophie)
            }
        }
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            BigButton("Admin", Modifier.width(260.dp), color = Palette.cream, height = 96.dp) { askPin = true }
            BigButton("Jeu", Modifier.width(260.dp), height = 96.dp) {
                val saved = c.savedGame()
                if (c.game.value != null) onGame()
                else if (saved != null && saved.phase != Phase.INTRO) resumeChoice = true
                else { c.newGame(); onGame() }
            }
        }
        Spacer(Modifier.height(24.dp))
        val nfcText = when (nfc) {
            NfcStatus.OK -> "NFC prêt"
            NfcStatus.DISABLED -> "NFC désactivé"
            NfcStatus.ABSENT -> "Pas de NFC sur cet appareil" + if (settings.simulator) " (simulateur actif)" else ""
        }
        val weather = content.variants.firstOrNull { it.id == content.variant }?.label?.let { " · $it" } ?: ""
        if (content.raw.ui.homeSubtitle.contains('[')) Text("Prénom à régler : Admin › Réglages › Personnaliser", fontSize = 18.sp, color = Color(0xFFFFC56B))
        Text("$nfcText · $pendants pendentif${if (pendants > 1) "s" else ""} lié${if (pendants > 1) "s" else ""} · badges $badges/2$weather · Contenu v${content.raw.version}${if (content.imported) " (importé)" else ""}",
            fontSize = 18.sp, color = if (pendants > 0 && badges == 2 && nfc == NfcStatus.OK) Color(0xFF8BE39A) else Color(0xFFFFC56B))
    }

    if (askPin) PinDialog(settings.pin, "Accès Admin", onDismiss = { askPin = false }) { askPin = false; onAdmin() }
    if (resumeChoice) {
        AlertDialog(
            onDismissRequest = { resumeChoice = false },
            title = { Text("Une partie est en cours", fontSize = 24.sp) },
            text = { Text("Reprendre là où elle s'était arrêtée, ou commencer une nouvelle partie ?", fontSize = 20.sp) },
            confirmButton = { TextButton({ resumeChoice = false; c.resumeGame(); onGame() }) { Text("Reprendre la partie", fontSize = 18.sp) } },
            dismissButton = { TextButton({ resumeChoice = false; c.newGame(); onGame() }) { Text("Nouvelle partie", fontSize = 18.sp) } },
        )
    }
}
