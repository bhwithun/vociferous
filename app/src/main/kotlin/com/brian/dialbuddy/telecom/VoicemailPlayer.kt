package com.brian.dialbuddy.telecom

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.VoicemailContract
import java.util.concurrent.Executors

class VoicemailPlayer(
    context: Context,
    private val store: VoicemailStore,
    speakerOn: Boolean,
    private val onError: (String) -> Unit,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val audio = app.getSystemService(AudioManager::class.java)
    private val speech = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var speakerOn = speakerOn
    private var generation = 0
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var observer: ContentObserver? = null
    private var poll: Runnable? = null
    private var previousMode: Int? = null
    private var preparing = false

    fun play(id: Long) {
        if (Calls.state.value.calls.isNotEmpty()) {
            onError(app.getString(com.brian.dialbuddy.R.string.play_during_call))
            return
        }
        stop()
        val token = generation
        io.execute {
            val media = try {
                store.media(id)
            } catch (_: VoicemailAccessDenied) {
                null
            }
            main.post {
                if (token != generation) return@post
                when {
                    media == null -> fail(app.getString(com.brian.dialbuddy.R.string.play_failed))
                    media.hasContent -> startPlayer(id, media.sourcePackage, token)
                    requestDownload(id, media.sourcePackage) -> watch(id, media.sourcePackage, token)
                    else -> fail(app.getString(com.brian.dialbuddy.R.string.download_failed))
                }
            }
        }
    }

    fun setSpeaker(on: Boolean) {
        speakerOn = on
        player?.let { applyRoute(it) }
    }

    fun stop() {
        generation += 1
        preparing = false
        clearWatch()
        releasePlayer()
        restoreRoute()
        abandonFocus()
    }

    fun release() {
        stop()
        io.shutdown()
    }

    private fun startPlayer(id: Long, sourcePackage: String?, token: Int) {
        if (token != generation) return
        if (!requestFocus()) {
            fail(app.getString(com.brian.dialbuddy.R.string.play_failed))
            return
        }
        preparing = true
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(speech)
        mp.setOnPreparedListener { prepared ->
            if (token != generation) return@setOnPreparedListener
            applyRoute(prepared)
            prepared.start()
            preparing = false
        }
        mp.setOnCompletionListener {
            if (token == generation) stop()
        }
        mp.setOnErrorListener { _, _, _ ->
            if (token == generation) fail(app.getString(com.brian.dialbuddy.R.string.play_failed))
            true
        }
        try {
            applyRoute(mp)
            mp.setDataSource(app, store.itemUri(id, sourcePackage))
            mp.prepareAsync()
        } catch (_: Exception) {
            fail(app.getString(com.brian.dialbuddy.R.string.play_failed))
        }
    }

    private fun requestDownload(id: Long, sourcePackage: String?): Boolean {
        val pkg = sourcePackage?.takeIf { it.isNotBlank() } ?: FI_PACKAGE
        val intent = Intent(VoicemailContract.ACTION_FETCH_VOICEMAIL).apply {
            setDataAndType(store.itemUri(id, pkg), VOICEMAIL_ITEM)
            setPackage(pkg)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        return try {
            app.sendBroadcast(intent)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun watch(id: Long, sourcePackage: String?, token: Int) {
        val obs = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) {
                checkReady(id, sourcePackage, token)
            }
        }
        observer = obs
        app.contentResolver.registerContentObserver(VoicemailContract.Voicemails.CONTENT_URI, true, obs)
        val deadline = SystemClock.elapsedRealtime() + DOWNLOAD_TIMEOUT_MS
        val task = object : Runnable {
            override fun run() {
                if (token != generation || preparing) return
                checkReady(id, sourcePackage, token)
                if (token == generation && !preparing) {
                    if (SystemClock.elapsedRealtime() < deadline) {
                        main.postDelayed(this, POLL_MS)
                    } else {
                        fail(app.getString(com.brian.dialbuddy.R.string.download_failed))
                    }
                }
            }
        }
        poll = task
        main.postDelayed(task, POLL_MS)
    }

    private fun checkReady(id: Long, sourcePackage: String?, token: Int) {
        if (token != generation || preparing) return
        io.execute {
            val ready = try {
                store.media(id).hasContent
            } catch (_: Exception) {
                false
            }
            main.post {
                if (token != generation || preparing || !ready) return@post
                preparing = true
                startPlayer(id, sourcePackage, token)
            }
        }
    }

    private fun applyRoute(mp: MediaPlayer) {
        if (previousMode == null) previousMode = audio.mode
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        val type = if (speakerOn) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        val device = audio.availableCommunicationDevices.firstOrNull { it.type == type }
            ?: audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == type }
        if (device != null) {
            audio.setCommunicationDevice(device)
            mp.setPreferredDevice(device)
        }
    }

    private fun restoreRoute() {
        audio.clearCommunicationDevice()
        previousMode?.let { audio.mode = it }
        previousMode = null
    }

    private fun requestFocus(): Boolean {
        if (focusRequest != null) return true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(speech)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) main.post { stop() }
            }
            .build()
        val granted = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) return false
        focusRequest = request
        return true
    }

    private fun abandonFocus() {
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun releasePlayer() {
        player?.let { mp ->
            mp.setOnPreparedListener(null)
            mp.setOnCompletionListener(null)
            mp.setOnErrorListener(null)
            try {
                mp.reset()
            } catch (_: IllegalStateException) {
            }
            mp.release()
        }
        player = null
    }

    private fun clearWatch() {
        poll?.let { main.removeCallbacks(it) }
        poll = null
        observer?.let { app.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    private fun fail(message: String) {
        stop()
        onError(message)
    }

    private companion object {
        const val FI_PACKAGE = "com.google.android.apps.tycho"
        const val VOICEMAIL_ITEM = "vnd.android.cursor.item/voicemail"
        const val DOWNLOAD_TIMEOUT_MS = 25_000L
        const val POLL_MS = 1_000L
    }
}
