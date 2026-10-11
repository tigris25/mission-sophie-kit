package fr.mission.sophie.engine

import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** A typical party: 8 guests. */
val EIGHT = listOf("telepathe", "polyglotte", "eclipseur", "rafaleur", "invocateur", "teleporteur", "empathe", "vociferateur")

/** Drives the engine with fake time, like the Android layer does. */
class Harness(val content: GameContent = defaultContent(), seed: Int = 42) {
    val engine = GameEngine(content, Random(seed))
    var now = 1_000_000L
    var state: GameState
    val said = mutableListOf<Message>()
    val sounds = mutableListOf<Sfx>()

    init {
        state = apply(engine.newGame(now))
    }

    private fun apply(o: Output): GameState {
        said += o.messages; sounds += o.sfx; return o.state
    }

    fun send(e: GameEvent) { state = apply(engine.handle(state, e, now)) }
    fun scan(role: Role?) = send(GameEvent.Scan(role))
    fun pendant(id: String) = scan(Role.PowerRole(id))
    fun advance(ms: Long) {
        val end = now + ms
        while (now < end) { now = minOf(end, now + 250); send(GameEvent.Tick) }
    }
    fun secs(s: Int) = advance(s * 1000L)
    fun lastSaid() = said.last()
    fun saidIds() = said.map { it.voiceId }
    val phase get() = state.phase

    /** Intro pages, then the gathering with [powers], then "all here". */
    fun start(powers: List<String> = EIGHT) {
        repeat(content.intro.size) { send(GameEvent.IntroNext) }
        assertEquals(Phase.GATHERING, phase)
        powers.forEach { pendant(it) }
        send(GameEvent.GatheringDone)
    }

    /** Plays one full power step from CALL to the next step. */
    fun playPower(id: String) {
        assertEquals(Phase.CALL, phase, "expected CALL for $id")
        assertEquals(id, engine.currentStep(state))
        val p = content.power(id)!!
        pendant(id)
        if (p.isShout) {
            assertEquals(Phase.SHOUT, phase)
            send(GameEvent.ShoutDone)
        } else {
            assertEquals(Phase.WAITING, phase)
            secs(31); pendant(id); assertEquals(Phase.CODE_ENTRY, phase)
            send(GameEvent.SubmitCode(p.answers.first()))
        }
        assertEquals(Phase.LEVEL_UP, phase)
        secs(content.settings.levelUpSec + 1); assertEquals(Phase.REWARD, phase)
        rewardDone()
    }

    /** What the app does once every dialogue is over. */
    fun rewardDone() { secs(content.settings.rewardMinSec); send(GameEvent.MessagesDone) }

    /** Completes the echo by reading the sequences out of the state. */
    fun solveEcho() {
        if (phase == Phase.ECHO_INTRO) send(GameEvent.StartEcho)
        var guard = 0
        while (phase != Phase.RESTORED) {
            if (guard++ > 200) fail("echo never ends")
            if (phase == Phase.ECHO_INPUT) {
                val e = state.echo!!
                e.sequence.drop(e.position).forEach { pendant(it) }
            } else advance(500)
        }
    }

    /** Plays every step of the game up to the finale. */
    fun playAll() {
        state.steps.forEach { step ->
            when (step) {
                "@trap" -> { scan(Role.FakeBadge); secs(6); solveEcho(); secs(8); rewardDone() }
                "@final" -> { engine.finalOrder(state).forEach { pendant(it) }; scan(Role.HeartBadge) }
                else -> playPower(step)
            }
        }
    }

    companion object {
        fun defaultContent(): GameContent {
            val f = listOf("../android/src/main/assets/game_content.json", "android/src/main/assets/game_content.json")
                .map { File(it) }.first { it.exists() }
            return ContentParser.parse(f.readText()).content
        }
    }
}

class CodeNormalizerTest {
    @Test fun tolerant() {
        assertTrue(CodeNormalizer.matches("étincelle", listOf("ETINCELLE")))
        assertTrue(CodeNormalizer.matches(" Étin-celle ", listOf("ETINCELLE")))
        assertTrue(CodeNormalizer.matches("e t i n c e l l e", listOf("ÉTINCELLE")))
        assertTrue(CodeNormalizer.matches("coeur", listOf("CŒUR")))
        assertFalse(CodeNormalizer.matches("ETINCELLES", listOf("ETINCELLE")))
        assertFalse(CodeNormalizer.matches("", listOf("")))
        assertTrue(CodeNormalizer.matches("vent", listOf("OMBRE", "VENT")))
    }
}

class ContentTest {
    @Test fun defaultContentLoads() {
        val r = ContentParser.parse(File("../android/src/main/assets/game_content.json").readText())
        assertEquals(20, r.content.powers.size)
        assertEquals(18, r.content.mainPowers.size)
        assertEquals(20, Role.all(r.content).size)
        assertTrue(r.warnings.isEmpty(), "warnings: ${r.warnings}")
        assertEquals(r.content.mainPowers.map { it.id }.toSet(), r.content.sequence.toSet())
        assertEquals(r.content.mainPowers.map { it.id }.toSet(), r.content.final.order.toSet())
    }

    @Test fun nameAgeAndPlacesFromAdmin() {
        val text = File("../android/src/main/assets/game_content.json").readText()
        val def = ContentParser.parse(text)
        assertEquals("Mission Sophie · [prénom], 10 ans", def.content.ui.homeSubtitle)
        assertEquals(listOf("prenom", "age", "piege_de", "coeur_lieu", "eclipse_zone"), def.params.map { it.key })
        assertEquals("dans la cave", def.params.first { it.key == "coeur_lieu" }.values["pluie"])
        val r = ContentParser.parse(text, "beau", mapOf("prenom" to "Léa", "age" to "9", "beau.coeur_lieu" to "sous le banc", "pluie.coeur_lieu" to "au grenier", "beau.piege_de" to " "))
        assertEquals("Mission Sophie · Léa, 9 ans", r.content.ui.homeSubtitle)
        assertTrue(r.content.final.heartLocationText.contains("sous le banc"))
        assertTrue(r.content.trap.announceText.contains("du trampoline")) // blank override = content value
        assertTrue("joyeux anniversaire, Léa !" in ContentParser.json.encodeToString(GameContent.serializer(), r.content))
        assertEquals("au grenier", r.params.first { it.key == "coeur_lieu" }.values["pluie"])
        assertTrue(r.warnings.isEmpty(), "warnings: ${r.warnings}")
    }

    @Test fun weatherVariants() {
        val text = File("../android/src/main/assets/game_content.json").readText()
        val rain = ContentParser.parse(text)
        assertEquals("pluie", rain.variant)
        assertEquals(listOf("pluie", "beau"), rain.variants.map { it.id })
        assertTrue(rain.content.trap.announceText.contains("de la mezzanine"))
        assertTrue(rain.content.final.heartLocationText.contains("dans la cave"))
        val sun = ContentParser.parse(text, "beau")
        assertTrue(sun.content.trap.announceText.contains("du trampoline"))
        assertTrue(sun.content.power("eclipseur")!!.consigne.contains("l'allée du garage"))
        assertTrue(sun.warnings.isEmpty(), "warnings: ${sun.warnings}")
        assertEquals("pluie", ContentParser.parse(text, "neige").variant) // unknown -> default
        val bad = ContentParser.parse(text.replace("{{piege_de}}", "{{inconnu}}"))
        assertTrue(bad.warnings.any { "inconnu" in it })
    }

    @Test fun invalidJsonGivesPreciseError() {
        val e = runCatching { ContentParser.parse("{ \"version\": 1, ") }.exceptionOrNull()
        assertTrue(e is ContentException)
        assertTrue(e.message!!.startsWith("JSON invalide"))
    }

    @Test fun unknownPowerInSequenceIsRejected() {
        val c = Harness.defaultContent()
        val e = runCatching { ContentParser.validate(c.copy(sequence = listOf("telepathe", "licorne"))) }.exceptionOrNull()
        assertTrue(e is ContentException && e.message!!.contains("licorne"))
    }

    @Test fun codeTrialWithoutAnswerIsRejected() {
        val c = Harness.defaultContent()
        val bad = c.copy(powers = c.powers.map { if (it.id == "empathe") it.copy(answers = emptyList()) else it })
        assertTrue(runCatching { ContentParser.validate(bad) }.exceptionOrNull() is ContentException)
    }

    @Test fun stepsFollowPreferredOrderWithTrapInTheMiddle() {
        val c = Harness.defaultContent()
        val steps = c.buildSteps(EIGHT.reversed())
        assertEquals("@final", steps.last())
        val trials = steps.filter { !it.startsWith("@") }
        assertEquals(c.sequence.filter { it in EIGHT }, trials)
        assertEquals(4, steps.indexOf("@trap"))
        assertEquals(listOf("telepathe", "@final"), c.buildSteps(listOf("telepathe")))
        assertEquals(listOf("telepathe", "@trap", "rafaleur", "@final"), c.buildSteps(listOf("rafaleur", "telepathe")))
    }

    @Test fun articles() {
        assertEquals("L'", Lines.article("Éclipseuse"))
        assertEquals("L'", Lines.article("Hydrokinésiste"))
        assertEquals("L'", Lines.article("Empathe"))
        assertEquals("La ", Lines.article("Télépathe"))
        assertEquals("L'Invocatrice a répondu à l'appel !", Lines(Harness.defaultContent()).gathered(Harness.defaultContent().power("invocateur")!!).text)
    }

    @Test fun voiceIdsAreUniqueAndFollowSpec() {
        val all = Lines(Harness.defaultContent()).all().map { it.voiceId }
        assertEquals(all.size, all.toSet().size)
        listOf("intro_1", "intro_5", "gather_intro", "gather_empathe", "gather_done", "call_telepathe", "consigne_polyglotte",
            "hint_eclipseur", "success_guerison", "react_flasheur", "card_secret", "idleSophie_3", "wrongCode_2",
            "trap_announce", "trap_round_3", "trap_wrong_4", "trap_help", "final_hint", "finale_5", "bonus_intro",
            "call_vociferateur").forEach { assertTrue(it in all, "missing $it") }
    }
}

class GatheringTest {
    @Test fun gatheringDefinesTheGame() {
        val h = Harness()
        repeat(h.content.intro.size) { h.send(GameEvent.IntroNext) }
        assertEquals(Phase.GATHERING, h.phase)
        h.send(GameEvent.GatheringDone) // nobody yet: ignored
        assertEquals(Phase.GATHERING, h.phase)
        h.pendant("empathe"); h.pendant("empathe"); h.pendant("telepathe")
        assertEquals(listOf("empathe", "telepathe"), h.state.selected)
        assertEquals("gather_telepathe", h.lastSaid().voiceId)
        h.pendant("guerison") // bonus-only: no pendant, treated as unexpected
        h.scan(Role.FakeBadge)
        assertEquals(listOf("empathe", "telepathe"), h.state.selected)
        h.send(GameEvent.MjBack) // removes the last pendant
        assertEquals(listOf("empathe"), h.state.selected)
        h.pendant("telepathe")
        h.send(GameEvent.GatheringDone)
        assertEquals(listOf("telepathe", "@trap", "empathe", "@final"), h.state.steps)
        assertEquals(Phase.CALL, h.phase)
        assertEquals("telepathe", h.engine.currentStep(h.state))
    }

    @Test fun backFromFirstCallReturnsToGathering() {
        val h = Harness(); h.start()
        h.send(GameEvent.MjBack)
        assertEquals(Phase.GATHERING, h.phase)
        assertEquals(EIGHT.size, h.state.selected.size)
    }
}

class TrialTest {
    @Test fun fullTrial() {
        val h = Harness(); h.start()
        assertEquals(Phase.CALL, h.phase)
        assertEquals("call_telepathe", h.lastSaid().voiceId)
        h.playPower("telepathe")
        assertEquals(listOf("telepathe"), h.state.acquired)
        assertEquals("rafaleur", h.engine.currentStep(h.state))
        assertTrue(h.saidIds().containsAll(listOf("consigne_telepathe", "success_telepathe", "react_telepathe", "card_secret")))
        assertTrue(h.saidIds().any { it.startsWith("codeOk_") })
    }

    @Test fun rewardWaitsForDialoguesThenMaximum() {
        val h = Harness(); h.start()
        h.send(GameEvent.MjForceSuccess); h.secs(8)
        assertEquals(Phase.REWARD, h.phase)
        h.send(GameEvent.MessagesDone) // too early: at least rewardMinSec
        assertEquals(Phase.REWARD, h.phase)
        h.secs(4); h.send(GameEvent.MessagesDone)
        assertEquals(Phase.CALL, h.phase)
        val h2 = Harness(); h2.start()
        h2.send(GameEvent.MjForceSuccess); h2.secs(8 + h2.content.settings.rewardScreenSec + 1)
        assertEquals(Phase.CALL, h2.phase) // never stuck, even without the signal
    }

    @Test fun shoutTrial() {
        val h = Harness(); h.start(listOf("vociferateur"))
        h.send(GameEvent.ShoutDone); assertEquals(Phase.CALL, h.phase) // not before the scan
        h.pendant("vociferateur")
        assertEquals(Phase.SHOUT, h.phase)
        h.pendant("vociferateur"); assertEquals(Phase.SHOUT, h.phase)
        h.send(GameEvent.ShoutDone)
        assertEquals(Phase.LEVEL_UP, h.phase)
        assertEquals(listOf("vociferateur"), h.state.acquired)
    }

    @Test fun secondScanTooFast() {
        val h = Harness(); h.start()
        h.pendant("telepathe"); h.secs(10); h.pendant("telepathe")
        assertEquals(Phase.WAITING, h.phase)
        assertTrue(h.lastSaid().voiceId.startsWith("tooFastSecondScan_"))
        h.secs(25); h.pendant("telepathe")
        assertEquals(Phase.CODE_ENTRY, h.phase)
    }

    @Test fun wrongPendantDoesNotProgress() {
        val h = Harness(); h.start()
        h.pendant("eclipseur")
        assertEquals(Phase.CALL, h.phase)
        assertTrue(h.lastSaid().voiceId.startsWith("wrongPendant_"))
        h.pendant("telepathe"); h.secs(40); h.pendant("eclipseur")
        assertEquals(Phase.WAITING, h.phase)
        h.scan(null); assertTrue(h.lastSaid().voiceId.startsWith("unknownTag_"))
        h.scan(Role.FakeBadge); assertTrue(h.lastSaid().voiceId.startsWith("fakeBadgeTooEarly_"))
        h.scan(Role.HeartBadge); assertTrue(h.lastSaid().voiceId.startsWith("heartTooEarly_"))
        assertEquals(Phase.WAITING, h.phase)
        assertTrue(h.state.acquired.isEmpty())
    }

    @Test fun hintAfterThreeErrorsThenCooldown() {
        val h = Harness(); h.start()
        h.pendant("telepathe"); h.secs(31); h.pendant("telepathe")
        repeat(2) { h.send(GameEvent.SubmitCode("NON")) }
        assertFalse(h.state.hintShown)
        h.send(GameEvent.SubmitCode("NON"))
        assertTrue(h.state.hintShown)
        assertTrue("hint_telepathe" in h.saidIds())
        h.send(GameEvent.SubmitCode("étincelle")) // during cooldown: ignored
        assertEquals(Phase.CODE_ENTRY, h.phase)
        h.secs(5)
        h.send(GameEvent.SubmitCode("é tin-celle"))
        assertEquals(Phase.LEVEL_UP, h.phase)
    }

    @Test fun wrongCodeReactionsAreSpacedAndAlternate() {
        val h = Harness(); h.start()
        h.pendant("telepathe"); h.secs(31); h.pendant("telepathe")
        h.said.clear()
        repeat(2) { h.send(GameEvent.SubmitCode("NON")) }
        assertEquals(1, h.said.count { it.voiceId.startsWith("wrongCode") }, "second error too soon: no message")
        h.secs(21); h.send(GameEvent.SubmitCode("NON")) // 3rd error: hint + Sophie's encouragement
        val r = h.said.filter { it.voiceId.startsWith("wrongCode") }
        assertEquals(listOf(Speaker.INVISIBLES, Speaker.SOPHIE), r.map { it.speaker })
    }

    @Test fun idleMessagesAlternateAndStop() {
        val h = Harness(); h.start()
        h.playPower("telepathe")
        h.pendant("rafaleur")
        h.said.clear()
        h.secs(239); assertTrue(h.said.none { it.priority == Priority.IDLE })
        h.secs(2)
        val first = h.said.single { it.priority == Priority.IDLE }
        assertEquals(Speaker.SOPHIE, first.speaker)
        h.secs(150)
        val idle = h.said.filter { it.priority == Priority.IDLE }
        assertEquals(2, idle.size); assertEquals(Speaker.INVISIBLES, idle[1].speaker)
        h.secs(150 * 10)
        assertEquals(6, h.said.count { it.priority == Priority.IDLE })
    }

    @Test fun idlePerTrialOverrideAndPause() {
        val h = Harness(); h.start()
        h.pendant("telepathe") // idleFirstAfterSec = 300 for telepathe
        h.said.clear(); h.secs(290)
        assertTrue(h.said.none { it.priority == Priority.IDLE })
        h.send(GameEvent.MjPauseIdle(true)); h.secs(600)
        assertTrue(h.said.none { it.priority == Priority.IDLE })
        h.send(GameEvent.MjPauseIdle(false)); h.secs(59)
        assertTrue(h.said.none { it.priority == Priority.IDLE })
        h.secs(2); assertEquals(1, h.said.count { it.priority == Priority.IDLE })
    }

    @Test fun jokerAllowsReplacementPendant() {
        val h = Harness(); h.start()
        h.pendant("empathe"); assertEquals(Phase.CALL, h.phase)
        h.send(GameEvent.MjAllowJoker)
        h.pendant("empathe"); assertEquals(Phase.WAITING, h.phase)
        h.secs(31); h.pendant("empathe"); assertEquals(Phase.CODE_ENTRY, h.phase)
        h.send(GameEvent.SubmitCode("ETINCELLE"))
        assertEquals(listOf("telepathe"), h.state.acquired)
    }

    @Test fun noRandomImmediateRepetition() {
        val h = Harness(); h.start()
        repeat(40) { h.pendant("eclipseur") }
        val ids = h.said.filter { it.voiceId.startsWith("wrongPendant_") }.map { it.voiceId }
        ids.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
    }
}

class TrapTest {
    private fun toTrap(h: Harness, powers: List<String> = EIGHT) {
        h.start(powers)
        while (h.engine.currentStep(h.state) != "@trap") h.playPower(h.engine.currentStep(h.state)!!)
        assertEquals(Phase.TRAP_WAIT, h.phase)
    }

    @Test fun corruptionAndEchoUseOnlyChosenPowers() {
        val h = Harness(); toTrap(h)
        assertTrue("trap_announce" in h.saidIds())
        val lastBefore = h.state.acquired.last()
        h.scan(Role.FakeBadge)
        assertEquals(Phase.CORRUPTION, h.phase)
        assertEquals(lastBefore, h.state.corrupted)
        h.secs(6); assertEquals(Phase.ECHO_INTRO, h.phase)
        h.send(GameEvent.StartEcho)
        val sizes = mutableListOf<Int>()
        var lastRound = -1
        var guard = 0
        while (h.phase != Phase.RESTORED) {
            if (guard++ > 300) fail("loop")
            val e = h.state.echo!!
            if (e.round != lastRound) { sizes += e.sequence.size; lastRound = e.round
                assertEquals(e.sequence.size, e.sequence.toSet().size, "no repetition in a sequence")
                assertTrue(e.sequence.all { it in EIGHT }, "only chosen powers")
                if (e.round == 2) assertTrue(lastBefore in e.sequence)
            }
            if (h.phase == Phase.ECHO_INPUT) e.sequence.drop(e.position).forEach { h.pendant(it) } else h.advance(500)
        }
        assertEquals(listOf(3, 4, 5), sizes)
        assertNull(h.state.corrupted)
        assertTrue(lastBefore in h.state.acquired)
        h.secs(8)
        assertEquals(Phase.REWARD, h.phase)
        assertFalse(h.state.rewardShowsCard)
        h.rewardDone()
        assertEquals(Phase.CALL, h.phase)
        assertTrue("trap_restored_sophie" in h.saidIds())
    }

    @Test fun echoRoundsCappedToPendantsInPlay() {
        val h = Harness(); toTrap(h, listOf("telepathe", "rafaleur", "polyglotte"))
        h.scan(Role.FakeBadge); h.secs(6); h.send(GameEvent.StartEcho)
        val sizes = mutableSetOf<Int>()
        var guard = 0
        while (h.phase != Phase.RESTORED) {
            if (guard++ > 300) fail("loop")
            val e = h.state.echo!!
            sizes += e.sequence.size
            if (h.phase == Phase.ECHO_INPUT) e.sequence.drop(e.position).forEach { h.pendant(it) } else h.advance(500)
        }
        assertEquals(setOf(3), sizes)
    }

    @Test fun errorReplaysSameSequenceAndHelpAfterSix() {
        val h = Harness(); toTrap(h)
        h.scan(Role.FakeBadge); h.secs(6); h.send(GameEvent.StartEcho)
        while (h.phase != Phase.ECHO_INPUT) h.advance(250)
        val seq = h.state.echo!!.sequence
        repeat(6) { i ->
            val wrong = EIGHT.first { it != seq[0] }
            h.pendant(wrong)
            assertEquals(Phase.ECHO_PAUSE, h.phase)
            assertEquals(seq, h.state.echo!!.sequence)
            assertEquals(i + 1, h.state.echo!!.failures)
            while (h.phase != Phase.ECHO_INPUT) h.advance(250)
        }
        assertTrue(h.state.echo!!.slow)
        assertEquals(1800, h.state.echo!!.stepMs)
        assertTrue("trap_help" in h.saidIds())
    }

    @Test fun trapStaysSecretUntilTheScan() {
        val h = Harness()
        h.start()
        h.said.clear()
        while (h.engine.currentStep(h.state) != "@trap") h.playPower(h.engine.currentStep(h.state)!!)
        h.scan(Role.FakeBadge.let { null }) // unknown tag: unrelated
        h.said.clear()
        h.secs(240 + 150 * 6)               // long search: idle messages
        assertEquals(Phase.TRAP_WAIT, h.phase)
        assertTrue(h.said.isNotEmpty())
        assertTrue(h.said.none { it.speaker == Speaker.INVISIBLES }, "Invisibles silent while the fake badge is searched")
        val h2 = Harness(); h2.start(); h2.said.clear()
        h2.scan(Role.FakeBadge) // too early: Sophie answers, not the Invisibles
        assertEquals(Speaker.SOPHIE, h2.lastSaid().speaker)
    }

    @Test fun pendantReadTwiceInEchoIsIgnored() {
        val h = Harness(); toTrap(h)
        h.scan(Role.FakeBadge); h.secs(6); h.send(GameEvent.StartEcho)
        while (h.phase != Phase.ECHO_INPUT) h.advance(250)
        val seq = h.state.echo!!.sequence
        h.pendant(seq[0]); h.pendant(seq[0])
        assertEquals(Phase.ECHO_INPUT, h.phase)
        assertEquals(1, h.state.echo!!.position)
        assertEquals(0, h.state.echo!!.failures)
    }

    @Test fun replayLimited() {
        val h = Harness(); toTrap(h)
        h.scan(Role.FakeBadge); h.secs(6); h.send(GameEvent.StartEcho)
        while (h.phase != Phase.ECHO_INPUT) h.advance(250)
        repeat(2) {
            h.send(GameEvent.ReplayEcho); assertEquals(Phase.ECHO_PLAYBACK, h.phase)
            while (h.phase != Phase.ECHO_INPUT) h.advance(250)
        }
        h.send(GameEvent.ReplayEcho); assertEquals(Phase.ECHO_INPUT, h.phase)
    }

    @Test fun mjValidatesRepair() {
        val h = Harness(); toTrap(h)
        h.scan(Role.FakeBadge); h.secs(6)
        h.send(GameEvent.MjValidateRepair)
        assertEquals(Phase.RESTORED, h.phase)
        assertEquals(4, h.state.acquired.size)
    }

    @Test fun mjValidationStrategyWhenNotSimon() {
        val c = Harness.defaultContent().let { it.copy(trap = it.trap.copy(repair = it.trap.repair.copy(type = "mj"))) }
        val h = Harness(c); toTrap(h)
        h.scan(Role.FakeBadge); h.secs(6)
        h.send(GameEvent.StartEcho); assertEquals(Phase.ECHO_INTRO, h.phase)
        h.send(GameEvent.MjValidateRepair); assertEquals(Phase.RESTORED, h.phase)
    }

    @Test fun corruptedPowerRandomOrSpecific() {
        val base = Harness.defaultContent()
        val h = Harness(base.copy(trap = base.trap.copy(corruptedPower = "polyglotte"))); toTrap(h)
        h.scan(Role.FakeBadge); assertEquals("polyglotte", h.state.corrupted)
        val h2 = Harness(base.copy(trap = base.trap.copy(corruptedPower = "random"))); toTrap(h2)
        val before = h2.state.acquired
        h2.scan(Role.FakeBadge); assertTrue(h2.state.corrupted in before)
    }
}

class FinalTest {
    private fun toFinal(h: Harness) {
        h.start()
        while (h.engine.currentStep(h.state) != "@final") {
            when (val step = h.engine.currentStep(h.state)!!) {
                "@trap" -> { h.scan(Role.FakeBadge); h.secs(6); h.solveEcho(); h.secs(8); h.rewardDone() }
                else -> h.playPower(step)
            }
        }
        assertEquals(Phase.FINAL_INPUT, h.phase)
        assertEquals(8, h.state.acquired.size)
    }

    @Test fun finalOrderIsTheOrderOfTheTrials() {
        val c = Harness.defaultContent()
        val group = c.mainPowers.map { it.id }.shuffled(Random(5)).take(9)
        assertEquals(c.orderTrials(group), c.finalOrder(group))
        c.orderTrials(group).forEachIndexed { i, id -> assertEquals(i + 1, c.cardNumber(group, id)) }
    }

    @Test fun wordsModeStillAvailable() {
        val base = Harness.defaultContent()
        val c = base.copy(final = base.final.copy(mode = Final.MODE_WORDS))
        val all = c.mainPowers.map { it.id }
        for (n in 7..10) {
            val group = all.shuffled(Random(n)).take(n)
            val w = c.finalWord(group)!!
            val letterOf = c.orderTrials(group).associateWith { w.cardLetters[c.cardNumber(group, it)!! - 1] }
            assertEquals(w.letters, c.finalOrder(group).map { letterOf[it] }.joinToString(""))
        }
        assertNull(c.cardNumber(all.take(5), all[0]))
    }

    @Test fun finalEchoPlaysAfterAnnouncementsAndBlocksScans() {
        val h = Harness(); toFinal(h)
        assertNull(h.state.finalPlaybackAt)
        h.send(GameEvent.MessagesDone)
        assertEquals(h.now, h.state.finalPlaybackAt)
        val order = h.engine.finalOrder(h.state)
        h.pendant(order[0]) // during the echo: ignored
        assertTrue(h.state.finalScans.isEmpty())
        h.advance(order.size * 1300L + 500)
        h.pendant(order[0]); assertEquals(listOf(order[0]), h.state.finalScans)
        h.send(GameEvent.FinalReplay)
        assertTrue(h.state.finalScans.isEmpty())
    }

    @Test fun cardNumberShownAfterEachTrial() {
        val h = Harness(); h.start()
        h.playPower("telepathe")
        assertTrue(h.said.any { it.voiceId == "card_secret" && it.text.contains("n°1 ") })
        assertTrue(h.said.none { it.text.contains("{n}") })
        h.playPower("rafaleur")
        assertTrue(h.said.any { it.voiceId == "card_secret" && it.text.contains("n°2 ") })
    }

    @Test fun invalidWordsRejected() {
        val c = Harness.defaultContent()
        val dup = c.copy(final = c.final.copy(words = listOf(FinalWord("ETOILE", "ELIOTE"))))
        assertTrue(runCatching { ContentParser.validate(dup) }.exceptionOrNull() is ContentException)
        val bad = c.copy(final = c.final.copy(words = listOf(FinalWord("COURAGE", "COURAGX"))))
        assertTrue(runCatching { ContentParser.validate(bad) }.exceptionOrNull() is ContentException)
    }

    @Test fun rightOrderRevealsHeartThenFinale() {
        val h = Harness(); toFinal(h)
        h.scan(Role.HeartBadge); assertEquals(Phase.FINAL_INPUT, h.phase)
        h.engine.finalOrder(h.state).forEach { h.pendant(it) }
        assertEquals(Phase.HEART, h.phase)
        assertTrue("final_ok_sophie" in h.saidIds())
        h.scan(Role.HeartBadge)
        assertEquals(Phase.FINALE, h.phase)
        assertTrue(h.saidIds().containsAll((1..5).map { "finale_$it" }))
    }

    @Test fun noFeedbackThenLockoutAndHint() {
        val h = Harness(); toFinal(h)
        val wrong = h.engine.finalOrder(h.state).reversed()
        repeat(3) { attempt ->
            h.said.clear()
            wrong.dropLast(1).forEach { h.pendant(it) }
            assertTrue(h.said.isEmpty(), "no message during the scans")
            h.pendant(wrong.last())
            assertEquals(Phase.FINAL_LOCKOUT, h.phase)
            h.pendant(wrong.first()) // ignored during lockout
            h.secs(19); assertEquals(Phase.FINAL_LOCKOUT, h.phase)
            h.secs(2); assertEquals(Phase.FINAL_INPUT, h.phase)
            h.advance(wrong.size * 1300L + 500) // the echo is replayed after the lockout
            assertTrue(h.state.finalScans.isEmpty())
            assertEquals(attempt == 2, h.state.finalHintShown)
        }
        assertTrue("final_hint" in h.saidIds())
    }

    @Test fun duplicateAndForeignPendantsIgnored() {
        val h = Harness(); toFinal(h)
        h.pendant("invocateur"); h.pendant("invocateur"); h.pendant("givreur")
        assertEquals(listOf("invocateur"), h.state.finalScans)
    }
}

class BonusTest {
    @Test fun bonusTrialsAfterFinale() {
        val h = Harness(); h.start(); h.playAll()
        assertEquals(Phase.FINALE, h.phase)
        val bonus = h.engine.bonusPowers(h.state).map { it.id }
        assertEquals(12, bonus.size)
        assertTrue("guerison" in bonus && "telekinesie" in bonus && "givreur" in bonus && "telepathe" !in bonus)
        h.send(GameEvent.OpenBonus)
        assertEquals(Phase.BONUS_MENU, h.phase)
        assertTrue("bonus_intro" in h.saidIds())

        h.send(GameEvent.StartBonus("telepathe")) // already played: refused
        assertEquals(Phase.BONUS_MENU, h.phase)

        h.send(GameEvent.StartBonus("guerison"))
        assertEquals(Phase.WAITING, h.phase)
        assertEquals("guerison", h.engine.currentStep(h.state))
        h.send(GameEvent.BonusBack); assertEquals(Phase.BONUS_MENU, h.phase)

        h.send(GameEvent.StartBonus("guerison"))
        h.send(GameEvent.BonusEnterCode); assertEquals(Phase.CODE_ENTRY, h.phase)
        h.send(GameEvent.SubmitCode("soin")); assertEquals(Phase.LEVEL_UP, h.phase)
        h.secs(8)
        assertEquals(Phase.BONUS_MENU, h.phase)
        assertEquals(listOf("guerison"), h.state.bonusDone)
        assertEquals(8, h.state.acquired.size) // bonus does not change the main progress
    }
}

class MjAndResumeTest {
    @Test fun forceSuccessKeepsSequenceCoherent() {
        val h = Harness(); h.start()
        h.send(GameEvent.MjForceSuccess); assertEquals(Phase.LEVEL_UP, h.phase)
        h.secs(8); h.rewardDone()
        assertEquals("rafaleur", h.engine.currentStep(h.state))
        assertEquals(listOf("telepathe"), h.state.acquired)
    }

    @Test fun backRestoresPreviousStep() {
        val h = Harness(); h.start()
        h.playPower("telepathe")
        h.pendant("rafaleur"); assertEquals(Phase.WAITING, h.phase)
        h.send(GameEvent.MjBack); assertEquals(Phase.CALL, h.phase); assertEquals("rafaleur", h.engine.currentStep(h.state))
        h.send(GameEvent.MjBack); assertEquals("telepathe", h.engine.currentStep(h.state))
        assertTrue(h.state.acquired.isEmpty())
    }

    @Test fun stateSurvivesSerialization() {
        val h = Harness(); h.start()
        h.playPower("telepathe"); h.pendant("rafaleur")
        val json = ContentParser.json.encodeToString(GameState.serializer(), h.state)
        val restored = ContentParser.json.decodeFromString(GameState.serializer(), json)
        assertEquals(h.state, restored)
        val engine2 = GameEngine(h.content, Random(1))
        val o = engine2.handle(restored, GameEvent.Scan(Role.PowerRole("rafaleur")), h.now + 40_000)
        assertEquals(Phase.CODE_ENTRY, o.state.phase)
    }

    @Test fun fullGamesWithVariousGroups() {
        val all = Harness.defaultContent().mainPowers.map { it.id }
        listOf(EIGHT, all, all.take(1), all.take(2), all.shuffled(Random(3)).take(11)).forEach { group ->
            val h = Harness()
            h.start(group)
            h.playAll()
            assertEquals(Phase.FINALE, h.phase, "group $group")
            assertEquals(group.toSet(), h.state.acquired.toSet())
        }
    }
}

class MessageQueueTest {
    private fun msg(id: String, p: Priority) = Message(id, Speaker.SOPHIE, id, p)

    @Test fun idleNeverInterrupts() {
        val q = MessageQueue({ 3000L })
        q.post(msg("a", Priority.ACTION), 0)
        val r = q.post(msg("idle", Priority.IDLE), 100)
        assertTrue(r.single() is MessageQueue.Action.Dropped)
        assertEquals("a", q.current!!.message.voiceId)
    }

    @Test fun actionInterruptsIdle() {
        val q = MessageQueue({ null })
        q.post(msg("idle", Priority.IDLE), 0)
        val r = q.post(msg("a", Priority.ACTION), 100)
        assertTrue(r[0] is MessageQueue.Action.Stop && (r[0] as MessageQueue.Action.Stop).interrupted)
        assertEquals("a", q.current!!.message.voiceId)
    }

    @Test fun displayDurationAndQueue() {
        val q = MessageQueue({ if (it.voiceId == "long") 9000L else null })
        q.post(msg("long", Priority.ACTION), 0)
        assertEquals(10_000, q.current!!.until)
        q.post(msg("b", Priority.ANNOUNCE), 10)
        q.post(msg("c", Priority.ACTION), 20)
        assertEquals(listOf("c", "b"), q.pending.map { it.voiceId })
        q.tick(9_999); assertEquals("long", q.current!!.message.voiceId)
        q.tick(10_000); assertEquals("c", q.current!!.message.voiceId)
        assertEquals(16_000, q.current!!.until)
    }
}
