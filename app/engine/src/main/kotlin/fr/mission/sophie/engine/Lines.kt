package fr.mission.sophie.engine

/** A text of the content with its computed voice identifier (spec 7.1). */
data class VoiceLine(val voiceId: String, val speaker: Speaker, val text: String)

/** Builds every displayable text with its voice id. Voice ids are computed, never stored in the JSON. */
class Lines(private val c: GameContent) {

    fun intro(i: Int): VoiceLine = c.intro[i].let { VoiceLine("intro_${i + 1}", it.speaker ?: Speaker.SOPHIE, it.text) }

    fun gatherIntro() = VoiceLine("gather_intro", Speaker.SOPHIE, c.gathering.introText)
    fun gathered(p: Power) = VoiceLine("gather_${p.id}", Speaker.SOPHIE,
        c.gathering.template.replace("{la}", article(p.talent)).replace("{talent}", p.talent))
    fun gatherDone() = VoiceLine("gather_done", Speaker.SOPHIE, c.gathering.doneText)
    fun bonusIntro() = VoiceLine("bonus_intro", Speaker.SOPHIE, c.bonus.introText)

    fun call(p: Power) = VoiceLine("call_${p.id}", Speaker.SOPHIE, p.callText)
    fun consigne(p: Power) = VoiceLine("consigne_${p.id}", Speaker.SOPHIE, p.consigne)
    fun hint(p: Power) = VoiceLine("hint_${p.id}", Speaker.SOPHIE, p.hint)
    fun success(p: Power) = VoiceLine("success_${p.id}", Speaker.SOPHIE, p.successText)
    fun react(p: Power): VoiceLine? = p.reactText?.let { VoiceLine("react_${p.id}", Speaker.INVISIBLES, it) }
    fun card(p: Power, number: Int? = null) = VoiceLine("card_secret", Speaker.SOPHIE, withNumber(p.cardText ?: c.cardText, number))

    fun trapAnnounce() = VoiceLine("trap_announce", Speaker.SOPHIE, c.trap.announceText)
    fun trapTeaser() = c.trap.teaserText?.let { VoiceLine("trap_teaser", Speaker.INVISIBLES, it) }
    fun trapCorruptInv() = VoiceLine("trap_corrupt_inv", Speaker.INVISIBLES, c.trap.corruptText)
    fun trapCorruptSophie() = c.trap.corruptSophieText?.let { VoiceLine("trap_corrupt_sophie", Speaker.SOPHIE, it) }
    fun trapRepairIntro() = VoiceLine("trap_repair_intro", Speaker.SOPHIE, c.trap.repairIntroText)
    fun trapRound(i: Int) = c.trap.roundIntroTexts.getOrNull(i)?.let { VoiceLine("trap_round_${i + 1}", Speaker.SOPHIE, it) }
    fun trapHelp() = c.trap.helpText?.let { VoiceLine("trap_help", Speaker.SOPHIE, it) }
    fun trapRoundOk(i: Int) = c.trap.roundSuccessTexts.getOrNull(i)?.let { VoiceLine("trap_round_ok_${i + 1}", Speaker.SOPHIE, it) }
    fun trapRestoredSophie() = VoiceLine("trap_restored_sophie", Speaker.SOPHIE, c.trap.restoredText)
    fun trapRestoredInv() = c.trap.restoredInvText?.let { VoiceLine("trap_restored_inv", Speaker.INVISIBLES, it) }
    fun trapWrongList() = c.trap.wrongStepTexts.map { Line(it, Speaker.INVISIBLES) }

    fun finalAnnounce() = c.final.announceText?.let { VoiceLine("final_announce", Speaker.SOPHIE, it) }
    fun finalConsigne() = c.final.consigneText?.let { VoiceLine("final_consigne", Speaker.SOPHIE, it) }
    fun finalWrongList() = c.final.wrongOrderTexts.map { Line(it, Speaker.INVISIBLES) }
    fun finalLockout() = c.final.lockoutText?.let { VoiceLine("final_lockout", Speaker.SOPHIE, it) }
    fun finalHint(selected: List<String> = emptyList()) = VoiceLine("final_hint", Speaker.SOPHIE, c.finalHint(selected))
    fun finalOkSophie() = VoiceLine("final_ok_sophie", Speaker.SOPHIE, c.final.heartLocationText)
    fun finalOkInv() = c.final.heartInvText?.let { VoiceLine("final_ok_inv", Speaker.INVISIBLES, it) }
    fun finale(): List<VoiceLine> {
        val texts = c.final.finaleTexts.ifEmpty { listOfNotNull(c.final.victoryText?.let { Line(it) }) }
        return texts.mapIndexed { i, l -> VoiceLine("finale_${i + 1}", l.speaker ?: Speaker.SOPHIE, l.text) }
    }

    /** Item [index] of a message list, voice id "<key>_<index+1>". */
    fun fromList(key: String, list: List<Line>, index: Int): VoiceLine {
        val l = list[index]
        return VoiceLine("${key}_${index + 1}", l.speaker ?: defaultSpeaker(key), l.text)
    }

    /** Every text of the content, for the "check voices" admin screen. */
    fun all(): List<VoiceLine> {
        val out = mutableListOf<VoiceLine>()
        c.intro.indices.forEach { out += intro(it) }
        out += gatherIntro(); c.mainPowers.forEach { out += gathered(it) }; out += gatherDone()
        c.powers.forEach { p ->
            out += call(p); out += consigne(p); out += hint(p); out += success(p); react(p)?.let { out += it }
        }
        out += card(c.powers.first())
        listOf(
            "wrongPendant" to c.messages.wrongPendant, "unknownTag" to c.messages.unknownTag,
            "fakeBadgeTooEarly" to c.messages.fakeBadgeTooEarly, "heartTooEarly" to c.messages.heartTooEarly,
            "tooFastSecondScan" to c.messages.tooFastSecondScan, "wrongCode" to c.messages.wrongCode,
            "codeOk" to c.messages.codeOk, "wrongCodeSophie" to c.messages.wrongCodeSophie, "idleSophie" to c.messages.idleSophie,
            "idleInvisibles" to c.messages.idleInvisibles, "invisiblesAfterSuccess" to c.messages.invisiblesAfterSuccess,
            "trap_wrong" to trapWrongList(), "final_wrong" to finalWrongList(),
        ).forEach { (key, list) -> list.indices.forEach { out += fromList(key, list, it) } }
        out += trapAnnounce(); trapTeaser()?.let { out += it }; out += trapCorruptInv(); trapCorruptSophie()?.let { out += it }
        out += trapRepairIntro(); c.trap.roundIntroTexts.indices.forEach { i -> trapRound(i)?.let { out += it } }
        trapHelp()?.let { out += it }; c.trap.roundSuccessTexts.indices.forEach { i -> trapRoundOk(i)?.let { out += it } }
        out += trapRestoredSophie(); trapRestoredInv()?.let { out += it }
        finalAnnounce()?.let { out += it }; finalConsigne()?.let { out += it }; finalLockout()?.let { out += it }
        out += finalHint(); out += finalOkSophie(); finalOkInv()?.let { out += it }; out += finale()
        out += bonusIntro()
        return out.distinctBy { it.voiceId }
    }

    companion object {
        /** Replaces "{n}" by the card number ("n°{n}" disappears when there is none). */
        fun withNumber(text: String, number: Int?): String =
            if (number != null) text.replace("{n}", number.toString())
            else text.replace(" n°{n}", "").replace("n°{n}", "").replace("{n}", "")

        /** "La " or "L'" before a talent (vowels and mute h). */
        fun article(word: String): String {
            val first = CodeNormalizer.normalize(word.take(1)).firstOrNull() ?: return "La "
            return if (first in "AEIOUYH") "L'" else "La "
        }

        fun defaultSpeaker(key: String): Speaker = when (key) {
            "heartTooEarly", "tooFastSecondScan", "codeOk", "idleSophie", "wrongCodeSophie" -> Speaker.SOPHIE
            else -> Speaker.INVISIBLES
        }
    }
}
