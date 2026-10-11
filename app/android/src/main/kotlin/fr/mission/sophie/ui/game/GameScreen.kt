package fr.mission.sophie.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mission.sophie.GameController
import fr.mission.sophie.engine.GameEngine
import fr.mission.sophie.engine.GameEvent
import fr.mission.sophie.engine.GameState
import fr.mission.sophie.engine.Phase
import fr.mission.sophie.engine.Power
import fr.mission.sophie.ui.AutoSizeText
import fr.mission.sophie.ui.BigButton
import fr.mission.sophie.ui.MessageBubble
import fr.mission.sophie.ui.Palette
import fr.mission.sophie.ui.PinDialog
import fr.mission.sophie.ui.PowerGem
import fr.mission.sophie.ui.SophieAvatar
import fr.mission.sophie.ui.parseColor
import kotlinx.coroutines.delay

@Composable
fun GameScreen(c: GameController, onExit: () -> Unit) {
    val state by c.game.collectAsState()
    val bubble by c.bubble.collectAsState()
    val settings by c.settings.collectAsState()
    var askPin by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        c.gameVisible = true
        c.audio.startMusic()
        onDispose { c.gameVisible = false; c.audio.stopMusic() }
    }
    BackHandler { /* ignored: the MJ menu is the way out */ }

    val s = state ?: return
    val engine = c.engine
    val hasSophieVideo = remember { c.media.video("sophie") != null }

    // Ask for the microphone during the intro, not in the middle of the shout trial.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val askMic = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (s.phase in GameEngine.PRE_GAME_PHASES && engine.content.powers.any { it.isShout } && !hasMicPermission(ctx)) {
            askMic.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.background)) {
        val b = bubble
        // Texts already written on the screen (instructions, hint) are not repeated in a bubble.
        val hint = engine.currentPower(s)?.hint?.takeIf { s.hintShown && s.phase in setOf(Phase.WAITING, Phase.CODE_ENTRY) }
        val finalHint = engine.content.finalHint(s.selected).takeIf { s.finalHintShown && s.phase in setOf(Phase.FINAL_INPUT, Phase.FINAL_LOCKOUT) }
        val hidden = b != null && (b.message.text == persistentText(engine, s) || b.message.text == hint || b.message.text == finalHint ||
            (b.message.voiceId == "card_secret" && s.phase == Phase.REWARD && s.rewardShowsCard))
        // While another message is shown in a bubble, the screen text waits underneath and comes back afterwards.
        val textWaits = b != null && !hidden && s.phase in TEXT_PHASES
        val textAlpha by androidx.compose.animation.core.animateFloatAsState(if (textWaits) 0f else 1f, tween(300), label = "textAlpha")
        // Side panel (only if Sophie's video exists): shown only while someone speaks, then it slides away.
        // Sophie's video while she speaks, the Invisibles' eye while they speak.
        val speaker = b?.message?.speaker
        var lastSpeaker by remember { mutableStateOf(fr.mission.sophie.engine.Speaker.SOPHIE) }
        if (speaker != null) lastSpeaker = speaker
        val side = hasSophieVideo && s.phase !in NO_SIDE_PHASES && speaker != null
        Column(Modifier.fillMaxSize()) {
            TopBar(c, engine, s, onLongPress = { askPin = true })
            Row(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedVisibility(
                    visible = side,
                    enter = androidx.compose.animation.expandHorizontally() + fadeIn(),
                    exit = androidx.compose.animation.shrinkHorizontally() + fadeOut(),
                ) {
                    Box(Modifier.fillMaxHeight().padding(start = 12.dp, top = 4.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                        if (lastSpeaker == fr.mission.sophie.engine.Speaker.INVISIBLES) {
                            Box(Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(20.dp))
                                .background(Palette.invisiblesDark).border(3.dp, Palette.invisibles, RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center) {
                                fr.mission.sophie.ui.InvisiblesAvatar(c.media, Modifier.fillMaxWidth(0.95f).aspectRatio(1f), mocking = true)
                            }
                        } else {
                            fr.mission.sophie.ui.SophieVideo(c.media, speaking = true, Modifier.fillMaxHeight())
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp, vertical = 8.dp).alpha(textAlpha)) {
                    PhaseContent(c, engine, s, withSideVideo = side, onExit = onExit)
                }
            }
        }

        // Over busy screens (keyboard, animations, echo) the bubble is compact and sits over the gem bar.
        val compact = s.phase in COMPACT_BUBBLE_PHASES
        AnimatedVisibility(
            visible = b != null && !hidden,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = if (compact) 2.dp else 70.dp, start = if (side && !compact) SIDE_WIDTH else 0.dp),
        ) {
            if (b != null) MessageBubble(b.message.speaker, b.message.text, c.media,
                Modifier.widthIn(max = if (compact) 1000.dp else 820.dp).padding(horizontal = if (compact) 70.dp else 16.dp), compact = compact,
                // The side panel already shows who speaks: no second avatar in the bubble.
                showAvatar = !side)
        }

        // Whenever the Invisibles speak, the whole screen glitches for a moment.
        InvisiblesGlitch(b?.takeIf { it.message.speaker == fr.mission.sophie.engine.Speaker.INVISIBLES }?.startedAt)

        // Contactless symbol: blinks while the app waits for a pendant or a badge.
        NfcHint(engine, s, Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 14.dp))

        if (settings.simulator) SimulatorPanel(c, Modifier.align(Alignment.TopEnd))
    }

    if (askPin) PinDialog(settings.pin, "Menu du Maître du jeu", onDismiss = { askPin = false }) { askPin = false; menu = true }
    if (menu) MjMenu(c, onClose = { menu = false }, onExit = onExit)
}

/** Full-screen animations keep the whole width. */
private val NO_SIDE_PHASES = setOf(Phase.LEVEL_UP, Phase.RESTORED, Phase.CORRUPTION, Phase.FINALE)
private val SIDE_WIDTH = 210.dp

/** Screens made of text (call, instructions, trap, echo explanation, heart): hidden while another bubble speaks. */
private val TEXT_PHASES = setOf(Phase.CALL, Phase.WAITING, Phase.TRAP_WAIT, Phase.ECHO_INTRO, Phase.HEART)

private val COMPACT_BUBBLE_PHASES = setOf(
    Phase.CODE_ENTRY, Phase.SHOUT, Phase.GATHERING, Phase.BONUS_MENU, Phase.LEVEL_UP, Phase.RESTORED, Phase.CORRUPTION, Phase.FINALE,
    Phase.ECHO_PAUSE, Phase.ECHO_PLAYBACK, Phase.ECHO_INPUT, Phase.FINAL_INPUT, Phase.FINAL_LOCKOUT,
)

/** Texts already written on the main screen: the bubble is not shown for them. */
private fun persistentText(engine: GameEngine, s: GameState): String? {
    val p = engine.currentPower(s)
    return when (s.phase) {
        Phase.INTRO -> engine.content.intro.getOrNull(s.introPage)?.text
        Phase.CALL -> p?.callText
        Phase.WAITING, Phase.SHOUT -> p?.consigne
        Phase.TRAP_WAIT -> engine.content.trap.announceText
        Phase.ECHO_INTRO -> engine.content.trap.repairIntroText
        Phase.HEART -> engine.content.final.heartLocationText
        Phase.FINAL_INPUT, Phase.FINAL_LOCKOUT -> engine.content.final.consigneText
        else -> null
    }
}

@Composable
private fun TopBar(c: GameController, engine: GameEngine, s: GameState, onLongPress: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sophie's logo: 3-second press opens the game-master menu.
        SophieAvatar(
            c.media,
            Modifier.size(52.dp).pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val up = withTimeoutOrNull(3000) { waitForUpOrCancellation() }
                    if (up == null) onLongPress()
                }
            },
        )
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            // In the gathering the bar stays empty: the grid below shows the pendants.
            // Hidden during the gathering (the grid shows the pendants) and the final sequence (it would give the order away).
            val hide = s.phase in GameEngine.PRE_GAME_PHASES || s.phase == Phase.FINAL_INPUT || s.phase == Phase.FINAL_LOCKOUT
            val powers = if (hide) emptyList() else engine.powersOf(s)
            val slot = if (powers.isEmpty()) 0.dp else minOf(56.dp, maxWidth / powers.size)
            Row(verticalAlignment = Alignment.CenterVertically) {
                powers.forEach { p ->
                    val lit = p.id in s.acquired
                    val corrupted = p.id == s.corrupted
                    Box(Modifier.size(slot), contentAlignment = Alignment.Center) {
                        PowerGem(p.id, parseColor(p.color), lit = lit, modifier = Modifier.size(slot * 0.84f), glow = if (lit) 0.3f else 0f)
                        if (corrupted) Text("✕", color = Palette.danger, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.width(52.dp))
    }
}

@Composable
private fun PhaseContent(c: GameController, engine: GameEngine, s: GameState, withSideVideo: Boolean = false, onExit: () -> Unit = {}) {
    val content = engine.content
    val ui = content.ui
    val p = engine.currentPower(s)
    val color = p?.let { parseColor(it.color) } ?: Palette.sophie
    when (s.phase) {
        Phase.INTRO -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            val line = content.intro[s.introPage]
            val invisibles = line.speaker == fr.mission.sophie.engine.Speaker.INVISIBLES
            // With the side video, Sophie is already on screen: only the Invisibles get an avatar here.
            // With the side panel, the speaker (Sophie's video or the Invisibles' eye) is already shown on the left.
            if (!withSideVideo) {
                if (invisibles) fr.mission.sophie.ui.InvisiblesAvatar(c.media, Modifier.size(200.dp), mocking = true)
                else SophieAvatar(c.media, Modifier.size(200.dp), power = 0.4f)
                Spacer(Modifier.width(24.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                AutoSizeText(line.text, Modifier.weight(1f).fillMaxWidth(), maxSize = 34.sp, color = if (invisibles) Palette.invisibles else Color.White)
                val last = s.introPage == content.intro.lastIndex
                BigButton(if (last) ui.startButton else ui.nextButton, Modifier.align(Alignment.End)) { c.dispatch(GameEvent.IntroNext) }
            }
        }

        Phase.CALL -> CenterColumn {
            if (p != null) {
                PulsingGem(p, lit = false)
                Spacer(Modifier.height(12.dp))
                AutoSizeText(p.callText, Modifier.weight(1f).fillMaxWidth(), maxSize = 36.sp, weight = FontWeight.SemiBold)
            }
        }

        Phase.GATHERING -> Gathering(c, engine, s)

        Phase.WAITING -> CenterColumn {
            if (p != null) {
                Text(p.title, color = color, fontSize = 38.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                AutoSizeText(p.consigne, Modifier.weight(1f).fillMaxWidth(), maxSize = 30.sp)
                if (s.hintShown) Text(p.hint, color = Palette.cream, fontSize = 22.sp, textAlign = TextAlign.Center)
                if (s.bonusPower != null) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    BigButton(ui.backButton, color = Palette.dim.copy(alpha = 0.9f), height = 60.dp, textSize = 22.sp) { c.dispatch(GameEvent.BonusBack) }
                    BigButton(ui.enterCodeButton, height = 60.dp, textSize = 22.sp) { c.dispatch(GameEvent.BonusEnterCode) }
                }
            }
        }

        Phase.SHOUT -> if (p != null) Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(p.title, color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            ShoutMeter(p.shout.threshold, p.shout.holdSec, ui.shoutLabel, ui.shoutMicMissing, color, Modifier.weight(1f).fillMaxWidth()) {
                c.dispatch(GameEvent.ShoutDone)
            }
            if (s.bonusPower != null) BigButton(ui.backButton, color = Palette.dim.copy(alpha = 0.9f), height = 52.dp, textSize = 20.sp) { c.dispatch(GameEvent.BonusBack) }
        }

        Phase.CODE_ENTRY -> CodeEntry(c, s, p, color)

        Phase.BONUS_MENU -> Box(Modifier.fillMaxSize()) {
            BonusMenu(c, engine, s)
            BigButton(ui.homeButton, Modifier.align(Alignment.BottomEnd), color = Palette.cream, height = 42.dp, textSize = 15.sp) { onExit() }
        }

        Phase.LEVEL_UP, Phase.RESTORED -> {
            val id = s.animPower ?: p?.id ?: ""
            val power = content.power(id)
            val counter = if (s.bonusPower != null) ui.bonusTitle else ui.powerCounter.format(s.acquired.size, engine.powersOf(s).size)
            LevelUpAnimation(id, parseColor(power?.color ?: "#FFFFFF"), counter, power?.name ?: "", c.media, content.settings.levelUpSec * 1000)
        }

        Phase.REWARD -> CenterColumn {
            if (p != null) PulsingGem(p, lit = true)
            Spacer(Modifier.height(16.dp))
            val n = p?.let { content.cardNumber(s.selected, it.id) }
            // The card instruction appears only once Sophie and the Invisibles have spoken (not under their bubbles).
            val talking by c.bubble.collectAsState()
            var cardTime by remember(s.phaseStartedAt) { mutableStateOf(false) }
            if (talking == null || talking?.message?.voiceId == "card_secret") cardTime = true
            if (s.rewardShowsCard && cardTime) AutoSizeText(fr.mission.sophie.engine.Lines.withNumber(ui.cardReminder, n), Modifier.weight(1f).fillMaxWidth(), maxSize = 36.sp, color = Palette.cream, weight = FontWeight.SemiBold)
        }

        // The trap must stay secret until the scan: Sophie announces what looks like a lost power.
        Phase.TRAP_WAIT -> CenterColumn {
            MysteryGem()
            Spacer(Modifier.height(12.dp))
            AutoSizeText(content.trap.announceText, Modifier.weight(1f).fillMaxWidth(), maxSize = 34.sp, weight = FontWeight.SemiBold)
        }

        Phase.CORRUPTION -> {
            val id = s.animPower ?: ""
            Box(Modifier.fillMaxSize()) {
                CorruptionAnimation(id, parseColor(content.power(id)?.color ?: "#FFFFFF"), content.settings.corruptionSec * 1000)
                fr.mission.sophie.ui.InvisiblesAvatar(c.media, Modifier.size(150.dp).align(Alignment.CenterEnd), mocking = true)
            }
        }

        Phase.ECHO_INTRO -> CenterColumn {
            AutoSizeText(content.trap.repairIntroText, Modifier.weight(1f).fillMaxWidth(), maxSize = 30.sp)
            BigButton(ui.startEchoButton) { c.dispatch(GameEvent.StartEcho) }
        }

        Phase.ECHO_PAUSE, Phase.ECHO_PLAYBACK, Phase.ECHO_INPUT -> Echo(c, engine, s)

        Phase.FINAL_INPUT, Phase.FINAL_LOCKOUT -> FinalInput(c, engine, s)

        Phase.HEART -> CenterColumn {
            val inf = rememberInfiniteTransition(label = "heart")
            val beat by inf.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "b")
            Text("♥", color = Color(0xFFFF6FA0), fontSize = 90.sp, modifier = Modifier.scale(beat))
            AutoSizeText(content.final.heartLocationText, Modifier.weight(1f).fillMaxWidth(), maxSize = 36.sp, weight = FontWeight.SemiBold)
        }

        Phase.FINALE -> Box(Modifier.fillMaxSize()) {
            val powers = engine.powersOf(s).map { it.id to parseColor(it.color) }
            FinaleAnimation(powers, c.media, content.final.victoryText ?: "")
            // The bonus button appears once the finale has played, only if bonus trials remain.
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
            val ready = now >= s.phaseStartedAt + content.settings.finaleSec * 1000L
            if (ready) Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (engine.bonusPowers(s).isNotEmpty()) {
                    BigButton(ui.bonusButton, color = Palette.sophie, height = 42.dp, textSize = 15.sp) {
                        c.dispatch(GameEvent.OpenBonus)
                    }
                }
                BigButton(ui.homeButton, color = Palette.cream, height = 42.dp, textSize = 15.sp) { onExit() }
            }
        }
    }
}

@Composable
private fun CenterColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content)

@Composable
private fun PulsingGem(p: Power, lit: Boolean) {
    val inf = rememberInfiniteTransition(label = "gem")
    val g by inf.animateFloat(0.2f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "g")
    PowerGem(p.id, parseColor(p.color), lit = lit, modifier = Modifier.size(110.dp), glow = g)
}

@Composable
private fun Glitch(content: @Composable () -> Unit) {
    val inf = rememberInfiniteTransition(label = "glitch")
    val a by inf.animateFloat(0.75f, 1f, infiniteRepeatable(tween(180), RepeatMode.Reverse), label = "a")
    Box(Modifier.fillMaxSize().alpha(a).background(Palette.invisiblesDark.copy(alpha = 0.4f), RoundedCornerShape(24.dp))) { content() }
}

@Composable
private fun CodeEntry(c: GameController, s: GameState, p: Power?, color: Color) {
    var code by remember(s.stepIndex) { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.codeCooldownUntil) {
        while (true) { now = System.currentTimeMillis(); delay(250) }
    }
    val cooling = (s.codeCooldownUntil ?: 0) > now
    val ui = c.engine.content.ui
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p != null) PowerGem(p.id, color, lit = false, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Text(ui.codePrompt, fontSize = 26.sp, color = Palette.cream)
        }
        if (s.hintShown && p != null) Text(p.hint, fontSize = 20.sp, color = Palette.cream, textAlign = TextAlign.Center, maxLines = 2)
        Box(
            Modifier.padding(vertical = 6.dp).widthIn(min = 360.dp).height(58.dp)
                .border(3.dp, color, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (cooling) ui.waitCooldown else code.ifEmpty { "…" }, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp,
                color = if (code.isEmpty() || cooling) Color.Gray else Color.White)
        }
        AzertyKeyboard(
            onKey = { if (code.length < 24) code += it },
            onBackspace = { code = code.dropLast(1) },
            onValidate = {
                if (code.isNotBlank()) {
                    c.dispatch(GameEvent.SubmitCode(code))
                    code = ""
                }
            },
            validateLabel = ui.validateButton,
            enabled = !cooling,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun Echo(c: GameController, engine: GameEngine, s: GameState) {
    val echo = s.echo ?: return
    // Crystals of the pendants in play; tones follow the power's index in the content (stable per power).
    val powers = engine.powersOf(s)
    val allPowers = engine.content.powers
    var litIndex by remember { mutableIntStateOf(-1) }
    // Playback: light each crystal in turn and play its tone.
    LaunchedEffect(s.phase, echo.playbackStartAt) {
        litIndex = -1
        if (s.phase != Phase.ECHO_PLAYBACK) return@LaunchedEffect
        var last = -1
        while (true) {
            val t = System.currentTimeMillis() - echo.playbackStartAt
            val i = (t / echo.stepMs).toInt()
            if (i >= echo.sequence.size) { litIndex = -1; break }
            val on = t % echo.stepMs < echo.stepMs * 0.75
            val pIdx = powers.indexOfFirst { it.id == echo.sequence[i] }
            if (i != last) { last = i; c.audio.powerTone(allPowers.indexOfFirst { it.id == echo.sequence[i] }) }
            litIndex = if (on) pIdx else -1
            delay(30)
        }
    }
    // Input: flash the crystal of the last correct pendant.
    var flash by remember { mutableIntStateOf(-1) }
    LaunchedEffect(echo.position, echo.round) {
        if (s.phase == Phase.ECHO_INPUT && echo.position > 0) {
            flash = powers.indexOfFirst { it.id == echo.sequence[echo.position - 1] }
            delay(600)
        }
        flash = -1
    }
    val ui = engine.content.ui
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        Text("Écho ${echo.round + 1} / ${engine.content.trap.repair.rounds.size}", fontSize = 30.sp, color = Palette.cream, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val slot = minOf(92.dp, maxWidth / powers.size.coerceAtLeast(1))
            Row {
                powers.forEachIndexed { i, p ->
                    val on = i == litIndex || i == flash
                    // Fixed-size slot so the row does not move when a crystal grows.
                    Box(Modifier.size(slot), contentAlignment = Alignment.Center) {
                        PowerGem(p.id, parseColor(p.color), lit = on, glow = if (on) 1f else 0f,
                            modifier = Modifier.size(if (on) slot * 0.95f else slot * 0.78f))
                    }
                }
            }
        }
        // Progress pastilles for the current sequence.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            echo.sequence.indices.forEach { i ->
                Box(Modifier.size(28.dp).clip(CircleShape).background(if (i < echo.position) Palette.cream else Palette.dim))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (s.phase) {
                    Phase.ECHO_INPUT -> "À vous ! Posez les pendentifs dans l'ordre."
                    else -> "Regardez et écoutez…"
                },
                fontSize = 26.sp,
            )
            Spacer(Modifier.width(24.dp))
            if (s.phase == Phase.ECHO_INPUT) {
                BigButton("${ui.replayButton} (${echo.replaysLeft})", enabled = echo.replaysLeft > 0, height = 60.dp, textSize = 20.sp) {
                    c.dispatch(GameEvent.ReplayEcho)
                }
            }
        }
    }
}

@Composable
private fun FinalInput(c: GameController, engine: GameEngine, s: GameState) {
    val content = engine.content
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.phase) { while (true) { now = System.currentTimeMillis(); delay(50) } }
    val locked = s.phase == Phase.FINAL_LOCKOUT
    val order = engine.finalOrder(s)
    val echoMode = content.final.mode == fr.mission.sophie.engine.Final.MODE_ECHO
    // Crystals shown in a shuffled order, stable for the game and never equal to the answer.
    val crystals = remember(order) {
        val byId = content.powers.associateBy { it.id }
        var ids = order.sorted().shuffled(kotlin.random.Random(order.sorted().hashCode()))
        if (ids.size > 1 && ids == order) ids = ids.drop(1) + ids.first()
        ids.mapNotNull { byId[it] }
    }
    val step = content.final.echoStepMs
    val at = s.finalPlaybackAt
    val playIndex = if (at == null || now < at) -1 else ((now - at) / step).toInt().takeIf { it < order.size } ?: -1
    val playing = playIndex >= 0
    val litId = if (playing && (now - at!!) % step < step * 0.75) order[playIndex] else null
    // One tone per crystal, as in the echo of the trap.
    LaunchedEffect(playIndex) { if (playIndex >= 0) c.audio.powerTone(content.powers.indexOfFirst { it.id == order[playIndex] }) }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        AutoSizeText(content.final.consigneText ?: "", Modifier.fillMaxWidth().height(90.dp), maxSize = 28.sp)
        if (echoMode) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val slot = minOf(84.dp, maxWidth / crystals.size.coerceAtLeast(1))
                Row {
                    crystals.forEach { p ->
                        val on = p.id == litId
                        Box(Modifier.size(slot), contentAlignment = Alignment.Center) {
                            PowerGem(p.id, parseColor(p.color), lit = on, glow = if (on) 1f else 0f,
                                modifier = Modifier.size(if (on) slot * 0.95f else slot * 0.72f))
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(order.size) { i ->
                val on = !locked && !playing && i < s.finalScans.size
                Canvas(Modifier.size(44.dp)) {
                    drawCircle(if (on) Palette.cream else Palette.dim)
                    if (on) drawCircle(Palette.sophie.copy(alpha = 0.5f), radius = size.minDimension * 0.7f, style = androidx.compose.ui.graphics.drawscope.Stroke(6f))
                }
            }
        }
        when {
            locked -> {
                val left = (((s.phaseUntil ?: now) - now) / 1000 + 1).coerceAtLeast(0)
                Text("${content.ui.lockoutLabel} $left", fontSize = 30.sp, color = Palette.invisibles, fontWeight = FontWeight.Bold)
            }
            playing -> Text(content.ui.finalWatchLabel, fontSize = 26.sp, color = Palette.cream)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.finalHintShown) Text(content.finalHint(s.selected), fontSize = 20.sp, color = Palette.cream, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f))
                if (echoMode && at != null) BigButton(content.ui.finalReplayButton, height = 56.dp, textSize = 20.sp) { c.dispatch(GameEvent.FinalReplay) }
            }
        }
    }
}

/** Short purple interference over the whole screen, restarted each time [key] changes. */
@Composable
private fun InvisiblesGlitch(key: Long?) {
    if (key == null) return
    val t = remember(key) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(650)) }
    val v = t.value
    if (v >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val strength = 1f - v
        drawRect(Palette.invisibles.copy(alpha = 0.12f * strength))
        val seed = (v * 25).toInt()
        repeat(9) { i ->
            val r = kotlin.random.Random(seed * 31 + i)
            val y = r.nextFloat() * size.height
            val h = 3f + r.nextFloat() * 26f
            val x = (r.nextFloat() - 0.5f) * 60f
            drawRect(
                (if (i % 3 == 0) Color(0xFF3BE8FF) else Palette.invisibles).copy(alpha = 0.35f * strength),
                androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(size.width, h),
            )
        }
    }
}

/** Gathering: every main power as a gem, lit when its pendant has been scanned. */
@Composable
private fun Gathering(c: GameController, engine: GameEngine, s: GameState) {
    val content = engine.content
    val powers = content.mainPowers
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        Text(content.ui.gatherPrompt, fontSize = 30.sp, color = Palette.cream, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val perRow = (powers.size + 1) / 2
            val slot = minOf(92.dp, maxWidth / perRow)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                powers.chunked(perRow).forEach { row ->
                    Row {
                        row.forEach { p ->
                            val on = p.id in s.selected
                            Column(Modifier.size(slot, slot + 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                PowerGem(p.id, parseColor(p.color), lit = on, glow = if (on) 0.7f else 0f, modifier = Modifier.size(slot * 0.78f))
                                Text(p.talent, fontSize = 11.sp, maxLines = 1, color = if (on) Color.White else Color.Gray)
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${s.selected.size} Gardienne${if (s.selected.size > 1) "s" else ""}", fontSize = 26.sp, color = Palette.cream)
            Spacer(Modifier.width(24.dp))
            BigButton(content.ui.gatherButton, enabled = s.selected.isNotEmpty(), height = 64.dp, textSize = 20.sp) {
                c.dispatch(GameEvent.GatheringDone)
            }
        }
    }
}

/** After the finale: the trials of the powers that were not chosen. */
@Composable
private fun BonusMenu(c: GameController, engine: GameEngine, s: GameState) {
    val bonus = engine.bonusPowers(s)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        Text(engine.content.ui.bonusTitle, fontSize = 32.sp, color = Palette.cream, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val perRow = minOf(bonus.size, 7).coerceAtLeast(1)
            val slot = minOf(110.dp, maxWidth / perRow)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bonus.chunked(perRow).forEach { row ->
                    Row {
                        row.forEach { p ->
                            val done = p.id in s.bonusDone
                            Column(
                                Modifier.size(slot, slot + 22.dp).clip(RoundedCornerShape(16.dp))
                                    .clickable { c.dispatch(GameEvent.StartBonus(p.id)) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                PowerGem(p.id, parseColor(p.color), lit = done, glow = if (done) 0.6f else 0.2f, modifier = Modifier.size(slot * 0.72f))
                                Text(p.talent, fontSize = 13.sp, maxLines = 1)
                                Text(if (done) "✓" else p.title, fontSize = 11.sp, maxLines = 1, color = Palette.cream)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A lit gem with a question mark: "a lost power" (the trap, not revealed). */
@Composable
private fun MysteryGem() {
    val inf = rememberInfiniteTransition(label = "mystery")
    val g by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "g")
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Palette.cream.copy(alpha = 0.5f * g), Color.Transparent), center, r), r)
            drawCircle(Palette.sophie, r * 0.62f)
            drawCircle(Color.White.copy(alpha = 0.5f), r * 0.62f, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.05f))
        }
        Text("?", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Palette.night)
    }
}

/** True when a scan is expected now (blinking symbol). */
private fun waitsForScan(engine: GameEngine, s: GameState, now: Long): Boolean {
    if (s.bonusPower != null) return false
    return when (s.phase) {
        Phase.GATHERING, Phase.CALL, Phase.TRAP_WAIT, Phase.ECHO_INPUT, Phase.HEART -> true
        // Second scan: only once the minimum time of the trial has passed (not during the first 30 s).
        Phase.WAITING -> now >= (s.firstScanAt ?: 0L) + engine.content.settings.minSecondScanDelaySec * 1000L
        Phase.FINAL_INPUT -> {
            val at = s.finalPlaybackAt
            at != null && now >= at + engine.finalOrder(s).size * engine.content.final.echoStepMs.toLong() + 400
        }
        else -> false
    }
}

@Composable
private fun NfcHint(engine: GameEngine, s: GameState, modifier: Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val waiting = waitsForScan(engine, s, now)
    val inf = rememberInfiniteTransition(label = "nfc")
    val blink by inf.animateFloat(0.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink")
    val alpha = if (waiting) blink else 0.12f
    Canvas(modifier.size(46.dp).alpha(alpha)) {
        val w = size.width
        val stroke = w * 0.075f
        val c = androidx.compose.ui.geometry.Offset(w * 0.22f, size.height / 2)
        drawCircle(Palette.cream, w * 0.07f, c)
        for (i in 1..3) {
            val r = w * 0.19f * i
            drawArc(Palette.cream, -45f, 90f, false,
                topLeft = androidx.compose.ui.geometry.Offset(c.x - r, c.y - r),
                size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}
