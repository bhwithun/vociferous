package com.brian.dialbuddy.ui.components

import android.media.AudioManager
import android.media.ToneGenerator

object Dtmf {
    private var generator: ToneGenerator? = null

    fun play(symbol: String) {
        val tone = toneFor(symbol) ?: return
        try {
            tones().startTone(tone, 160)
        } catch (_: RuntimeException) {
            generator?.release()
            generator = null
            runCatching { tones().startTone(tone, 160) }
        }
    }

    private fun tones(): ToneGenerator {
        return generator ?: ToneGenerator(AudioManager.STREAM_DTMF, 80).also { generator = it }
    }

    private fun toneFor(symbol: String): Int? = when (symbol) {
        "0" -> ToneGenerator.TONE_DTMF_0
        "1" -> ToneGenerator.TONE_DTMF_1
        "2" -> ToneGenerator.TONE_DTMF_2
        "3" -> ToneGenerator.TONE_DTMF_3
        "4" -> ToneGenerator.TONE_DTMF_4
        "5" -> ToneGenerator.TONE_DTMF_5
        "6" -> ToneGenerator.TONE_DTMF_6
        "7" -> ToneGenerator.TONE_DTMF_7
        "8" -> ToneGenerator.TONE_DTMF_8
        "9" -> ToneGenerator.TONE_DTMF_9
        "*" -> ToneGenerator.TONE_DTMF_S
        "#" -> ToneGenerator.TONE_DTMF_P
        else -> null
    }
}
