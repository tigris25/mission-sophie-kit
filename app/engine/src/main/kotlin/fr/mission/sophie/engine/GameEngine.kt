package fr.mission.sophie.engine

import kotlin.random.Random

/**
 * Pure game logic. Stateless: every call takes the current [GameState] and returns a new one plus effects.
 * Time is passed explicitly (epoch millis), timers are driven by [GameEvent.Tick].
 *
 * Flow: intro → gathering (each girl scans her pendant) → trials of the chosen powers, with the trap in the
 * middle → final sequence → heart → finale → optional bonus trials (powers not chosen).
 */
class GameEngine(
    val content: GameContent,
    private val random: Random = Random.Default,
    private val repairStrategy: TrapRepairStrategy = TrapRepairStrategy.forContent(content),
) {
    val lines = Lines(content)
    private val settings get() = content.settings

    fun newGame(now: Long): Output {
        val tx = Tx(GameState(phase = Phase.INTRO, phaseStartedAt = now), now)
        tx.say(lines.intro(0), Priority.ANNOUNCE)
        tx.log("Nouvelle partie")
        return tx.output(interrupt = true)
    }

    fun handle(state: GameState, event: GameEvent, now: Long): Output {
        val tx = Tx(state, now)
        when (event) {
            is GameEvent.Tick -> tx.tick()
            is GameEvent.Scan -> tx.scan(event.role)
            is GameEvent.IntroNext -> tx.introNext()
            is GameEvent.MessagesDone -> tx.messagesDone()
            is GameEvent.GatheringDone -> tx.gatheringDone()
            is GameEvent.SubmitCode -> tx.submitCode(event.code)
            is GameEvent.ShoutDone -> tx.shoutDone()
            is GameEvent.StartEcho -> if (state.phase == Phase.ECHO_INTRO) repairStrategy.start(tx)
            is GameEvent.ReplayEcho -> repairStrategy.replay(tx)
            is GameEvent.OpenBonus -> tx.openBonus()
            is GameEvent.StartBonus -> tx.startBonus(event.powerId)
            is GameEvent.BonusEnterCode -> tx.bonusEnterCode()
            is GameEvent.BonusBack -> tx.bonusBack()
            is GameEvent.FinalReplay -> tx.finalReplay()
            is GameEvent.MjForceSuccess -> tx.mjForceSuccess()
            is GameEvent.MjForceCode -> tx.mjForceCode()
            is GameEvent.MjAllowJoker -> tx.mjAllowJoker()
            is GameEvent.MjSendHint -> tx.mjSendHint()
            is GameEvent.MjSay -> tx.mjSay(event.speaker, event.text)
            is GameEvent.MjPauseIdle -> tx.pauseIdle(event.paused)
            is GameEvent.MjValidateRepair -> tx.mjValidateRepair()
            is GameEvent.MjBack -> tx.mjBack()
        }
        return tx.output(interrupt = tx.interrupt)
    }

    /** Current step id ("telepathe", "@trap", "@final"), the bonus power during a bonus trial, else null. */
    fun currentStep(state: GameState): String? = when {
        state.bonusPower != null -> state.bonusPower
        state.phase in PRE_GAME_PHASES || state.phase == Phase.BONUS_MENU -> null
        else -> state.steps.getOrNull(state.stepIndex)
    }

    fun currentPower(state: GameState): Power? = currentStep(state)?.let { content.power(it) }

    /** Powers of this game in play order (progress bar). During the gathering: the ones already scanned. */
    fun powersOf(state: GameState): List<Power> =
        (if (state.steps.isEmpty()) state.selected else content.orderTrials(state.selected)).mapNotNull { content.power(it) }

    fun finalOrder(state: GameState): List<String> = content.finalOrder(state.selected)

    fun bonusPowers(state: GameState): List<Power> = content.bonusPowers(state.selected)

    /** Transaction: mutable view of the state for one event. */
    inner class Tx(var s: GameState, val now: Long) {
        private val messages = mutableListOf<Message>()
        private val sfx = mutableListOf<Sfx>()
        private val logs = mutableListOf<String>()
        var interrupt = false

        val engine: GameEngine get() = this@GameEngine
        val content: GameContent get() = this@GameEngine.content

        fun output(interrupt: Boolean) = Output(s, messages.toList(), sfx.toList(), logs.toList(), interrupt)

        fun say(line: VoiceLine?, priority: Priority) {
            if (line == null) return
            messages += Message(line.voiceId, line.speaker, line.text, priority)
        }

        fun sound(x: Sfx) { sfx += x }
        fun log(x: String) { logs += x }

        /** Picks a random item of a list without immediate repetition. */
        fun pick(key: String, list: List<Line>): VoiceLine? {
            if (list.isEmpty()) return null
            val last = s.lastPicks[key]
            val idx = if (list.size == 1) 0 else {
                var i: Int
                do { i = random.nextInt(list.size) } while (i == last)
                i
            }
            s = s.copy(lastPicks = s.lastPicks + (key to idx))
            return lines.fromList(key, list, idx)
        }

        fun setPhase(phase: Phase, until: Long? = null) {
            s = s.copy(phase = phase, phaseStartedAt = now, phaseUntil = until)
        }

        fun secs(x: Int): Long = x * 1000L

        /** Powers the echo can use: the chosen ones. */
        fun echoPool(): List<String> = s.selected

        // ---------------------------------------------------------------- steps

        private fun resetTrial() {
            s = s.copy(
                activePendant = null, firstScanAt = null, codeErrors = 0, hintShown = false,
                codeCooldownUntil = null, jokerAllowed = false, echo = null, animPower = null, idle = IdleState(),
                lastWrongCodeMsgAt = null, wrongCodeMsgs = 0,
                finalScans = emptyList(),
            )
        }

        fun enterStep(index: Int) {
            resetTrial()
            s = s.copy(stepIndex = index)
            val step = s.steps[index]
            when {
                step == GameContent.STEP_TRAP -> {
                    if (s.trapDone) { advance(); return }
                    setPhase(Phase.TRAP_WAIT)
                    say(lines.trapAnnounce(), Priority.ANNOUNCE)
                    say(lines.trapTeaser(), Priority.ANNOUNCE)
                    startIdle(settings.idleFirstAfterSec)
                    log("Piège annoncé")
                }
                step == GameContent.STEP_FINAL -> {
                    setPhase(Phase.FINAL_INPUT)
                    s = s.copy(finalFailures = 0, finalHintShown = false, finalPlaybackAt = null)
                    say(lines.finalAnnounce(), Priority.ANNOUNCE)
                    say(lines.finalConsigne(), Priority.ANNOUNCE)
                    log("Séquence finale")
                }
                else -> {
                    val p = content.power(step)!!
                    setPhase(Phase.CALL)
                    say(lines.call(p), Priority.ANNOUNCE)
                    log("Appel : ${p.name}")
                }
            }
        }

        fun advance() {
            val next = s.stepIndex + 1
            if (next < s.steps.size) enterStep(next)
        }

        /** Resets the game to the start of step [index] (MJ "back"), recomputing the progress. */
        fun goToStep(index: Int) {
            interrupt = true
            if (index < 0) {
                s = GameState(phase = Phase.GATHERING, phaseStartedAt = now, introPage = content.intro.lastIndex,
                    selected = s.selected, lastPicks = s.lastPicks)
                say(lines.gatherIntro(), Priority.ANNOUNCE)
                log("MJ : retour au rassemblement")
                return
            }
            val before = s.steps.take(index)
            s = s.copy(
                acquired = before.filter { content.power(it) != null },
                corrupted = null,
                trapDone = GameContent.STEP_TRAP in before,
                finalFailures = 0, finalHintShown = false,
            )
            enterStep(index)
            log("MJ : retour à l'étape ${index + 1}")
        }

        // ---------------------------------------------------------------- intro and gathering

        fun introNext() {
            if (s.phase != Phase.INTRO) return
            interrupt = true
            if (s.introPage < content.intro.lastIndex) {
                s = s.copy(introPage = s.introPage + 1)
                say(lines.intro(s.introPage), Priority.ANNOUNCE)
            } else {
                setPhase(Phase.GATHERING)
                say(lines.gatherIntro(), Priority.ANNOUNCE)
                log("Rassemblement des Gardiennes")
            }
        }

        private fun onScanGathering(r: Role?) {
            val p = (r as? Role.PowerRole)?.let { content.power(it.powerId) }
            if (p == null || p.bonusOnly) return unexpected(r)
            if (p.id in s.selected) return
            s = s.copy(selected = s.selected + p.id)
            interrupt = true
            sound(Sfx.SCAN_OK)
            say(lines.gathered(p), Priority.ACTION)
            log("Rassemblement : ${p.name}")
        }

        fun gatheringDone() {
            if (s.phase != Phase.GATHERING || s.selected.isEmpty()) return
            s = s.copy(steps = content.buildSteps(s.selected), acquired = emptyList(), corrupted = null, trapDone = false)
            log("Mission lancée avec ${s.selected.size} pouvoirs : ${s.steps.joinToString(", ")}")
            say(lines.gatherDone(), Priority.ANNOUNCE)
            interrupt = true
            enterStep(0)
        }

        // ---------------------------------------------------------------- idle messages

        fun startIdle(firstSec: Int) {
            s = s.copy(idle = IdleState(nextAt = now + secs(firstSec)))
        }

        fun stopIdle() {
            s = s.copy(idle = IdleState())
        }

        fun pauseIdle(paused: Boolean) {
            val idle = s.idle
            val next = if (!paused && idle.nextAt != null) maxOf(idle.nextAt, now + secs(settings.idleMinGapSec)) else idle.nextAt
            s = s.copy(idlePaused = paused, idle = idle.copy(nextAt = next))
            log(if (paused) "MJ : messages d'attente en pause" else "MJ : messages d'attente relancés")
        }

        private fun idleTick() {
            if (s.phase !in IDLE_PHASES || s.idlePaused) return
            val idle = s.idle
            val at = idle.nextAt ?: return
            if (now < at || idle.count >= settings.idleMaxMessages) return
            val m = content.messages
            // While the fake badge is being looked for, the Invisibles stay silent (the trap must not be guessed).
            val sophieOnly = s.phase == Phase.TRAP_WAIT && m.idleSophie.isNotEmpty()
            val (line, speaker) = when {
                sophieOnly -> pick("idleSophie", m.idleSophie) to Speaker.SOPHIE
                idle.nextSpeaker == Speaker.SOPHIE && m.idleSophie.isNotEmpty() -> pick("idleSophie", m.idleSophie) to Speaker.SOPHIE
                idle.nextSpeaker == Speaker.INVISIBLES && m.idleInvisibles.isNotEmpty() -> pick("idleInvisibles", m.idleInvisibles) to Speaker.INVISIBLES
                m.idleSophie.isNotEmpty() -> pick("idleSophie", m.idleSophie) to Speaker.SOPHIE
                else -> pick("idleInvisibles", m.idleInvisibles) to Speaker.INVISIBLES
            }
            say(line, Priority.IDLE)
            val gap = maxOf(settings.idleRepeatEverySec, settings.idleMinGapSec)
            s = s.copy(
                idle = IdleState(
                    nextAt = now + secs(gap),
                    count = idle.count + 1,
                    nextSpeaker = if (speaker == Speaker.SOPHIE) Speaker.INVISIBLES else Speaker.SOPHIE,
                )
            )
        }

        // ---------------------------------------------------------------- timers

        fun tick() {
            val until = s.phaseUntil
            if (until != null && now >= until) {
                when (s.phase) {
                    Phase.LEVEL_UP -> endLevelUp()
                    Phase.REWARD -> advance()
                    Phase.CORRUPTION, Phase.ECHO_PAUSE, Phase.ECHO_PLAYBACK -> repairStrategy.onTimer(this)
                    Phase.RESTORED -> endRestored()
                    // After the lockout the echo is replayed.
                    Phase.FINAL_LOCKOUT -> { setPhase(Phase.FINAL_INPUT); s = s.copy(finalScans = emptyList(), finalPlaybackAt = now) }
                    else -> s = s.copy(phaseUntil = null)
                }
            }
            idleTick()
        }

        // ---------------------------------------------------------------- scans

        fun scan(role: Role?) {
            val r = if (role is Role.PowerRole && content.power(role.powerId) == null) null else role
            log("Scan : ${r?.key ?: "inconnu"} (${s.phase})")
            if (s.bonusPower != null) {
                // Bonus trials are driven by buttons; pendants are ignored.
                if (r !is Role.PowerRole) unexpected(r)
                return
            }
            when (s.phase) {
                Phase.GATHERING -> onScanGathering(r)
                Phase.CALL -> onScanCall(r)
                Phase.WAITING -> onScanWaiting(r)
                Phase.TRAP_WAIT -> if (r == Role.FakeBadge) repairStrategy.corrupt(this) else unexpected(r)
                Phase.ECHO_INPUT -> if (r is Role.PowerRole) repairStrategy.onPendant(this, r.powerId) else unexpected(r)
                Phase.FINAL_INPUT -> if (r is Role.PowerRole) onFinalPendant(r.powerId) else unexpected(r)
                Phase.FINAL_LOCKOUT -> if (r is Role.PowerRole) say(lines.finalLockout(), Priority.ACTION) else unexpected(r)
                Phase.HEART -> if (r == Role.HeartBadge) startFinale() else if (r !is Role.PowerRole) unexpected(r)
                // Silent phases: pendants are ignored, only badges and unknown tags get a reaction.
                Phase.INTRO, Phase.CODE_ENTRY, Phase.SHOUT, Phase.LEVEL_UP, Phase.REWARD, Phase.CORRUPTION, Phase.ECHO_INTRO,
                Phase.ECHO_PAUSE, Phase.ECHO_PLAYBACK, Phase.RESTORED, Phase.FINALE, Phase.BONUS_MENU ->
                    if (r !is Role.PowerRole) unexpected(r)
            }
        }

        private fun unexpected(r: Role?) {
            val m = content.messages
            when (r) {
                null -> say(pick("unknownTag", m.unknownTag), Priority.ACTION)
                Role.FakeBadge -> say(pick("fakeBadgeTooEarly", m.fakeBadgeTooEarly), Priority.ACTION)
                Role.HeartBadge -> say(pick("heartTooEarly", m.heartTooEarly), Priority.ACTION)
                is Role.PowerRole -> say(pick("wrongPendant", m.wrongPendant), Priority.ACTION)
            }
            sound(Sfx.ERROR)
        }

        private fun onScanCall(r: Role?) {
            val p = engine.currentPower(s) ?: return
            if (r !is Role.PowerRole || !(r.powerId == p.id || s.jokerAllowed)) return unexpected(r)
            s = s.copy(activePendant = r.powerId, firstScanAt = now)
            sound(Sfx.SCAN_OK)
            say(lines.consigne(p), Priority.ACTION)
            setPhase(if (p.isShout) Phase.SHOUT else Phase.WAITING)
            startIdle(p.idleFirstAfterSec ?: settings.idleFirstAfterSec)
            if (r.powerId != p.id) log("Joker : pendentif ${r.powerId} accepté pour ${p.id}")
        }

        private fun onScanWaiting(r: Role?) {
            val p = engine.currentPower(s) ?: return
            if (r !is Role.PowerRole || (r.powerId != s.activePendant && r.powerId != p.id)) return unexpected(r)
            val first = s.firstScanAt ?: now
            if (now - first < secs(settings.minSecondScanDelaySec)) {
                say(pick("tooFastSecondScan", content.messages.tooFastSecondScan), Priority.ACTION)
                return
            }
            stopIdle()
            setPhase(Phase.CODE_ENTRY)
            sound(Sfx.SCAN_OK)
        }

        // ---------------------------------------------------------------- code and shout

        fun submitCode(code: String) {
            if (s.phase != Phase.CODE_ENTRY) return
            val p = engine.currentPower(s) ?: return
            val cooldown = s.codeCooldownUntil
            if (cooldown != null && now < cooldown) return
            if (CodeNormalizer.matches(code, p.answers)) {
                log("Code juste : ${p.name}")
                levelUp(p.id)
                return
            }
            val errors = s.codeErrors + 1
            log("Code faux ($errors) : ${p.name}")
            s = s.copy(codeErrors = errors)
            sound(Sfx.ERROR)
            // Not a reaction to every error: at most one every wrongCodeMsgGapSec, alternating Invisibles / Sophie.
            val last = s.lastWrongCodeMsgAt
            if (last == null || now - last >= secs(settings.wrongCodeMsgGapSec)) {
                val m = content.messages
                val sophie = s.wrongCodeMsgs % 2 == 1 && m.wrongCodeSophie.isNotEmpty()
                say(if (sophie) pick("wrongCodeSophie", m.wrongCodeSophie) else pick("wrongCode", m.wrongCode), Priority.ACTION)
                s = s.copy(lastWrongCodeMsgAt = now, wrongCodeMsgs = s.wrongCodeMsgs + 1)
            }
            if (!s.hintShown && errors >= settings.codeAttemptsBeforeHint) {
                s = s.copy(hintShown = true)
                say(lines.hint(p), Priority.ACTION)
            }
            if (s.hintShown) s = s.copy(codeCooldownUntil = now + secs(settings.codeCooldownAfterHintSec))
        }

        fun shoutDone() {
            if (s.phase != Phase.SHOUT) return
            val p = engine.currentPower(s) ?: return
            log("Cri-mètre réussi : ${p.name}")
            levelUp(p.id)
        }

        fun levelUp(powerId: String) {
            stopIdle()
            s = if (s.bonusPower != null) {
                s.copy(bonusDone = s.bonusDone - powerId + powerId, animPower = powerId, codeCooldownUntil = null)
            } else {
                s.copy(acquired = s.acquired - powerId + powerId, animPower = powerId, codeCooldownUntil = null)
            }
            setPhase(Phase.LEVEL_UP, now + secs(settings.levelUpSec))
            sound(Sfx.LEVEL_UP)
            say(pick("codeOk", content.messages.codeOk), Priority.ACTION)
        }

        private fun endLevelUp() {
            val p = engine.currentPower(s)
            if (p != null) {
                say(lines.success(p), Priority.ACTION)
                say(lines.react(p) ?: pick("invisiblesAfterSuccess", content.messages.invisiblesAfterSuccess), Priority.ACTION)
            }
            if (s.bonusPower != null) {
                s = s.copy(bonusPower = null)
                resetTrial()
                setPhase(Phase.BONUS_MENU)
                return
            }
            if (p != null) say(lines.card(p, content.cardNumber(s.selected, p.id)), Priority.ACTION)
            s = s.copy(rewardShowsCard = true)
            setPhase(Phase.REWARD, now + secs(settings.rewardScreenSec))
        }

        // ---------------------------------------------------------------- trap (shared helpers for strategies)

        fun chooseCorrupted(): String? {
            val acquired = s.acquired
            if (acquired.isEmpty()) return null
            return when (val cp = content.trap.corruptedPower) {
                "last_acquired" -> acquired.last()
                "random" -> acquired[random.nextInt(acquired.size)]
                else -> if (cp in acquired) cp else acquired.last().also { log("trap.corruptedPower $cp pas encore acquis : dernier pouvoir utilisé") }
            }
        }

        fun restore() {
            stopIdle()
            val c = s.corrupted
            s = s.copy(
                acquired = if (c != null) s.acquired - c + c else s.acquired,
                corrupted = null, trapDone = true, echo = null, animPower = c,
            )
            setPhase(Phase.RESTORED, now + secs(settings.levelUpSec))
            sound(Sfx.LEVEL_UP)
            log("Pouvoir restauré : ${c ?: "-"}")
        }

        private fun endRestored() {
            say(lines.trapRestoredSophie(), Priority.ACTION)
            say(lines.trapRestoredInv(), Priority.ACTION)
            // Let the dialogue finish before the next call.
            s = s.copy(rewardShowsCard = false)
            setPhase(Phase.REWARD, now + secs(settings.rewardScreenSec))
        }

        /** The reward screen ends when the dialogues are over (after a minimum time), or at its maximum time. */
        fun messagesDone() {
            if (s.phase == Phase.REWARD) {
                if (now - s.phaseStartedAt >= secs(settings.rewardMinSec)) advance()
                return
            }
            // Final sequence: the echo starts once Sophie has finished her announcements.
            if (s.phase == Phase.FINAL_INPUT && s.finalPlaybackAt == null && content.final.mode == Final.MODE_ECHO) finalReplay()
        }

        /** Replays the final echo (the powers in the order they were awakened); pendants laid so far are cleared. */
        fun finalReplay() {
            if (s.phase != Phase.FINAL_INPUT) return
            s = s.copy(finalPlaybackAt = now, finalScans = emptyList())
            log("Écho final")
        }

        /** True while the final echo is being played (pendants are ignored). */
        fun finalPlaying(): Boolean {
            val at = s.finalPlaybackAt ?: return false
            return now < at + engine.finalOrder(s).size * content.final.echoStepMs.toLong() + 400
        }

        fun random(): Random = random

        // ---------------------------------------------------------------- final

        private fun onFinalPendant(id: String) {
            val order = engine.finalOrder(s)
            // Pendants of powers not in this game, pendants already laid, and scans during the echo are ignored.
            if (id !in order || id in s.finalScans || finalPlaying()) return
            val scans = s.finalScans + id
            s = s.copy(finalScans = scans)
            sound(Sfx.NEUTRAL)
            if (scans.size < order.size) return
            if (scans == order) {
                finalSuccess()
            } else {
                val failures = s.finalFailures + 1
                log("Ordre final faux ($failures)")
                s = s.copy(finalFailures = failures)
                sound(Sfx.ERROR)
                say(pick("final_wrong", lines.finalWrongList()), Priority.ACTION)
                say(lines.finalLockout(), Priority.ACTION)
                if (failures >= content.final.hintAfterFailures && !s.finalHintShown) {
                    s = s.copy(finalHintShown = true)
                    say(lines.finalHint(s.selected), Priority.ACTION)
                }
                setPhase(Phase.FINAL_LOCKOUT, now + secs(content.final.lockoutSec))
            }
        }

        private fun finalSuccess() {
            log("Ordre final juste")
            s = s.copy(finalScans = engine.finalOrder(s))
            setPhase(Phase.HEART)
            sound(Sfx.LEVEL_UP)
            say(lines.finalOkSophie(), Priority.ACTION)
            say(lines.finalOkInv(), Priority.ACTION)
        }

        private fun startFinale() {
            log("Cœur scanné : finale")
            setPhase(Phase.FINALE, now + secs(settings.finaleSec))
            sound(Sfx.FINALE)
            lines.finale().forEach { say(it, Priority.ACTION) }
        }

        // ---------------------------------------------------------------- bonus

        fun openBonus() {
            if (s.phase != Phase.FINALE && s.phase != Phase.BONUS_MENU) return
            if (s.phase == Phase.FINALE) say(lines.bonusIntro(), Priority.ANNOUNCE)
            s = s.copy(bonusPower = null)
            setPhase(Phase.BONUS_MENU)
            log("Épreuves bonus")
        }

        fun startBonus(id: String) {
            if (s.phase != Phase.BONUS_MENU) return
            val p = content.power(id) ?: return
            if (p.id in s.selected) return
            resetTrial()
            s = s.copy(bonusPower = p.id, activePendant = p.id, firstScanAt = now)
            say(lines.consigne(p), Priority.ACTION)
            setPhase(if (p.isShout) Phase.SHOUT else Phase.WAITING)
            log("Bonus : ${p.name}")
        }

        fun bonusEnterCode() {
            if (s.bonusPower == null || s.phase != Phase.WAITING) return
            setPhase(Phase.CODE_ENTRY)
        }

        fun bonusBack() {
            if (s.bonusPower == null) return
            s = s.copy(bonusPower = null)
            resetTrial()
            setPhase(Phase.BONUS_MENU)
        }

        // ---------------------------------------------------------------- game master

        fun mjForceSuccess() {
            log("MJ : succès forcé (${s.phase})")
            when (s.phase) {
                Phase.INTRO -> { interrupt = true; setPhase(Phase.GATHERING); say(lines.gatherIntro(), Priority.ANNOUNCE) }
                Phase.GATHERING -> gatheringDone()
                Phase.CALL, Phase.WAITING, Phase.CODE_ENTRY, Phase.SHOUT -> engine.currentPower(s)?.let { levelUp(it.id) }
                Phase.LEVEL_UP -> endLevelUp()
                Phase.REWARD -> advance()
                Phase.TRAP_WAIT -> { s = s.copy(trapDone = true); stopIdle(); advance() }
                Phase.CORRUPTION, Phase.ECHO_INTRO, Phase.ECHO_PAUSE, Phase.ECHO_PLAYBACK, Phase.ECHO_INPUT -> restore()
                Phase.RESTORED -> endRestored()
                Phase.FINAL_INPUT, Phase.FINAL_LOCKOUT -> finalSuccess()
                Phase.HEART -> startFinale()
                Phase.FINALE, Phase.BONUS_MENU -> Unit
            }
        }

        fun mjForceCode() {
            if (s.phase == Phase.CODE_ENTRY || s.phase == Phase.WAITING || s.phase == Phase.SHOUT) {
                log("MJ : code forcé")
                engine.currentPower(s)?.let { levelUp(it.id) }
            }
        }

        fun mjAllowJoker() {
            if (s.phase in setOf(Phase.CALL, Phase.WAITING, Phase.CODE_ENTRY)) {
                s = s.copy(jokerAllowed = true)
                log("MJ : joker autorisé")
            }
        }

        fun mjSendHint() {
            log("MJ : indice envoyé")
            when (s.phase) {
                Phase.CALL, Phase.WAITING, Phase.CODE_ENTRY, Phase.SHOUT -> engine.currentPower(s)?.let {
                    s = s.copy(hintShown = true)
                    say(lines.hint(it), Priority.ACTION)
                }
                Phase.FINAL_INPUT, Phase.FINAL_LOCKOUT -> {
                    s = s.copy(finalHintShown = true)
                    say(lines.finalHint(s.selected), Priority.ACTION)
                }
                Phase.ECHO_INTRO, Phase.ECHO_PAUSE, Phase.ECHO_PLAYBACK, Phase.ECHO_INPUT -> say(lines.trapHelp(), Priority.ACTION)
                Phase.TRAP_WAIT -> say(lines.trapAnnounce(), Priority.ACTION)
                Phase.HEART -> say(lines.finalOkSophie(), Priority.ACTION)
                Phase.GATHERING -> say(lines.gatherIntro(), Priority.ACTION)
                else -> Unit
            }
        }

        fun mjSay(speaker: Speaker, text: String?) {
            if (text != null) {
                messages += Message("mj_free", speaker, text, Priority.ACTION)
            } else {
                val m = content.messages
                say(
                    if (speaker == Speaker.SOPHIE) pick("idleSophie", m.idleSophie) else pick("idleInvisibles", m.idleInvisibles),
                    Priority.ACTION,
                )
            }
            log("MJ : message ${if (speaker == Speaker.SOPHIE) "de Sophie" else "des Invisibles"}")
        }

        fun mjValidateRepair() {
            when (s.phase) {
                Phase.TRAP_WAIT -> { log("MJ : piège passé"); s = s.copy(trapDone = true); stopIdle(); advance() }
                Phase.CORRUPTION, Phase.ECHO_INTRO, Phase.ECHO_PAUSE, Phase.ECHO_PLAYBACK, Phase.ECHO_INPUT -> {
                    log("MJ : réparation validée")
                    MjValidationStrategy.validate(this)
                }
                else -> Unit
            }
        }

        fun mjBack() {
            if (s.bonusPower != null) return bonusBack()
            when (s.phase) {
                Phase.INTRO -> if (s.introPage > 0) {
                    s = s.copy(introPage = s.introPage - 1)
                    say(lines.intro(s.introPage), Priority.ANNOUNCE)
                    interrupt = true
                }
                Phase.GATHERING -> if (s.selected.isNotEmpty()) {
                    log("MJ : pendentif retiré (${s.selected.last()})")
                    s = s.copy(selected = s.selected.dropLast(1))
                } else {
                    s = s.copy(introPage = content.intro.lastIndex)
                    setPhase(Phase.INTRO)
                    say(lines.intro(s.introPage), Priority.ANNOUNCE)
                    interrupt = true
                }
                Phase.CALL, Phase.TRAP_WAIT -> goToStep(s.stepIndex - 1)
                Phase.FINAL_INPUT -> if (s.finalScans.isEmpty()) goToStep(s.stepIndex - 1) else goToStep(s.stepIndex)
                Phase.BONUS_MENU -> { setPhase(Phase.FINALE); s = s.copy(phaseUntil = null) }
                else -> goToStep(s.stepIndex)
            }
        }
    }

    companion object {
        val IDLE_PHASES = setOf(Phase.WAITING, Phase.SHOUT, Phase.TRAP_WAIT, Phase.ECHO_INTRO, Phase.ECHO_INPUT)
        val PRE_GAME_PHASES = setOf(Phase.INTRO, Phase.GATHERING)
    }
}
