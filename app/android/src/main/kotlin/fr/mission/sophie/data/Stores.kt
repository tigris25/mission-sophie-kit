package fr.mission.sophie.data

import android.content.Context
import fr.mission.sophie.BuildConfig
import fr.mission.sophie.engine.ContentException
import fr.mission.sophie.engine.ContentLoadResult
import fr.mission.sophie.engine.ContentParser
import fr.mission.sophie.engine.GameContent
import fr.mission.sophie.engine.GameState
import kotlinx.serialization.Serializable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class AppSettings(
    val pin: String = "4275",
    // Overrides of the content settings (null = value from game_content.json).
    val idleFirstAfterSec: Int? = null,
    val idleRepeatEverySec: Int? = null,
    val idleMaxMessages: Int? = null,
    val codeAttemptsBeforeHint: Int? = null,
    val minSecondScanDelaySec: Int? = null,
    val voiceVolume: Float = 1f,
    val musicVolume: Float = 0.35f,
    val sfxVolume: Float = 0.8f,
    val musicEnabled: Boolean = true,
    /** Version of the places ("pluie" / "beau"), null = default of the content. */
    val variant: String? = null,
    /** Personalisation of the `{{key}}` of the content: "prenom", "age", or "pluie.coeur_lieu" for a place. Empty = value of the content. */
    val params: Map<String, String> = emptyMap(),
    /** Mutes everything (voices, music, effects), e.g. for demos. */
    val muted: Boolean = false,
    /** null = default of the build type (on in debug, off in release). */
    val simulatorEnabled: Boolean? = null,
    val ttsFallback: Boolean = false,
) {
    val simulator: Boolean get() = simulatorEnabled ?: BuildConfig.SIMULATOR_DEFAULT

    fun applyTo(c: GameContent): GameContent = c.copy(
        settings = c.settings.copy(
            idleFirstAfterSec = idleFirstAfterSec ?: c.settings.idleFirstAfterSec,
            idleRepeatEverySec = idleRepeatEverySec ?: c.settings.idleRepeatEverySec,
            idleMaxMessages = idleMaxMessages ?: c.settings.idleMaxMessages,
            codeAttemptsBeforeHint = codeAttemptsBeforeHint ?: c.settings.codeAttemptsBeforeHint,
            minSecondScanDelaySec = minSecondScanDelaySec ?: c.settings.minSecondScanDelaySec,
        ),
        // A per-trial idle delay would silently win over the admin setting: drop it when the admin set one.
        powers = if (idleFirstAfterSec != null) c.powers.map { it.copy(idleFirstAfterSec = null) } else c.powers,
    )
}

/** Tag UID (hex) -> role key ("power:telepathie", "fake_badge", "heart_badge"). */
@Serializable
data class TagBindings(val byUid: Map<String, String> = emptyMap()) {
    fun uidFor(roleKey: String): String? = byUid.entries.firstOrNull { it.value == roleKey }?.key
    fun roleOf(uid: String): String? = byUid[uid]

    fun bind(uid: String, roleKey: String): TagBindings {
        val cleaned = byUid.filter { it.key != uid && it.value != roleKey }
        return TagBindings(cleaned + (uid to roleKey))
    }

    fun unbind(roleKey: String) = TagBindings(byUid.filterValues { it != roleKey })
}

@Serializable
data class LogEntry(val time: Long, val kind: String, val text: String)

/** Small JSON file stores in the app's private storage, written atomically. */
class Stores(private val context: Context) {
    private val json = ContentParser.json
    private val dir = context.filesDir

    val mediaDir: File get() = File(dir, "media").apply { mkdirs() }

    private fun file(name: String) = File(dir, name)

    private fun writeAtomic(name: String, text: String) {
        val tmp = file("$name.tmp")
        tmp.writeText(text)
        val target = file(name)
        if (!tmp.renameTo(target)) {
            target.delete(); tmp.renameTo(target)
        }
    }

    private inline fun <T> read(name: String, parse: (String) -> T): T? = try {
        file(name).takeIf { it.exists() }?.readText()?.let(parse)
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------------ settings
    fun loadSettings(): AppSettings = read("settings.json") { json.decodeFromString(AppSettings.serializer(), it) } ?: AppSettings()
    fun saveSettings(s: AppSettings) = writeAtomic("settings.json", json.encodeToString(AppSettings.serializer(), s))

    // ------------------------------------------------------------------ bindings
    fun loadBindings(): TagBindings = read("bindings.json") { json.decodeFromString(TagBindings.serializer(), it) } ?: TagBindings()
    fun saveBindings(b: TagBindings) = writeAtomic("bindings.json", json.encodeToString(TagBindings.serializer(), b))
    fun bindingsToJson(b: TagBindings) = json.encodeToString(TagBindings.serializer(), b)
    fun bindingsFromJson(text: String) = json.decodeFromString(TagBindings.serializer(), text)

    // ------------------------------------------------------------------ game state
    fun loadGame(): GameState? = read("game_state.json") { json.decodeFromString(GameState.serializer(), it) }
    fun saveGame(s: GameState) = writeAtomic("game_state.json", json.encodeToString(GameState.serializer(), s))
    fun clearGame() { file("game_state.json").delete() }

    // ------------------------------------------------------------------ content
    /** Imported content if present and valid, else the built-in asset. */
    fun loadContent(variant: String? = null, params: Map<String, String> = emptyMap()): Pair<ContentLoadResult, Boolean> {
        val imported = file("game_content.json")
        if (imported.exists()) {
            try {
                return ContentParser.parse(imported.readText(), variant, params) to true
            } catch (e: ContentException) {
                // Corrupted import: fall back to the built-in content.
            }
        }
        val text = context.assets.open("game_content.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return ContentParser.parse(text, variant, params) to false
    }

    /** Validates then stores an imported content. Throws [ContentException] and keeps the old content on error. */
    fun importContent(text: String, variant: String? = null, params: Map<String, String> = emptyMap()): ContentLoadResult {
        val r = ContentParser.parse(text, variant, params)
        writeAtomic("game_content.json", text)
        return r
    }

    fun resetContent() { file("game_content.json").delete() }

    // ------------------------------------------------------------------ log
    private val logFile get() = file("journal.log")

    fun appendLog(e: LogEntry) {
        try {
            logFile.appendText("${e.time}\t${e.kind}\t${e.text.replace('\n', ' ')}\n")
            if (logFile.length() > 400_000) {
                val keep = logFile.readLines().takeLast(1500)
                logFile.writeText(keep.joinToString("\n", postfix = "\n"))
            }
        } catch (_: Exception) {
        }
    }

    fun readLog(limit: Int = 400): List<LogEntry> = try {
        if (!logFile.exists()) emptyList() else logFile.readLines().takeLast(limit).mapNotNull { line ->
            val p = line.split('\t', limit = 3)
            if (p.size == 3) LogEntry(p[0].toLongOrNull() ?: 0, p[1], p[2]) else null
        }.reversed()
    } catch (_: Exception) {
        emptyList()
    }

    fun clearLog() { logFile.delete() }

    companion object {
        private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE)
        fun formatTime(t: Long): String = fmt.format(Date(t))
    }
}
