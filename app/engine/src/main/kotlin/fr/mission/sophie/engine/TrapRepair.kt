package fr.mission.sophie.engine

/**
 * How the corrupted power gets repaired after the trap (spec 8.4).
 * Corruption itself is common to every strategy; the repair phases use the ECHO_* phases.
 */
abstract class TrapRepairStrategy {

    /** Fake badge scanned at the right time. */
    fun corrupt(tx: GameEngine.Tx) {
        val lost = tx.chooseCorrupted()
        tx.stopIdle()
        tx.s = tx.s.copy(
            corrupted = lost,
            acquired = if (lost != null) tx.s.acquired - lost else tx.s.acquired,
            animPower = lost,
        )
        tx.setPhase(Phase.CORRUPTION, tx.now + tx.secs(tx.content.settings.corruptionSec))
        tx.sound(Sfx.CORRUPTION)
        tx.say(tx.engine.lines.trapCorruptInv(), Priority.ACTION)
        tx.say(tx.engine.lines.trapCorruptSophie(), Priority.ACTION)
        tx.log("Piège déclenché, pouvoir corrompu : ${lost ?: "-"}")
    }

    /** A timed phase ended (CORRUPTION, ECHO_PAUSE, ECHO_PLAYBACK). */
    open fun onTimer(tx: GameEngine.Tx) {
        if (tx.s.phase == Phase.CORRUPTION) {
            tx.setPhase(Phase.ECHO_INTRO)
            tx.say(tx.engine.lines.trapRepairIntro(), Priority.ANNOUNCE)
            tx.startIdle(tx.content.settings.idleFirstAfterSec)
        }
    }

    /** "Start" button on the echo intro screen. */
    open fun start(tx: GameEngine.Tx) {}

    open fun onPendant(tx: GameEngine.Tx, powerId: String) {}

    open fun replay(tx: GameEngine.Tx) {}

    companion object {
        fun forContent(c: GameContent): TrapRepairStrategy =
            if (c.trap.repair.type == "simon") SimonStrategy() else MjValidationStrategy
    }
}

/** Fallback: only the game master can validate the repair from the hidden menu. */
object MjValidationStrategy : TrapRepairStrategy() {
    fun validate(tx: GameEngine.Tx) = tx.restore()
}

/** "Écho des pouvoirs": a Simon game played with the pendants. */
class SimonStrategy : TrapRepairStrategy() {

    override fun start(tx: GameEngine.Tx) {
        startRound(tx, 0, failures = 0, slow = false)
    }

    private fun startRound(tx: GameEngine.Tx, round: Int, failures: Int, slow: Boolean) {
        val repair = tx.content.trap.repair
        // Never more crystals than pendants in play.
        val size = minOf(repair.rounds[round], tx.echoPool().size)
        val isLast = round == repair.rounds.lastIndex
        val seq = drawSequence(tx, size, mustInclude = if (isLast && repair.lastRoundIncludesCorrupted) tx.s.corrupted else null)
        tx.s = tx.s.copy(
            echo = EchoState(
                round = round, sequence = seq, position = 0, replaysLeft = repair.replaysPerRound,
                failures = failures, slow = slow, stepMs = stepMs(repair, slow),
            )
        )
        tx.say(tx.engine.lines.trapRound(round), Priority.ANNOUNCE)
        tx.setPhase(Phase.ECHO_PAUSE, tx.now + ROUND_INTRO_PAUSE_MS)
        tx.startIdle(tx.content.settings.idleFirstAfterSec)
        tx.log("Écho : manche ${round + 1} (${seq.size} pouvoirs)")
    }

    private fun stepMs(repair: Repair, slow: Boolean) = if (slow) (repair.stepDurationMs * 1.5).toInt() else repair.stepDurationMs

    private fun drawSequence(tx: GameEngine.Tx, size: Int, mustInclude: String?): List<String> {
        val rnd = tx.random()
        val seq = tx.echoPool().shuffled(rnd).take(size).toMutableList()
        if (mustInclude != null && mustInclude !in seq) seq[rnd.nextInt(seq.size)] = mustInclude
        return seq
    }

    override fun onTimer(tx: GameEngine.Tx) {
        val echo = tx.s.echo
        when (tx.s.phase) {
            Phase.ECHO_PAUSE -> if (echo != null) startPlayback(tx, echo)
            Phase.ECHO_PLAYBACK -> tx.setPhase(Phase.ECHO_INPUT)
            else -> super.onTimer(tx)
        }
    }

    private fun startPlayback(tx: GameEngine.Tx, echo: EchoState) {
        tx.s = tx.s.copy(echo = echo.copy(position = 0, playbackStartAt = tx.now))
        tx.setPhase(Phase.ECHO_PLAYBACK, tx.now + echo.sequence.size * echo.stepMs + PLAYBACK_TAIL_MS)
    }

    override fun replay(tx: GameEngine.Tx) {
        val echo = tx.s.echo ?: return
        if (tx.s.phase != Phase.ECHO_INPUT || echo.replaysLeft <= 0) return
        tx.s = tx.s.copy(echo = echo.copy(replaysLeft = echo.replaysLeft - 1))
        startPlayback(tx, tx.s.echo!!)
    }

    override fun onPendant(tx: GameEngine.Tx, powerId: String) {
        val echo = tx.s.echo ?: return
        val repair = tx.content.trap.repair
        // The pendant just validated, read a second time (left on the phone): ignored, not an error.
        if (echo.position > 0 && echo.sequence[echo.position - 1] == powerId) return
        if (echo.sequence[echo.position] == powerId) {
            val pos = echo.position + 1
            tx.sound(Sfx.STEP_OK)
            if (pos < echo.sequence.size) {
                tx.s = tx.s.copy(echo = echo.copy(position = pos))
                return
            }
            tx.s = tx.s.copy(echo = echo.copy(position = pos))
            if (echo.round < repair.rounds.lastIndex) {
                tx.say(tx.engine.lines.trapRoundOk(echo.round), Priority.ACTION)
                startRound(tx, echo.round + 1, echo.failures, echo.slow)
            } else {
                tx.restore()
            }
            return
        }
        // Wrong pendant: same sequence replayed from the start.
        val failures = echo.failures + 1
        tx.sound(Sfx.ERROR)
        tx.say(tx.pick("trap_wrong", tx.engine.lines.trapWrongList()), Priority.ACTION)
        var slow = echo.slow
        if (!slow && failures >= repair.maxFailuresBeforeHelp) {
            slow = true
            tx.say(tx.engine.lines.trapHelp(), Priority.ACTION)
            tx.log("Écho : coup de pouce (séquence ralentie)")
        }
        tx.s = tx.s.copy(echo = echo.copy(position = 0, failures = failures, slow = slow, stepMs = stepMs(repair, slow)))
        tx.setPhase(Phase.ECHO_PAUSE, tx.now + ERROR_PAUSE_MS)
        tx.log("Écho : erreur ($failures)")
    }

    companion object {
        const val ROUND_INTRO_PAUSE_MS = 3500L
        const val ERROR_PAUSE_MS = 2500L
        const val PLAYBACK_TAIL_MS = 400L
    }
}
