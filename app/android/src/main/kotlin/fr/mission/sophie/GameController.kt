package fr.mission.sophie

import android.content.Context
import fr.mission.sophie.audio.AudioEngine
import fr.mission.sophie.data.AppSettings
import fr.mission.sophie.data.LogEntry
import fr.mission.sophie.data.MediaRepo
import fr.mission.sophie.data.Stores
import fr.mission.sophie.data.TagBindings
import fr.mission.sophie.engine.ContentLoadResult
import fr.mission.sophie.engine.GameContent
import fr.mission.sophie.engine.GameEngine
import fr.mission.sophie.engine.GameEvent
import fr.mission.sophie.engine.GameState
import fr.mission.sophie.engine.Message
import fr.mission.sophie.engine.MessageQueue
import fr.mission.sophie.engine.Output
import fr.mission.sophie.engine.Phase
import fr.mission.sophie.engine.Role
import fr.mission.sophie.nfc.NfcStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

data class ContentInfo(
    val raw: GameContent,
    val imported: Boolean,
    val warnings: List<String>,
    val variants: List<fr.mission.sophie.engine.Variant> = emptyList(),
    val variant: String? = null,
    val params: List<fr.mission.sophie.engine.Param> = emptyList(),
)

private fun ContentLoadResult.info(imported: Boolean) = ContentInfo(content, imported, warnings, variants, variant, params)

data class ScanInfo(val time: Long, val uid: String, val roleKey: String?, val simulated: Boolean)

/** App-wide controller: routes scans, runs the engine, persists everything, drives audio. Main thread only. */
class GameController(private val context: Context) {
    val stores = Stores(context)
    val media = MediaRepo(context, stores)
    val audio = AudioEngine(context, media)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val io = Executors.newSingleThreadExecutor()

    private val _settings = MutableStateFlow(stores.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _bindings = MutableStateFlow(stores.loadBindings())
    val bindings: StateFlow<TagBindings> = _bindings.asStateFlow()

    private val _content: MutableStateFlow<ContentInfo>
    val content: StateFlow<ContentInfo>

    var engine: GameEngine
        private set

    private val _game = MutableStateFlow<GameState?>(null)
    /** Running game, null when none is loaded. */
    val game: StateFlow<GameState?> = _game.asStateFlow()

    private val queue = MessageQueue({ m -> media.audioDurationMs(m.voiceId) ?: audio.estimateTtsMs(m.text) })
    private val _bubble = MutableStateFlow<MessageQueue.Playing?>(null)
    val bubble: StateFlow<MessageQueue.Playing?> = _bubble.asStateFlow()

    private val _lastScan = MutableStateFlow<ScanInfo?>(null)
    val lastScan: StateFlow<ScanInfo?> = _lastScan.asStateFlow()

    private val _nfcStatus = MutableStateFlow(NfcStatus.OK)
    val nfcStatus: StateFlow<NfcStatus> = _nfcStatus.asStateFlow()

    /** Admin binding: the next scan is given to this callback instead of the game. */
    var captureNextScan: ((String) -> Unit)? = null

    /** True while the game screen is shown: only then scans go to the engine. */
    var gameVisible = false

    init {
        val (loaded, imported) = stores.loadContent(_settings.value.variant, _settings.value.params)
        val info = loaded.info(imported)
        _content = MutableStateFlow(info)
        content = _content.asStateFlow()
        engine = GameEngine(_settings.value.applyTo(info.raw))
        loaded.warnings.forEach { log("contenu", it) }
        applyAudioSettings(_settings.value)
        scope.launch {
            while (true) {
                delay(200)
                tick()
            }
        }
    }

    fun setNfcStatus(s: NfcStatus) { _nfcStatus.value = s }

    // ------------------------------------------------------------------ journal

    fun log(kind: String, text: String) {
        val e = LogEntry(System.currentTimeMillis(), kind, text)
        io.execute { stores.appendLog(e) }
    }

    // ------------------------------------------------------------------ scans

    fun onUid(uid: String, simulated: Boolean = false) {
        val roleKey = _bindings.value.roleOf(uid)
        _lastScan.value = ScanInfo(System.currentTimeMillis(), uid, roleKey, simulated)
        captureNextScan?.let { cb ->
            captureNextScan = null
            log("scan", "…${uid.takeLast(4)} → liaison")
            cb(uid)
            return
        }
        val routed = gameVisible && _game.value != null
        log("scan", "…${uid.takeLast(4)} ${roleKey ?: "inconnu"}${if (simulated) " (simulé)" else ""}${if (routed) "" else " (hors jeu)"}")
        if (routed) dispatch(GameEvent.Scan(roleKey?.let { Role.parse(it) }))
    }

    /** Simulator: acts as if the tag bound to [roleKey] was read (or a virtual tag when none is bound). */
    fun simulate(roleKey: String?) {
        val uid = roleKey?.let { _bindings.value.uidFor(it) } ?: roleKey?.let { "SIM-${it.uppercase()}" } ?: "SIM-INCONNU"
        if (roleKey != null && _bindings.value.roleOf(uid) == null && captureNextScan == null) {
            // Unbound role: inject the role directly so the whole game can be tested without any tag.
            _lastScan.value = ScanInfo(System.currentTimeMillis(), uid, roleKey, true)
            log("scan", "$roleKey (simulé, non lié)")
            if (gameVisible && _game.value != null) dispatch(GameEvent.Scan(Role.parse(roleKey)))
            return
        }
        onUid(uid, simulated = true)
    }

    // ------------------------------------------------------------------ bindings

    fun bind(uid: String, roleKey: String) {
        val b = _bindings.value.bind(uid, roleKey)
        _bindings.value = b
        stores.saveBindings(b)
        log("admin", "Tag …${uid.takeLast(4)} lié à $roleKey")
    }

    fun unbind(roleKey: String) {
        val b = _bindings.value.unbind(roleKey)
        _bindings.value = b
        stores.saveBindings(b)
    }

    fun replaceBindings(b: TagBindings) {
        _bindings.value = b
        stores.saveBindings(b)
        log("admin", "Liaisons importées (${b.byUid.size})")
    }

    // ------------------------------------------------------------------ settings & content

    fun updateSettings(f: (AppSettings) -> AppSettings) {
        val old = _settings.value
        val s = f(old)
        _settings.value = s
        stores.saveSettings(s)
        applyAudioSettings(s)
        if (s.variant != old.variant || s.params != old.params) {
            // Weather switch or new name/places: reload the texts (the game in progress keeps going).
            val (loaded, imported) = stores.loadContent(s.variant, s.params)
            setContent(loaded.info(imported))
            if (s.variant != old.variant) log("admin", "Version des lieux : ${loaded.variant}")
        } else {
            engine = GameEngine(s.applyTo(_content.value.raw))
        }
    }

    private fun applyAudioSettings(s: AppSettings) {
        audio.voiceVolume = if (s.muted) 0f else s.voiceVolume
        audio.musicVolume = if (s.muted) 0f else s.musicVolume
        audio.sfxVolume = if (s.muted) 0f else s.sfxVolume
        audio.musicEnabled = s.musicEnabled && !s.muted
        audio.ttsFallback = s.ttsFallback && !s.muted
    }

    /** Throws ContentException with a precise message; the previous content stays active. */
    fun importContent(text: String): ContentLoadResult {
        val r = stores.importContent(text, _settings.value.variant, _settings.value.params)
        setContent(r.info(true))
        log("admin", "Contenu importé (version ${r.content.version})")
        r.warnings.forEach { log("contenu", it) }
        return r
    }

    fun resetContent() {
        stores.resetContent()
        val (loaded, imported) = stores.loadContent(_settings.value.variant, _settings.value.params)
        setContent(loaded.info(imported))
    }

    private fun setContent(info: ContentInfo) {
        _content.value = info
        engine = GameEngine(_settings.value.applyTo(info.raw))
        _game.value?.let { g -> if (!compatible(g)) _game.value = null.also { stores.clearGame() } }
    }

    fun importMedia(tree: android.net.Uri): Int {
        val n = media.importFolder(tree)
        audio.reloadSounds()
        log("admin", "$n fichiers médias importés")
        return n
    }

    fun clearMedia() { media.clearImported(); audio.reloadSounds() }

    // ------------------------------------------------------------------ game

    /** Saved game, or null when none or when it does not match the current content (e.g. after a content change). */
    fun savedGame(): GameState? = stores.loadGame()?.takeIf { compatible(it) }

    private fun compatible(g: GameState): Boolean {
        val c = _content.value.raw
        if (g.phase !in GameEngine.PRE_GAME_PHASES && g.steps.isEmpty()) return false
        return (g.selected + g.steps.filter { !it.startsWith("@") } + listOfNotNull(g.bonusPower)).all { c.power(it) != null }
    }

    fun newGame() {
        clearMessages()
        apply(engine.newGame(System.currentTimeMillis()))
    }

    fun resumeGame(): Boolean {
        val s = savedGame() ?: return false
        clearMessages()
        _game.value = s
        log("jeu", "Partie reprise (${s.phase})")
        return true
    }

    fun endGame() {
        clearMessages()
        _game.value = null
        stores.clearGame()
        log("jeu", "Partie réinitialisée")
    }

    fun dispatch(e: GameEvent) {
        val s = _game.value ?: return
        apply(engine.handle(s, e, System.currentTimeMillis()))
    }

    private var quietSince = 0L

    private fun tick() {
        val now = System.currentTimeMillis()
        val s = _game.value
        if (s != null && gameVisible) apply(engine.handle(s, GameEvent.Tick, now))
        handleQueue(queue.tick(now))
        // Tell the engine when every dialogue is over (1.2 s of silence), so the reward screen can move on.
        val busy = queue.current != null || queue.pending.isNotEmpty()
        if (busy) quietSince = 0L else if (quietSince == 0L) quietSince = now
        val g = _game.value
        val waiting = g != null && (g.phase == Phase.REWARD || (g.phase == Phase.FINAL_INPUT && g.finalPlaybackAt == null))
        // After the card instruction, leave it alone on screen for a few seconds (time to hand the card over).
        val quietNeeded = if (g?.phase == Phase.REWARD) 6000L else 1200L
        if (g != null && gameVisible && waiting && quietSince != 0L && now - quietSince >= quietNeeded) {
            apply(engine.handle(g, GameEvent.MessagesDone, now))
        }
    }

    private fun apply(o: Output) {
        val changed = o.state != _game.value
        _game.value = o.state
        if (changed) {
            val snapshot = o.state
            io.execute { stores.saveGame(snapshot) }
        }
        o.sfx.forEach { audio.play(it) }
        o.log.forEach { log("jeu", it) }
        o.messages.forEachIndexed { i, m -> post(m, interrupt = o.interrupt && i == 0) }
    }

    // ------------------------------------------------------------------ messages

    fun post(m: Message, interrupt: Boolean = false) {
        handleQueue(queue.post(m, System.currentTimeMillis(), interrupt))
    }

    private fun clearMessages() = handleQueue(queue.clear())

    private fun handleQueue(actions: List<MessageQueue.Action>) {
        for (a in actions) when (a) {
            is MessageQueue.Action.Start -> {
                _bubble.value = a.playing
                audio.playVoice(a.playing.message.voiceId, a.playing.message.text)
                log("voix", "${a.playing.message.voiceId} [${a.playing.message.priority}]${if (a.playing.hasAudio) "" else " (texte seul)"}")
            }
            is MessageQueue.Action.Stop -> {
                if (a.interrupted) {
                    audio.fadeOutVoice()
                    log("voix", "${a.message.voiceId} interrompu")
                }
                if (_bubble.value?.message == a.message) _bubble.value = null
            }
            is MessageQueue.Action.Dropped -> log("voix", "${a.message.voiceId} abandonné")
        }
    }

    val phase: Phase? get() = _game.value?.phase
}
