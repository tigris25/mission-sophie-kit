package fr.mission.sophie.engine

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
enum class Speaker {
    @SerialName("sophie") SOPHIE,
    @SerialName("invisibles") INVISIBLES,
}

/** A line of text. In JSON it is either a plain string or `{ "speaker": "sophie"|"invisibles", "text": "..." }`. */
@Serializable(with = LineSerializer::class)
data class Line(val text: String, val speaker: Speaker? = null)

object LineSerializer : KSerializer<Line> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Line")

    override fun deserialize(decoder: Decoder): Line {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Line: JSON only")
        return when (val e = input.decodeJsonElement()) {
            is JsonPrimitive -> Line(e.content)
            is JsonObject -> {
                val text = e["text"]?.jsonPrimitive?.contentOrNull
                    ?: throw SerializationException("Line: champ \"text\" manquant dans $e")
                val speaker = when (e["speaker"]?.jsonPrimitive?.contentOrNull) {
                    null -> null
                    "sophie" -> Speaker.SOPHIE
                    "invisibles" -> Speaker.INVISIBLES
                    else -> throw SerializationException("Line: speaker inconnu dans $e (attendu sophie ou invisibles)")
                }
                Line(text, speaker)
            }
            else -> throw SerializationException("Line: attendu une chaîne ou un objet, reçu $e")
        }
    }

    override fun serialize(encoder: Encoder, value: Line) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("Line: JSON only")
        if (value.speaker == null) {
            output.encodeJsonElement(JsonPrimitive(value.text))
        } else {
            output.encodeJsonElement(buildJsonObject {
                put("speaker", if (value.speaker == Speaker.SOPHIE) "sophie" else "invisibles")
                put("text", value.text)
            })
        }
    }
}

@Serializable
data class Settings(
    val idleFirstAfterSec: Int = 240,
    val idleRepeatEverySec: Int = 150,
    val idleMaxMessages: Int = 6,
    val idleMinGapSec: Int = 60,
    val minSecondScanDelaySec: Int = 30,
    val codeAttemptsBeforeHint: Int = 3,
    val codeCooldownAfterHintSec: Int = 5,
    /** Minimum gap between two reactions to wrong codes (other errors only get the error sound). */
    val wrongCodeMsgGapSec: Int = 20,
    val levelUpSec: Int = 7,
    /** Reward screen: at least rewardMinSec, then it waits for the dialogues to end (at most rewardScreenSec). */
    val rewardMinSec: Int = 4,
    val rewardScreenSec: Int = 45,
    val corruptionSec: Int = 5,
    val finaleSec: Int = 25,
)

@Serializable
data class Shout(
    /** Level to reach, 0..1 on the on-screen gauge. */
    val threshold: Float = 0.8f,
    /** Seconds the level must be held. */
    val holdSec: Float = 3f,
)

@Serializable
data class Power(
    val id: String,
    /** Official talent name (masculine, as in the books), shown on level-up. */
    val name: String,
    /** Feminine form used for the girls ("Éclipseuse"). */
    val talent: String,
    val color: String = "#FFFFFF",
    /** "code" (default): scan, trial, scan, code. "shout": scan then the shout meter validates. */
    val kind: String = KIND_CODE,
    /** Only playable as a bonus after the finale (no pendant). */
    val bonusOnly: Boolean = false,
    val callText: String,
    val title: String,
    val consigne: String,
    val answers: List<String> = emptyList(),
    val hint: String,
    val idleFirstAfterSec: Int? = null,
    val successText: String,
    /** Invisibles' reaction after this power's success; falls back to messages.invisiblesAfterSuccess. */
    val reactText: String? = null,
    val cardText: String? = null,
    val shout: Shout = Shout(),
) {
    val isShout: Boolean get() = kind == KIND_SHOUT

    companion object {
        const val KIND_CODE = "code"
        const val KIND_SHOUT = "shout"
    }
}

@Serializable
data class Repair(
    val type: String = "simon",
    val rounds: List<Int> = listOf(3, 4, 5),
    val stepDurationMs: Int = 1200,
    val replaysPerRound: Int = 2,
    val maxFailuresBeforeHelp: Int = 6,
    val lastRoundIncludesCorrupted: Boolean = true,
)

@Serializable
data class Trap(
    /** "middle" (default): after half of the chosen trials; or a power id (if chosen). */
    val afterPower: String = "middle",
    val announceText: String,
    val teaserText: String? = null,
    /** "last_acquired", "random" or a power id. */
    val corruptedPower: String = "last_acquired",
    /** Invisibles' line on corruption. */
    val corruptText: String,
    val corruptSophieText: String? = null,
    val repair: Repair = Repair(),
    val repairIntroText: String,
    val roundIntroTexts: List<String> = emptyList(),
    val wrongStepTexts: List<String> = emptyList(),
    val helpText: String? = null,
    val roundSuccessTexts: List<String> = emptyList(),
    val restoredText: String,
    val restoredInvText: String? = null,
)

/**
 * Final word for a given number of pendants. After each trial the MJ hands the next card of the stack:
 * card n°k carries the letter [cards][k-1]. The pendants must then be laid in the order of the letters of [word].
 */
@Serializable
data class FinalWord(
    val word: String,
    /** The same letters, in the order the cards are handed out (a scramble of [word]). */
    val cards: String,
    /** Hint after [Final.hintAfterFailures] wrong attempts (else [Final.hint]). */
    val hint: String? = null,
) {
    val letters: String get() = CodeNormalizer.normalize(word)
    val cardLetters: String get() = CodeNormalizer.normalize(cards)
}

@Serializable
data class Final(
    /**
     * "echo" (default): the pendants go back in the order the trials were played; the app replays that
     * order (the final echo) and each girl has a numbered card. "words": order given by [words] / [order].
     */
    val mode: String = MODE_ECHO,
    /** Duration of one crystal in the final echo. */
    val echoStepMs: Int = 1300,
    /** One word per number of pendants (7 to 10 letters, all different), used in "words" mode. */
    val words: List<FinalWord> = emptyList(),
    /** Fallback order when no word matches the number of pendants; only the chosen powers are kept. */
    val order: List<String> = emptyList(),
    val lockoutSec: Int = 20,
    val hintAfterFailures: Int = 3,
    val announceText: String? = null,
    val consigneText: String? = null,
    val wrongOrderTexts: List<String> = emptyList(),
    val lockoutText: String? = null,
    val hint: String,
    val heartLocationText: String,
    val heartInvText: String? = null,
    val victoryText: String? = null,
    val finaleTexts: List<Line> = emptyList(),
) {
    companion object {
        const val MODE_ECHO = "echo"
        const val MODE_WORDS = "words"
    }
}

@Serializable
data class Gathering(
    val introText: String = "Chacune à votre tour, posez votre pendentif sur le Transmetteur pour réveiller votre talent !",
    /** {la} is replaced by "La " or "L'" and {talent} by the talent. */
    val template: String = "{la}{talent} a répondu à l'appel !",
    val doneText: String = "Toutes les Gardiennes sont réunies. Que la mission commence !",
)

@Serializable
data class Bonus(
    val introText: String = "Il reste des talents endormis… Voulez-vous m'aider encore un peu, Gardiennes ?",
)

@Serializable
data class Messages(
    val wrongPendant: List<Line> = emptyList(),
    val unknownTag: List<Line> = emptyList(),
    val fakeBadgeTooEarly: List<Line> = emptyList(),
    val heartTooEarly: List<Line> = emptyList(),
    val tooFastSecondScan: List<Line> = emptyList(),
    val wrongCode: List<Line> = emptyList(),
    /** Sophie's encouragements after a wrong code, alternating with the Invisibles' mockery. */
    val wrongCodeSophie: List<Line> = emptyList(),
    val codeOk: List<Line> = emptyList(),
    val idleSophie: List<Line> = emptyList(),
    val idleInvisibles: List<Line> = emptyList(),
    val invisiblesAfterSuccess: List<Line> = emptyList(),
)

@Serializable
data class Ui(
    val homeTitle: String = "Les Pouvoirs volés",
    val homeSubtitle: String = "Mission Sophie",
    val startButton: String = "Lancer la mission",
    val nextButton: String = "Suivant",
    val replayButton: String = "Écouter à nouveau",
    val startEchoButton: String = "Commencer l'écho",
    val replayGameButton: String = "Rejouer",
    val newGameButton: String = "Nouvelle partie",
    val gatherButton: String = "Toutes les Gardiennes sont là !",
    val gatherPrompt: String = "Posez vos pendentifs un par un",
    val bonusButton: String = "Épreuves bonus",
    val homeButton: String = "Retour à l'accueil",
    val bonusTitle: String = "Épreuves bonus",
    val enterCodeButton: String = "Entrer le code",
    val backButton: String = "Retour",
    val shoutLabel: String = "Criez toutes ensemble !",
    val finalReplayButton: String = "Revoir l'écho",
    val finalWatchLabel: String = "Regardez bien l'ordre…",
    val shoutMicMissing: String = "Micro non autorisé : le Maître du jeu valide l'épreuve.",
    val codePrompt: String = "Tape le code secret",
    val validateButton: String = "Valider",
    val waitCooldown: String = "Patiente un instant…",
    val lockoutLabel: String = "Les pendentifs se rechargent…",
    val powerCounter: String = "Pouvoir %d/%d",
    val cardReminder: String = "Va chercher ta carte secrète auprès du Maître du jeu !",
)

@Serializable
data class GameContent(
    val version: Int = 1,
    val settings: Settings = Settings(),
    /** Preferred order of the trials; only the powers chosen at the gathering are played. */
    val sequence: List<String> = emptyList(),
    val intro: List<Line>,
    val gathering: Gathering = Gathering(),
    val powers: List<Power>,
    val trap: Trap,
    val final: Final,
    val bonus: Bonus = Bonus(),
    val messages: Messages = Messages(),
    /** Single "go get your secret card" line, voice id card_secret. */
    val cardText: String = "Ta carte secrète t'attend auprès du Maître du jeu.",
    val ui: Ui = Ui(),
) {
    fun power(id: String): Power? = powers.firstOrNull { it.id == id }

    /** Powers that have a pendant and can be chosen at the gathering. */
    val mainPowers: List<Power> get() = powers.filter { !it.bonusOnly }

    /** Chosen powers in play order: the preferred [sequence] first, then the others in scan order. */
    fun orderTrials(selected: List<String>): List<String> =
        sequence.filter { it in selected } + selected.filter { it !in sequence }

    /** Trials + "@trap" + "@final". The trap needs at least 2 trials (one power to corrupt, one after). */
    fun buildSteps(selected: List<String>): List<String> {
        val steps = orderTrials(selected).toMutableList()
        if (steps.size >= 2) {
            val at = steps.indexOf(trap.afterPower).takeIf { it in 0 until steps.lastIndex }?.plus(1)
                ?: ((steps.size + 1) / 2)
            steps.add(at, STEP_TRAP)
        }
        steps.add(STEP_FINAL)
        return steps
    }

    /** Final word used for this number of pendants, if any. */
    fun finalWord(selected: List<String>): FinalWord? =
        if (final.mode == Final.MODE_WORDS) final.words.firstOrNull { it.letters.length == selected.size } else null

    /** Number of the card handed after the trial of [powerId] (1-based); null in words mode without a word. */
    fun cardNumber(selected: List<String>, powerId: String): Int? {
        if (final.mode == Final.MODE_WORDS && finalWord(selected) == null) return null
        return orderTrials(selected).indexOf(powerId).takeIf { it >= 0 }?.plus(1)
    }

    /** Expected final order for the chosen powers. */
    fun finalOrder(selected: List<String>): List<String> {
        val trials = orderTrials(selected)
        if (final.mode != Final.MODE_WORDS) return trials // order in which the powers were awakened
        val w = finalWord(selected)
            ?: return final.order.filter { it in selected } + trials.filter { it !in final.order }
        // The holder of card k has letter cards[k]; pendants go in the order of the letters in the word.
        return trials.indices.sortedBy { w.letters.indexOf(w.cardLetters[it]) }.map { trials[it] }
    }

    fun finalHint(selected: List<String>): String = finalWord(selected)?.hint ?: final.hint

    /** Bonus trials after the finale: powers not chosen + bonus-only powers. */
    fun bonusPowers(selected: List<String>): List<Power> = powers.filter { it.id !in selected }

    companion object {
        const val STEP_TRAP = "@trap"
        const val STEP_FINAL = "@final"
    }
}

/** A version of the places (e.g. "pluie" / "beau"), chosen in Admin. */
data class Variant(val id: String, val label: String)

/** Result of parsing: the content, non-fatal warnings (for the log), the available variants and the one applied. */
data class ContentLoadResult(
    val content: GameContent,
    val warnings: List<String>,
    val variants: List<Variant> = emptyList(),
    val variant: String? = null,
)

class ContentException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ContentParser {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
    }

    private val placeholder = Regex("\\{\\{([a-zA-Z0-9_]+)\\}\\}")

    /**
     * Parses and validates. Throws [ContentException] with a precise French message on fatal errors.
     *
     * Variants: the JSON may contain `"variants": { "pluie": { "_label": "Il pleut", "piege_de": "de la mezzanine", ... }, ... }`.
     * Every `{{key}}` in the texts is replaced by the value of the chosen [variant] (else `defaultVariant`, else the first).
     */
    fun parse(text: String, variant: String? = null): ContentLoadResult {
        val root = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw ContentException("JSON invalide : ${e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.simpleName}", e)
        }
        val warnings = mutableListOf<String>()
        val obj = root as? JsonObject ?: throw ContentException("JSON invalide : un objet { … } est attendu")
        val variantsObj = obj["variants"] as? JsonObject
        val variants = variantsObj?.entries?.map { (id, v) ->
            Variant(id, ((v as? JsonObject)?.get("_label") as? JsonPrimitive)?.contentOrNull ?: id)
        } ?: emptyList()
        val default = (obj["defaultVariant"] as? JsonPrimitive)?.contentOrNull
        val chosen = listOf(variant, default).firstOrNull { it != null && variants.any { v -> v.id == it } } ?: variants.firstOrNull()?.id
        val values: Map<String, String> = (variantsObj?.get(chosen ?: "") as? JsonObject)
            ?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull ?: "" } ?: emptyMap()
        val unknown = sortedSetOf<String>()
        fun subst(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> JsonObject(e.filterKeys { it != "variants" }.mapValues { subst(it.value) })
            is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(e.map { subst(it) })
            is JsonPrimitive -> if (e.isString && "{{" in e.content) JsonPrimitive(placeholder.replace(e.content) { m ->
                values[m.groupValues[1]] ?: m.value.also { unknown += m.groupValues[1] }
            }) else e
            else -> e
        }
        val content = try {
            json.decodeFromJsonElement(GameContent.serializer(), subst(obj))
        } catch (e: Exception) {
            throw ContentException("JSON invalide : ${e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.simpleName}", e)
        }
        unknown.forEach { warnings += "Lieu {{$it}} non défini pour la version « ${chosen ?: "-"} »" }
        variantsObj?.entries?.forEach { (id, v) ->
            val missing = values.keys - ((v as? JsonObject)?.keys ?: emptySet())
            if (missing.isNotEmpty()) warnings += "Version « $id » : il manque ${missing.joinToString()}"
        }
        return ContentLoadResult(content, validate(content) + warnings, variants, chosen)
    }

    fun validate(c: GameContent): List<String> {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val ids = c.powers.map { it.id }
        ids.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { errors += "Pouvoir en double : $it" }
        if (c.mainPowers.isEmpty()) errors += "Aucun pouvoir avec pendentif (tous sont bonusOnly)"
        c.sequence.filter { it !in ids }.forEach { errors += "La séquence cite un pouvoir inconnu : $it" }
        c.sequence.filter { c.power(it)?.bonusOnly == true }.forEach { warnings += "La séquence cite un pouvoir bonus (ignoré) : $it" }
        c.final.order.filter { it !in ids }.forEach { errors += "final.order cite un pouvoir inconnu : $it" }
        if (c.final.order.toSet().size != c.final.order.size) errors += "final.order contient un doublon"
        if (c.final.mode != Final.MODE_ECHO && c.final.mode != Final.MODE_WORDS) errors += "final.mode inconnu : ${c.final.mode} (attendu echo ou words)"
        c.final.words.forEach { w ->
            if (w.letters.toSet().size != w.letters.length) errors += "Mot final ${w.word} : chaque lettre doit être unique"
            if (w.cardLetters.toList().sorted() != w.letters.toList().sorted()) errors += "Mot final ${w.word} : cards doit contenir exactement les mêmes lettres"
        }
        c.final.words.groupBy { it.letters.length }.filter { it.value.size > 1 }
            .forEach { errors += "Plusieurs mots finaux de ${it.key} lettres" }
        c.powers.filter { it.kind != Power.KIND_CODE && it.kind != Power.KIND_SHOUT }
            .forEach { errors += "Le pouvoir ${it.id} a un type inconnu : ${it.kind} (attendu code ou shout)" }
        c.powers.filter { !it.isShout && it.answers.none { a -> CodeNormalizer.normalize(a).isNotEmpty() } }
            .forEach { errors += "Le pouvoir ${it.id} n'a aucune réponse (answers)" }
        if (c.intro.isEmpty()) errors += "intro est vide"
        val cp = c.trap.corruptedPower
        if (cp != "last_acquired" && cp != "random" && cp !in ids) errors += "trap.corruptedPower inconnu : $cp"
        if (c.trap.afterPower != "middle" && c.trap.afterPower !in ids) errors += "trap.afterPower inconnu : ${c.trap.afterPower}"
        if (c.trap.repair.rounds.isEmpty()) errors += "trap.repair.rounds est vide"
        if (c.trap.repair.rounds.any { it < 1 }) errors += "trap.repair.rounds : chaque manche doit compter au moins 1 pouvoir"
        if (c.trap.repair.type != "simon") warnings += "trap.repair.type \"${c.trap.repair.type}\" inconnu : validation par le MJ uniquement"
        if (errors.isNotEmpty()) throw ContentException(errors.joinToString("\n"))

        val m = c.messages
        listOf(
            "wrongPendant" to m.wrongPendant, "unknownTag" to m.unknownTag, "fakeBadgeTooEarly" to m.fakeBadgeTooEarly,
            "heartTooEarly" to m.heartTooEarly, "tooFastSecondScan" to m.tooFastSecondScan, "wrongCode" to m.wrongCode,
            "idleSophie" to m.idleSophie, "idleInvisibles" to m.idleInvisibles, "invisiblesAfterSuccess" to m.invisiblesAfterSuccess,
        ).filter { it.second.isEmpty() }.forEach { warnings += "messages.${it.first} est vide" }
        if (c.trap.roundIntroTexts.size < c.trap.repair.rounds.size) warnings += "trap.roundIntroTexts : moins de textes que de manches"
        if (c.final.finaleTexts.isEmpty() && c.final.victoryText == null) warnings += "final.finaleTexts est vide"
        return warnings
    }
}
