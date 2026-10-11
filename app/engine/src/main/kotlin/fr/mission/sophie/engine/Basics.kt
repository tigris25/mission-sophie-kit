package fr.mission.sophie.engine

import java.text.Normalizer

object CodeNormalizer {
    private val marks = Regex("\\p{M}+")
    private val nonAlnum = Regex("[^A-Z0-9]")

    /** Uppercase, no accents, no spaces, dashes or punctuation. "Étin-celle " -> "ETINCELLE". */
    fun normalize(s: String): String {
        val upper = s.uppercase().replace("Œ", "OE").replace("Æ", "AE")
        val noAccents = Normalizer.normalize(upper, Normalizer.Form.NFD).replace(marks, "")
        return noAccents.replace(nonAlnum, "")
    }

    fun matches(input: String, answers: List<String>): Boolean {
        val n = normalize(input)
        return n.isNotEmpty() && answers.any { normalize(it) == n }
    }
}

/** Role of a physical tag. Serialized as "power:<id>", "fake_badge", "heart_badge". */
sealed class Role {
    data class PowerRole(val powerId: String) : Role()
    data object FakeBadge : Role()
    data object HeartBadge : Role()

    val key: String
        get() = when (this) {
            is PowerRole -> "power:$powerId"
            FakeBadge -> "fake_badge"
            HeartBadge -> "heart_badge"
        }

    companion object {
        fun parse(key: String): Role? = when {
            key == "fake_badge" -> FakeBadge
            key == "heart_badge" -> HeartBadge
            key.startsWith("power:") && key.length > 6 -> PowerRole(key.removePrefix("power:"))
            else -> null
        }

        fun all(content: GameContent): List<Role> =
            content.mainPowers.map { PowerRole(it.id) } + FakeBadge + HeartBadge
    }
}

/** Voice priority: lower ordinal = more important. */
enum class Priority { ACTION, ANNOUNCE, IDLE }

/** A message shown in the bubble and possibly voiced by an audio file named after [voiceId]. */
data class Message(
    val voiceId: String,
    val speaker: Speaker,
    val text: String,
    val priority: Priority,
)

enum class Sfx { SCAN_OK, NEUTRAL, ERROR, LEVEL_UP, CORRUPTION, FINALE, STEP_OK }

/** Clock abstraction so the engine can be tested with fake time. */
fun interface Clock {
    fun now(): Long
}
