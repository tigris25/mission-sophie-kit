package fr.mission.sophie.engine

import kotlinx.serialization.Serializable

@Serializable
enum class Phase {
    /** G0: Sophie's introduction pages. */
    INTRO,
    /** Each girl scans her pendant: the chosen powers define the game. */
    GATHERING,
    /** G1: Sophie calls a power, waiting for scan 1. */
    CALL,
    /** G2: title and instructions shown, waiting for scan 2. */
    WAITING,
    /** G3: code keyboard. */
    CODE_ENTRY,
    /** Shout meter (kind "shout"): the microphone validates the trial. */
    SHOUT,
    /** G4: level-up animation. */
    LEVEL_UP,
    /** After the level-up: messages and "go get your card" reminder. */
    REWARD,
    /** G5: trap announced, waiting for the fake badge. */
    TRAP_WAIT,
    /** Corruption animation. */
    CORRUPTION,
    /** Echo explanation, waiting for the "start" button. */
    ECHO_INTRO,
    /** Short pause before a playback (after round intro or an error). */
    ECHO_PAUSE,
    /** The app lights the sequence. */
    ECHO_PLAYBACK,
    /** Girls reproduce the sequence with their pendants. */
    ECHO_INPUT,
    /** Corrupted power restored (level-up animation). */
    RESTORED,
    /** G6: final sequence, 8 scans without feedback. */
    FINAL_INPUT,
    /** Wrong final order: pendants "recharging". */
    FINAL_LOCKOUT,
    /** G7: heart location revealed, waiting for the heart badge. */
    HEART,
    /** G8: victory. */
    FINALE,
    /** After the finale: list of bonus trials (powers not chosen). */
    BONUS_MENU,
}

@Serializable
data class IdleState(
    val nextAt: Long? = null,
    val count: Int = 0,
    val nextSpeaker: Speaker = Speaker.SOPHIE,
)

@Serializable
data class EchoState(
    val round: Int = 0,
    val sequence: List<String> = emptyList(),
    val position: Int = 0,
    val replaysLeft: Int = 0,
    val failures: Int = 0,
    val slow: Boolean = false,
    val stepMs: Int = 1200,
    val playbackStartAt: Long = 0,
)

@Serializable
data class GameState(
    val phase: Phase = Phase.INTRO,
    val phaseStartedAt: Long = 0,
    /** Deadline of timed phases (animations, pauses, lockout). */
    val phaseUntil: Long? = null,
    val introPage: Int = 0,
    /** Powers chosen at the gathering, in scan order. */
    val selected: List<String> = emptyList(),
    /** Play order built at the end of the gathering (power ids, "@trap", "@final"). */
    val steps: List<String> = emptyList(),
    /** Index in [steps]. */
    val stepIndex: Int = 0,
    /** Powers currently lit, in acquisition order. */
    val acquired: List<String> = emptyList(),
    val corrupted: String? = null,
    val trapDone: Boolean = false,
    /** Pendant used for scan 1 (may differ from the step's power with a joker). */
    val activePendant: String? = null,
    val firstScanAt: Long? = null,
    val codeErrors: Int = 0,
    val hintShown: Boolean = false,
    val codeCooldownUntil: Long? = null,
    /** Time of the last reaction to a wrong code, and how many were said (to alternate speakers). */
    val lastWrongCodeMsgAt: Long? = null,
    val wrongCodeMsgs: Int = 0,
    val jokerAllowed: Boolean = false,
    val idle: IdleState = IdleState(),
    val idlePaused: Boolean = false,
    val echo: EchoState? = null,
    val finalScans: List<String> = emptyList(),
    val finalFailures: Int = 0,
    val finalHintShown: Boolean = false,
    /** Start of the final echo playback (null = not played yet, waits for the announcements to end). */
    val finalPlaybackAt: Long? = null,
    /** Power shown by the current level-up / restored / corruption animation. */
    val animPower: String? = null,
    /** Bonus trial being played after the finale (null = main game). */
    val bonusPower: String? = null,
    /** Reward screen after a trial (card reminder) or after the trap repair (no card). */
    val rewardShowsCard: Boolean = true,
    val bonusDone: List<String> = emptyList(),
    /** Last index drawn per message list, to avoid immediate repetition. */
    val lastPicks: Map<String, Int> = emptyMap(),
)

sealed interface GameEvent {
    /** A tag was read. [role] is null for an unknown tag. */
    data class Scan(val role: Role?) : GameEvent
    data object Tick : GameEvent
    data object IntroNext : GameEvent
    /** Every queued message has been shown (and voiced): the reward screen may move on. */
    data object MessagesDone : GameEvent
    /** End of the gathering ("all the Guardians are here"). */
    data object GatheringDone : GameEvent
    /** The shout meter was held long enough. */
    data object ShoutDone : GameEvent
    data object OpenBonus : GameEvent
    data class StartBonus(val powerId: String) : GameEvent
    /** Bonus trial: no second scan, a button opens the code entry. */
    data object BonusEnterCode : GameEvent
    data object BonusBack : GameEvent
    /** "Watch the echo again" button of the final sequence. */
    data object FinalReplay : GameEvent
    data class SubmitCode(val code: String) : GameEvent
    data object StartEcho : GameEvent
    data object ReplayEcho : GameEvent

    // Game master (hidden menu)
    data object MjForceSuccess : GameEvent
    data object MjForceCode : GameEvent
    data object MjAllowJoker : GameEvent
    data object MjSendHint : GameEvent
    /** [text] null = random idle line of that speaker. */
    data class MjSay(val speaker: Speaker, val text: String? = null) : GameEvent
    data class MjPauseIdle(val paused: Boolean) : GameEvent
    data object MjValidateRepair : GameEvent
    data object MjBack : GameEvent
}

data class Output(
    val state: GameState,
    val messages: List<Message> = emptyList(),
    val sfx: List<Sfx> = emptyList(),
    /** Human-readable journal lines (French, never containing codes). */
    val log: List<String> = emptyList(),
    /** True when the intro message should replace whatever is playing. */
    val interrupt: Boolean = false,
)
