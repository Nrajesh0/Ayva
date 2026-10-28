/*
 * Copyright (C) 2024-2026 Focus by Rj. All rights reserved.
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, or modification is strictly prohibited.
 */

package com.focusbyrj.app.ui.screens.notes

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

class AudioMemoManager(private val context: Context) {
    private val transcriber = com.focusbyrj.app.util.speech.OnDeviceTranscriber(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    val recordingState get() = transcriber.recordingState

    @androidx.annotation.MainThread
    fun finalizedTranscript(): String = transcriber.finalizedTranscript()

    @androidx.annotation.MainThread
    fun startRecording(onTranscriptUpdate: (String) -> Unit = {}) {
        stopPlayback()
        transcriber.startRecording(onTranscriptUpdate)
    }

    @androidx.annotation.MainThread
    fun retryRecording() = transcriber.retryRecording()

    @androidx.annotation.MainThread
    fun pauseRecording() = transcriber.pauseRecording()

    @androidx.annotation.MainThread
    fun pauseByUser() = transcriber.pauseByUser()

    @androidx.annotation.MainThread
    fun stopRecording(): Pair<String?, String> = transcriber.stopRecording()

    @androidx.annotation.MainThread
    fun cancelRecording() = transcriber.cancelRecording()

    // ==========================================
    // PLAYBACK STATE
    // ==========================================
    data class PlaybackState(
        val currentPath: String? = null,
        val isPlaying: Boolean = false,
        val currentPositionMs: Int = 0,
        val durationMs: Int = 0,
        val speed: Float = 1.0f
    )

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    fun playOrToggle(audioPath: String) {
        if (_playbackState.value.currentPath == audioPath) {
            if (_playbackState.value.isPlaying) {
                pausePlayback()
            } else {
                resumePlayback()
            }
            return
        }

        stopPlayback()

        var player: MediaPlayer? = null
        try {
            val file = File(audioPath)
            if (!file.exists()) return

            val decryptedBytes = com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(file)
            val newPlayer = MediaPlayer().apply {
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    true
                }
                if (decryptedBytes != null && com.focusbyrj.app.util.crypto.EncryptedMediaStorage.isEncrypted(file)) {
                    setDataSource(object : android.media.MediaDataSource() {
                        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                            if (position >= decryptedBytes.size) return -1
                            val length = minOf(size, (decryptedBytes.size - position).toInt())
                            System.arraycopy(decryptedBytes, position.toInt(), buffer, offset, length)
                            return length
                        }
                        override fun getSize(): Long = decryptedBytes.size.toLong()
                        override fun close() {}
                    })
                } else {
                    setDataSource(audioPath)
                }
                prepare()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && _playbackState.value.speed != 1.0f) {
                    try {
                        val params = playbackParams
                        params.speed = _playbackState.value.speed
                        playbackParams = params
                    } catch (_: Exception) {}
                }
                setOnCompletionListener {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = 0
                    )
                    playbackJob?.cancel()
                }
                start()
            }
            player = newPlayer
            mediaPlayer = newPlayer

            _playbackState.value = _playbackState.value.copy(
                currentPath = audioPath,
                isPlaying = true,
                currentPositionMs = 0,
                durationMs = newPlayer.duration
            )

            startPlaybackTracking()
        } catch (e: Exception) {
            try {
                player?.release()
            } catch (_: Throwable) {}
            stopPlayback()
        }
    }

    private fun pausePlayback() {
        try {
            mediaPlayer?.pause()
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
            playbackJob?.cancel()
        } catch (_: Exception) {}
    }

    private fun resumePlayback() {
        try {
            mediaPlayer?.start()
            _playbackState.value = _playbackState.value.copy(isPlaying = true)
            startPlaybackTracking()
        } catch (_: Exception) {}
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
        } catch (_: Exception) {}
    }

    fun setPlaybackSpeed(speed: Float) {
        try {
            mediaPlayer?.let { player ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val params = player.playbackParams
                    params.speed = speed
                    player.playbackParams = params
                }
            }
            _playbackState.value = _playbackState.value.copy(speed = speed)
        } catch (_: Exception) {}
    }

    fun skip(deltaMs: Int) {
        val current = _playbackState.value.currentPositionMs
        val dur = _playbackState.value.durationMs
        val target = (current + deltaMs).coerceIn(0, if (dur > 0) dur else Int.MAX_VALUE)
        seekTo(target)
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {} finally {
            mediaPlayer = null
        }
        _playbackState.value = PlaybackState()
    }

    private fun startPlaybackTracking() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            while (isActive && _playbackState.value.isPlaying) {
                delay(100)
                val pos = try {
                    mediaPlayer?.currentPosition ?: 0
                } catch (_: Exception) {
                    0
                }
                val dur = try {
                    mediaPlayer?.duration ?: 0
                } catch (_: Exception) {
                    0
                }
                _playbackState.value = _playbackState.value.copy(
                    currentPositionMs = pos,
                    durationMs = if (dur > 0) dur else _playbackState.value.durationMs
                )
            }
        }
    }

    fun release() {
        cancelRecording()
        stopPlayback()
    }

    companion object {
        fun formatDuration(ms: Long): String {
            val totalSeconds = (ms / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }

        fun getAudioDurationMs(path: String): Long {
            var mmr: android.media.MediaMetadataRetriever? = null
            var decryptedBytes: ByteArray? = null
            return try {
                val file = File(path)
                if (!file.exists()) return 0L
                val retriever = android.media.MediaMetadataRetriever()
                mmr = retriever
                val bytes = com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(file)
                decryptedBytes = bytes
                if (bytes != null && com.focusbyrj.app.util.crypto.EncryptedMediaStorage.isEncrypted(file)) {
                    retriever.setDataSource(object : android.media.MediaDataSource() {
                        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                            if (position >= bytes.size) return -1
                            val length = minOf(size, (bytes.size - position).toInt())
                            System.arraycopy(bytes, position.toInt(), buffer, offset, length)
                            return length
                        }
                        override fun getSize(): Long = bytes.size.toLong()
                        override fun close() {}
                    })
                } else {
                    retriever.setDataSource(path)
                }
                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationStr?.toLongOrNull() ?: 0L
            } catch (_: Throwable) {
                0L
            } finally {
                try {
                    mmr?.release()
                } catch (_: Throwable) {}
                if (decryptedBytes != null) {
                    java.util.Arrays.fill(decryptedBytes, 0.toByte())
                }
            }
        }

        fun copyAudioFile(context: Context, originalPath: String): String? {
            return try {
                val src = File(originalPath)
                if (!src.exists()) return null
                if (!com.focusbyrj.app.data.note.NoteMediaManager.isAllowedMediaFile(src, context)) {
                    android.util.Log.w("AudioMemoManager", "Refusing to copy audio from unverified or restricted path: $originalPath")
                    return null
                }
                val audioDir = File(context.filesDir, "keep_audio").apply { if (!exists()) mkdirs() }
                val ext = src.extension.ifEmpty { "m4a" }
                val dest = File(audioDir, "audio_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext")
                val rawBytes = com.focusbyrj.app.util.crypto.EncryptedMediaStorage.readDecryptedBytes(src) ?: return null
                com.focusbyrj.app.util.crypto.EncryptedMediaStorage.writeEncryptedBytes(dest, rawBytes)
                java.util.Arrays.fill(rawBytes, 0.toByte())
                dest.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
