package fr.mission.sophie.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Bundle
import android.speech.tts.TextToSpeech
import fr.mission.sophie.data.MediaRepo
import fr.mission.sophie.engine.Sfx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Voices (one at a time, with 200 ms fade on interruption), background music with ducking,
 * and short effects. Every file is optional: missing effects use synthesized sounds, missing voices are silent.
 */
class AudioEngine(private val context: Context, private val media: MediaRepo) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var voiceVolume = 1f
    var musicVolume = 0.35f
        set(v) { field = v; applyMusicVolume() }
    var sfxVolume = 0.8f
    var musicEnabled = true
        set(v) { field = v; if (!v) stopMusic() }
    var ttsFallback = false

    private val speechAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val musicAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val sfxAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    private val pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(sfxAttrs).build()
    private val soundIds = HashMap<String, Int>()

    private var voice: MediaPlayer? = null
    private var voiceFade: Job? = null
    private var music: MediaPlayer? = null
    private var ducked = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var focusRequest: AudioFocusRequest? = null

    init { reloadSounds() }

    /** (Re)loads effects: imported/built-in "sfx_<name>" files first, synthesized WAV otherwise. */
    fun reloadSounds() {
        soundIds.values.forEach { pool.unload(it) }
        soundIds.clear()
        val synth = Synth.generate(File(context.cacheDir, "synth2"))
        synth.forEach { (name, file) ->
            val id = when (val src = media.audio("sfx_$name")) {
                is MediaRepo.Source.FileSrc -> pool.load(src.file.absolutePath, 1)
                is MediaRepo.Source.AssetSrc -> media.openAsset(src.path).use { pool.load(it, 1) }
                null -> pool.load(file.absolutePath, 1)
            }
            soundIds[name] = id
        }
    }

    fun requestFocus() {
        if (focusRequest != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(musicAttrs).setOnAudioFocusChangeListener { }.build()
        am.requestAudioFocus(req)
        focusRequest = req
    }

    fun abandonFocus() {
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    // ------------------------------------------------------------------ effects

    fun play(sfx: Sfx) = playNamed(
        when (sfx) {
            Sfx.SCAN_OK -> "scan_ok"
            Sfx.NEUTRAL -> "neutral"
            Sfx.ERROR -> "error"
            Sfx.LEVEL_UP -> "level_up"
            Sfx.CORRUPTION -> "corruption"
            Sfx.FINALE -> "finale"
            Sfx.STEP_OK -> "step_ok"
        }
    )

    /** Tone of the power at [index] in the content's power list (echo of the powers). */
    fun powerTone(index: Int) = playNamed("power_${index.coerceIn(0, Synth.POWER_NOTES.lastIndex)}")

    private fun playNamed(name: String) {
        val id = soundIds[name] ?: return
        pool.play(id, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    // ------------------------------------------------------------------ voices

    /** Plays the voice file of [voiceId]. Returns false when there is no file (TTS may speak [text] instead). */
    fun playVoice(voiceId: String, text: String): Boolean {
        stopVoiceNow()
        val src = media.audio(voiceId)
        if (src == null) {
            if (ttsFallback && voiceId != "mj_free") speak(text)
            return false
        }
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(speechAttrs)
            when (src) {
                is MediaRepo.Source.FileSrc -> mp.setDataSource(src.file.absolutePath)
                is MediaRepo.Source.AssetSrc -> media.openAsset(src.path).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            }
            mp.setVolume(voiceVolume, voiceVolume)
            mp.setOnCompletionListener { if (voice === it) { it.release(); voice = null; duck(false) } }
            mp.setOnErrorListener { p, _, _ -> if (voice === p) { voice = null; duck(false) }; p.release(); true }
            mp.prepare()
            mp.start()
            voice = mp
            duck(true)
            return true
        } catch (e: Exception) {
            mp.release()
            return false
        }
    }

    /** Stops the current voice with a 200 ms fade. */
    fun fadeOutVoice() {
        val mp = voice ?: run { tts?.stop(); return }
        voice = null
        voiceFade = scope.launch {
            for (i in 4 downTo 0) {
                val v = voiceVolume * i / 5f
                try { mp.setVolume(v, v) } catch (_: Exception) {}
                delay(40)
            }
            try { mp.stop() } catch (_: Exception) {}
            mp.release()
            duck(false)
        }
    }

    private fun stopVoiceNow() {
        tts?.stop()
        val mp = voice ?: return
        voice = null
        try { mp.stop() } catch (_: Exception) {}
        mp.release()
    }

    private fun speak(text: String) {
        val t = tts
        if (t == null) {
            tts = TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) { tts?.language = Locale.FRENCH; speak(text) }
            }
            return
        }
        if (!ttsReady) return
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, voiceVolume) }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, params, "msg")
    }

    fun estimateTtsMs(text: String): Long? = if (ttsFallback) 1000L + text.length * 65L else null

    // ------------------------------------------------------------------ music

    fun startMusic() {
        if (!musicEnabled || music != null) return
        val src = media.audio("music") ?: return
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(musicAttrs)
            when (src) {
                is MediaRepo.Source.FileSrc -> mp.setDataSource(src.file.absolutePath)
                is MediaRepo.Source.AssetSrc -> media.openAsset(src.path).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            }
            mp.isLooping = true
            mp.prepare()
            music = mp
            applyMusicVolume()
            mp.start()
        } catch (e: Exception) {
            mp.release()
        }
    }

    fun stopMusic() {
        music?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        music = null
    }

    private fun duck(on: Boolean) { ducked = on; applyMusicVolume() }

    private fun applyMusicVolume() {
        val v = if (ducked) musicVolume * 0.3f else musicVolume
        music?.setVolume(v, v)
    }

    fun release() {
        stopVoiceNow(); stopMusic(); pool.release(); tts?.shutdown(); abandonFocus()
    }
}
