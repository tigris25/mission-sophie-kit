package fr.mission.sophie.ui.admin

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.mission.sophie.GameController
import fr.mission.sophie.data.Stores
import fr.mission.sophie.engine.ContentException
import fr.mission.sophie.engine.GameContent
import fr.mission.sophie.engine.Lines
import fr.mission.sophie.engine.Message
import fr.mission.sophie.engine.Priority
import fr.mission.sophie.engine.Role
import fr.mission.sophie.engine.Sfx
import fr.mission.sophie.engine.Speaker
import fr.mission.sophie.ui.MessageBubble
import fr.mission.sophie.ui.Palette
import fr.mission.sophie.ui.PowerGem
import fr.mission.sophie.ui.Section
import fr.mission.sophie.ui.game.CorruptionAnimation
import fr.mission.sophie.ui.game.FinaleAnimation
import fr.mission.sophie.ui.game.LevelUpAnimation
import fr.mission.sophie.ui.game.VolumeRow
import fr.mission.sophie.ui.parseColor
import kotlinx.coroutines.delay
import kotlin.random.Random

private enum class Tab(val label: String) {
    TAGS("Liaison des tags"), TEST("Test de lecture"), PREVIEW("Prévisualisation"), SETTINGS("Réglages"),
    CONTENT("Contenu et voix"), BACKUP("Sauvegarde"), LOG("Journal"), GAME("Partie"),
}

fun roleLabel(content: GameContent, key: String?): String {
    val r = key?.let { Role.parse(it) } ?: return "inconnu"
    return when (r) {
        is Role.PowerRole -> content.power(r.powerId)?.let { "${it.name} (${it.talent})" } ?: "pouvoir ${r.powerId} (absent du contenu)"
        Role.FakeBadge -> "Faux badge des Invisibles (piège)"
        Role.HeartBadge -> "Badge final : le Cœur de Sophie"
    }
}

fun shortUid(uid: String) = "…" + uid.takeLast(4)

@Composable
fun AdminScreen(c: GameController, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.TAGS) }
    BackHandler { onBack() }
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(230.dp).fillMaxHeight().background(Color(0xFF0E1433)).padding(8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Admin", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.cream, modifier = Modifier.padding(8.dp))
            Tab.entries.forEach { t ->
                val sel = t == tab
                Text(
                    t.label,
                    fontSize = 18.sp,
                    color = if (sel) Palette.night else Color.White,
                    modifier = Modifier.fillMaxWidth()
                        .background(if (sel) Palette.sophie else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { tab = t }.padding(12.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            OutlinedButton(onBack, Modifier.fillMaxWidth()) { Text("← Accueil") }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            when (tab) {
                Tab.TAGS -> TagsTab(c)
                Tab.TEST -> TestTab(c)
                Tab.PREVIEW -> PreviewTab(c)
                Tab.SETTINGS -> SettingsTab(c)
                Tab.CONTENT -> ContentTab(c)
                Tab.BACKUP -> BackupTab(c)
                Tab.LOG -> LogTab(c)
                Tab.GAME -> GameTab(c)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- tags

@Composable
private fun TagsTab(c: GameController) {
    val bindings by c.bindings.collectAsState()
    val content by c.content.collectAsState()
    val settings by c.settings.collectAsState()
    val roles = Role.all(content.raw)
    val ready = roles.count { bindings.uidFor(it.key) != null }
    var capturing by remember { mutableStateOf<String?>(null) }
    var conflict by remember { mutableStateOf<Pair<String, String>?>(null) } // uid to roleKey

    DisposableEffect(Unit) { onDispose { c.captureNextScan = null } }

    fun onUid(uid: String, roleKey: String) {
        capturing = null
        val other = bindings.roleOf(uid)
        if (other != null && other != roleKey) conflict = uid to roleKey else c.bind(uid, roleKey)
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Liaison des tags", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("$ready/${roles.size} prêts", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = if (ready == roles.size) Color(0xFF8BE39A) else Color(0xFFFFC56B))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(roles) { role ->
                val uid = bindings.uidFor(role.key)
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF151B3D), RoundedCornerShape(12.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (role) {
                        is Role.PowerRole -> PowerGem(role.powerId, parseColor(content.raw.power(role.powerId)?.color ?: "#fff"), lit = uid != null, modifier = Modifier.size(40.dp))
                        Role.FakeBadge -> fr.mission.sophie.ui.InvisiblesLogo(Modifier.size(40.dp))
                        else -> Text("♥", fontSize = 28.sp, modifier = Modifier.width(40.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(roleLabel(content.raw, role.key), fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Text(if (uid != null) "✓ ${shortUid(uid)}" else "non lié", fontSize = 18.sp, fontFamily = FontFamily.Monospace,
                        color = if (uid != null) Color(0xFF8BE39A) else Color(0xFFFFC56B), modifier = Modifier.width(140.dp))
                    Button({
                        capturing = role.key
                        c.captureNextScan = { u -> onUid(u, role.key) }
                    }) { Text("Scanner") }
                    Spacer(Modifier.width(6.dp))
                    TextButton({ c.unbind(role.key) }, enabled = uid != null) { Text("Délier") }
                }
            }
        }
    }

    capturing?.let { key ->
        AlertDialog(
            onDismissRequest = { capturing = null; c.captureNextScan = null },
            title = { Text("Approchez le tag") },
            text = { Text("Posez le tag à lier sur le dos du téléphone pour :\n${roleLabel(content.raw, key)}", fontSize = 20.sp) },
            confirmButton = {
                if (settings.simulator) TextButton({
                    c.captureNextScan = null
                    onUid("SIM" + Random.nextLong().toULong().toString(16).uppercase().take(10), key)
                }) { Text("Tag simulé") }
            },
            dismissButton = { TextButton({ capturing = null; c.captureNextScan = null }) { Text("Annuler") } },
        )
    }

    conflict?.let { (uid, key) ->
        AlertDialog(
            onDismissRequest = { conflict = null },
            title = { Text("Tag déjà utilisé") },
            text = { Text("Le tag ${shortUid(uid)} est lié à « ${roleLabel(content.raw, bindings.roleOf(uid))} ». Le réassigner à « ${roleLabel(content.raw, key)} » ?") },
            confirmButton = { TextButton({ c.bind(uid, key); conflict = null }) { Text("Réassigner") } },
            dismissButton = { TextButton({ conflict = null }) { Text("Annuler") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------- read test

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestTab(c: GameController) {
    val last by c.lastScan.collectAsState()
    val content by c.content.collectAsState()
    val settings by c.settings.collectAsState()
    var since by remember { mutableStateOf(System.currentTimeMillis()) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Test de lecture", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Posez un tag sur le téléphone : son rôle s'affiche ci-dessous.", fontSize = 20.sp)
        val l = last?.takeIf { it.time >= since }
        Box(Modifier.fillMaxWidth().background(Color(0xFF151B3D), RoundedCornerShape(16.dp)).padding(24.dp)) {
            if (l == null) Text("En attente d'un tag…", fontSize = 28.sp, color = Color.Gray)
            else Column {
                Text(roleLabel(content.raw, l.roleKey), fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    color = if (l.roleKey == null) Palette.danger else Color(0xFF8BE39A))
                Text("UID ${l.uid}${if (l.simulated) " (simulé)" else ""} · ${Stores.formatTime(l.time)}", fontSize = 18.sp, fontFamily = FontFamily.Monospace)
            }
        }
        TextButton({ since = System.currentTimeMillis() }) { Text("Effacer") }
        if (settings.simulator) {
            Text("Simulateur", fontSize = 20.sp, color = Palette.cream)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Role.all(content.raw).forEach { r -> OutlinedButton({ c.simulate(r.key) }) { Text(roleLabel(content.raw, r.key).substringBefore(" (")) } }
                OutlinedButton({ c.simulate(null) }) { Text("Inconnu") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- preview

private enum class Anim { LEVEL_UP, CORRUPTION, FINALE }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewTab(c: GameController) {
    val content by c.content.collectAsState()
    var anim by remember { mutableStateOf<Pair<Anim, String>?>(null) }
    var shoutTest by remember { mutableStateOf(false) }
    val raw = content.raw
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Prévisualisation", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Section("Avatars (après import de sophie.png / invisibles.png, GIF ou WebP animé accepté)") {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("Sophie" to false, "Sophie qui parle" to true).forEach { (l, speaking) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        fr.mission.sophie.ui.SophieAvatar(c.media, Modifier.size(130.dp), speaking = speaking); Text(l)
                    }
                }
                listOf("Invisibles" to false, "Invisibles moqueurs" to true).forEach { (l, mocking) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        fr.mission.sophie.ui.InvisiblesAvatar(c.media, Modifier.size(130.dp), mocking = mocking); Text(l)
                    }
                }
            }
        }
        Section("Animations") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                raw.powers.forEach { p ->
                    Button({ c.audio.play(Sfx.LEVEL_UP); anim = Anim.LEVEL_UP to p.id },
                        colors = ButtonDefaults.buttonColors(containerColor = parseColor(p.color))) { Text("Level-up ${p.name}", color = Palette.night) }
                }
                Button({ c.audio.play(Sfx.CORRUPTION); anim = Anim.CORRUPTION to raw.powers.first().id }) { Text("Corruption") }
                Button({ shoutTest = true }, colors = ButtonDefaults.buttonColors(containerColor = Palette.danger)) { Text("Tester le cri-mètre") }
                Button({ c.audio.play(Sfx.FINALE); anim = Anim.FINALE to "" }) { Text("Finale") }
            }
        }
        Section("Messages") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val lines = Lines(raw)
                Button({ lines.intro(0).let { c.post(Message(it.voiceId, it.speaker, it.text, Priority.ACTION), interrupt = true) } }) { Text("Message de Sophie") }
                Button({
                    val l = lines.fromList("wrongPendant", raw.messages.wrongPendant, 0)
                    c.post(Message(l.voiceId, Speaker.INVISIBLES, l.text, Priority.ACTION), interrupt = true)
                }, colors = ButtonDefaults.buttonColors(containerColor = Palette.invisibles)) { Text("Moquerie des Invisibles") }
                Button({ c.post(Message("preview", Speaker.SOPHIE, "Message d'attente (priorité basse)", Priority.IDLE)) }) { Text("Message d'attente") }
            }
            val bubble by c.bubble.collectAsState()
            bubble?.let { MessageBubble(it.message.speaker, it.message.text, c.media, Modifier.fillMaxWidth()) }
        }
        Section("Sons") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Sfx.entries.forEach { s -> OutlinedButton({ c.audio.play(s) }) { Text(s.name.lowercase()) } }
                raw.powers.forEachIndexed { i, p -> OutlinedButton({ c.audio.powerTone(i) }) { Text("Note ${p.name}") } }
            }
        }
    }

    if (shoutTest) {
        val p = raw.powers.firstOrNull { it.isShout }
        var ok by remember { mutableStateOf(false) }
        Dialog({ shoutTest = false }, DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Palette.background).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                fr.mission.sophie.ui.game.ShoutMeter(
                    p?.shout?.threshold ?: 0.8f, p?.shout?.holdSec ?: 3f,
                    if (ok) "Réussi ! (seuil ${((p?.shout?.threshold ?: 0.8f) * 100).toInt()} %)" else raw.ui.shoutLabel,
                    raw.ui.shoutMicMissing, Palette.danger, Modifier.weight(1f).fillMaxWidth(),
                ) { ok = true; c.audio.play(Sfx.LEVEL_UP) }
                Text("Le seuil et la durée se règlent dans game_content.json (champ shout du Vociférateur).", color = Color.LightGray)
                TextButton({ shoutTest = false }) { Text("Fermer") }
            }
        }
    }

    anim?.let { (kind, id) ->
        val ms = when (kind) { Anim.LEVEL_UP -> raw.settings.levelUpSec * 1000; Anim.CORRUPTION -> raw.settings.corruptionSec * 1000; Anim.FINALE -> 12000 }
        LaunchedEffect(kind, id) { delay(ms + 800L); anim = null }
        Dialog({ anim = null }, DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Palette.background).clickable { anim = null }) {
                val p = raw.power(id)
                when (kind) {
                    Anim.LEVEL_UP -> LevelUpAnimation(id, parseColor(p?.color ?: "#fff"), raw.ui.powerCounter.format(3, raw.powers.size), p?.name ?: "", c.media, ms)
                    Anim.CORRUPTION -> CorruptionAnimation(id, parseColor(p?.color ?: "#fff"), ms)
                    Anim.FINALE -> FinaleAnimation(raw.powers.map { it.id to parseColor(it.color) }, c.media, raw.final.victoryText ?: "")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- settings

@Composable
private fun SettingsTab(c: GameController) {
    val settings by c.settings.collectAsState()
    val content by c.content.collectAsState()
    val base = content.raw.settings
    var newPin by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Réglages", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (content.variants.size > 1) Section("Météo : version des lieux (cachettes, zones)") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                content.variants.forEach { v ->
                    val sel = v.id == content.variant
                    Button({ c.updateSettings { it.copy(variant = v.id) } },
                        colors = ButtonDefaults.buttonColors(containerColor = if (sel) Palette.sophie else Palette.dim,
                            contentColor = if (sel) Palette.night else Color.White)) {
                        Text((if (sel) "✓ " else "") + v.label, fontSize = 20.sp)
                    }
                }
            }
        }
        Section("Messages d'attente et codes (vide = valeur du contenu)") {
            NumberField("1er message d'attente après (s)", settings.idleFirstAfterSec, base.idleFirstAfterSec) { v -> c.updateSettings { it.copy(idleFirstAfterSec = v) } }
            NumberField("Puis toutes les (s)", settings.idleRepeatEverySec, base.idleRepeatEverySec) { v -> c.updateSettings { it.copy(idleRepeatEverySec = v) } }
            NumberField("Nombre maximum de messages", settings.idleMaxMessages, base.idleMaxMessages) { v -> c.updateSettings { it.copy(idleMaxMessages = v) } }
            NumberField("Délai minimum avant le 2e scan (s)", settings.minSecondScanDelaySec, base.minSecondScanDelaySec) { v -> c.updateSettings { it.copy(minSecondScanDelaySec = v) } }
            NumberField("Essais avant l'indice", settings.codeAttemptsBeforeHint, base.codeAttemptsBeforeHint) { v -> c.updateSettings { it.copy(codeAttemptsBeforeHint = v) } }
        }
        Section("Volumes") {
            SwitchRow("Muet (démos) : coupe voix, musique et effets", settings.muted) { v -> c.updateSettings { it.copy(muted = v) } }
            VolumeRow("Voix", settings.voiceVolume) { v -> c.updateSettings { it.copy(voiceVolume = v) } }
            VolumeRow("Musique", settings.musicVolume) { v -> c.updateSettings { it.copy(musicVolume = v) } }
            VolumeRow("Effets", settings.sfxVolume) { v -> c.updateSettings { it.copy(sfxVolume = v) } }
            SwitchRow("Musique d'ambiance (fichier « music »)", settings.musicEnabled) { v -> c.updateSettings { it.copy(musicEnabled = v) } }
            SwitchRow("Voix de secours (synthèse vocale) pour les textes sans fichier", settings.ttsFallback) { v -> c.updateSettings { it.copy(ttsFallback = v) } }
        }
        Section("Tests") {
            SwitchRow("Simulateur de scans (bouton SIM en jeu)", settings.simulator) { v -> c.updateSettings { it.copy(simulatorEnabled = v) } }
        }
        Section("Code PIN (Admin et menu MJ)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newPin, { newPin = it.filter(Char::isDigit).take(8) }, label = { Text("Nouveau PIN (4 à 8 chiffres)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
                Spacer(Modifier.width(8.dp))
                Button({
                    c.updateSettings { it.copy(pin = newPin) }
                    newPin = ""
                    Toast.makeText(ctx, "PIN modifié", Toast.LENGTH_SHORT).show()
                }, enabled = newPin.length >= 4) { Text("Changer") }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int?, default: Int, onChange: (Int?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 18.sp)
        OutlinedTextField(
            text,
            { t -> text = t.filter(Char::isDigit).take(5); onChange(text.toIntOrNull()) },
            Modifier.width(160.dp),
            placeholder = { Text("$default") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), fontSize = 18.sp)
        Switch(value, onChange)
    }
}

// ---------------------------------------------------------------------------------------------- content & voices

@Composable
private fun ContentTab(c: GameController) {
    val content by c.content.collectAsState()
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var showVoices by remember { mutableStateOf(false) }
    var mediaVersion by remember { mutableStateOf(0) }

    val pickJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val r = c.importContent(text)
            error = null
            Toast.makeText(ctx, "Contenu v${r.content.version} importé" + if (r.warnings.isNotEmpty()) " (${r.warnings.size} avertissements)" else "", Toast.LENGTH_LONG).show()
        } catch (e: ContentException) {
            error = e.message
        } catch (e: Exception) {
            error = "Lecture impossible : ${e.message}"
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val n = try { c.importMedia(uri) } catch (e: Exception) { -1 }
        mediaVersion++
        Toast.makeText(ctx, if (n >= 0) "$n fichiers importés" else "Import impossible", Toast.LENGTH_LONG).show()
    }

    val all = remember(content, mediaVersion) { Lines(content.raw).all() }
    val found = remember(all, mediaVersion) { all.count { c.media.hasAudio(it.voiceId) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Contenu et voix", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Section("Textes du jeu (game_content.json)") {
            Text("Version ${content.raw.version} · ${if (content.imported) "fichier importé" else "contenu intégré"}", fontSize = 18.sp)
            content.warnings.forEach { Text("⚠ $it", color = Color(0xFFFFC56B)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ pickJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) { Text("Importer un fichier JSON") }
                OutlinedButton({ c.resetContent(); error = null }, enabled = content.imported) { Text("Revenir au contenu intégré") }
            }
            error?.let {
                Text("Fichier refusé, l'ancien contenu reste actif :", color = Palette.danger, fontWeight = FontWeight.Bold)
                Text(it, color = Palette.danger, fontFamily = FontFamily.Monospace)
            }
        }
        Section("Voix, images et sons") {
            Text("$found/${all.size} voix trouvées · ${c.media.importedCount()} fichiers importés", fontSize = 18.sp)
            Text("Musique : « Fantascape », Eric Matyas, www.soundimage.org", fontSize = 15.sp, color = Palette.cream)
            Text("Noms attendus : <identifiant>.mp3 (ou .ogg .m4a .wav), sophie.png, invisibles.png, music.mp3, sfx_level_up.mp3…", fontSize = 15.sp, color = Color.LightGray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ pickFolder.launch(null) }) { Text("Importer un dossier") }
                OutlinedButton({ showVoices = true }) { Text("Vérifier les voix") }
                OutlinedButton({ c.clearMedia(); mediaVersion++ }) { Text("Supprimer les fichiers importés") }
            }
        }
    }

    if (showVoices) {
        Dialog({ showVoices = false }, DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Palette.night).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vérifier les voix : $found/${all.size}", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton({ showVoices = false }) { Text("Fermer ✕") }
                }
                LazyColumn {
                    items(all.sortedBy { c.media.hasAudio(it.voiceId) }) { l ->
                        val ok = c.media.hasAudio(l.voiceId)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (ok) "✓" else "✗", color = if (ok) Color(0xFF8BE39A) else Palette.danger, fontSize = 22.sp, modifier = Modifier.width(32.dp))
                            Text(l.voiceId, fontFamily = FontFamily.Monospace, modifier = Modifier.width(260.dp))
                            Text(l.text, maxLines = 1, modifier = Modifier.weight(1f), color = if (l.speaker == Speaker.SOPHIE) Palette.sophie else Palette.invisibles)
                            TextButton({ c.post(Message(l.voiceId, l.speaker, l.text, Priority.ACTION), interrupt = true) }) { Text("Écouter") }
                        }
                        HorizontalDivider(color = Color(0x22FFFFFF))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- backup

@Composable
private fun BackupTab(c: GameController) {
    val ctx = LocalContext.current
    val bindings by c.bindings.collectAsState()
    var pending by remember { mutableStateOf<fr.mission.sophie.data.TagBindings?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(c.stores.bindingsToJson(bindings).toByteArray()) }
            Toast.makeText(ctx, "Liaisons exportées", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Export impossible : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            pending = c.stores.bindingsFromJson(text)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Fichier invalide : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sauvegarde des liaisons tags ↔ rôles", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("${bindings.byUid.size} tags liés.", fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ export.launch("liaisons-tags-sophie.json") }) { Text("Exporter") }
            OutlinedButton({ import.launch(arrayOf("application/json", "*/*")) }) { Text("Importer") }
        }
    }
    pending?.let { b ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Remplacer les liaisons ?") },
            text = { Text("${b.byUid.size} liaisons vont remplacer les ${bindings.byUid.size} actuelles.") },
            confirmButton = { TextButton({ c.replaceBindings(b); pending = null }) { Text("Remplacer") } },
            dismissButton = { TextButton({ pending = null }) { Text("Annuler") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------- log

@Composable
private fun LogTab(c: GameController) {
    var entries by remember { mutableStateOf(c.stores.readLog()) }
    var filter by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Journal", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            listOf(null to "Tout", "scan" to "Scans", "voix" to "Voix", "jeu" to "Jeu").forEach { (k, l) ->
                TextButton({ filter = k }) { Text(l, fontWeight = if (filter == k) FontWeight.Bold else FontWeight.Normal) }
            }
            TextButton({ entries = c.stores.readLog() }) { Text("Actualiser") }
            TextButton({ c.stores.clearLog(); entries = emptyList() }) { Text("Vider") }
        }
        LazyColumn {
            items(entries.filter { filter == null || it.kind == filter }) { e ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(Stores.formatTime(e.time), fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.width(150.dp), color = Color.LightGray)
                    Text(e.kind, fontSize = 14.sp, modifier = Modifier.width(70.dp), color = Palette.cream)
                    Text(e.text, fontSize = 14.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- game

@Composable
private fun GameTab(c: GameController) {
    var confirm by remember { mutableStateOf(false) }
    val game by c.game.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Partie", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        val saved = game ?: c.savedGame()
        Text(if (saved == null) "Aucune partie en cours." else "Partie en cours : ${fr.mission.sophie.ui.game.phaseLabel(saved.phase)}, ${saved.acquired.size} pouvoirs.", fontSize = 18.sp)
        Button({ confirm = true }, colors = ButtonDefaults.buttonColors(containerColor = Palette.danger)) { Text("Réinitialiser (nouvelle partie au prochain lancement)") }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Réinitialiser la partie ?") },
            text = { Text("La progression sera effacée. Les liaisons des tags sont conservées.") },
            confirmButton = { TextButton({ c.endGame(); confirm = false }) { Text("Réinitialiser") } },
            dismissButton = { TextButton({ confirm = false }) { Text("Annuler") } },
        )
    }
}
