package com.focusbyrj.app.ui.speech

import android.content.Context

/** Stable preference IDs; display names can evolve without invalidating a saved choice. */
enum class VoiceStyle(val id: String, val title: String, val description: String) {
    LIQUID("liquid", "Liquid Core", "Fluid currents in a soft, breathing orb"),
    HALO("halo", "Breathing Halo", "An open ring that flexes with your voice"),
    RIBBON("ribbon", "Silk Ribbon", "Fine, flowing ribbons of sound"),
    PARTICLES("particles", "Particle Cloud", "A quiet constellation that opens with speech"),
    PETALS("petals", "Pulse Petals", "Layered petals that unfold with sound"),
    EQUALIZER("equalizer", "Soft Equalizer", "Seven rounded bars following your volume"),
    RIPPLES("ripples", "Ripple Pool", "Gentle, expanding contours"),
    ORBITS("orbits", "Orbit Weave", "Fine loops woven around a quiet centre"),
    AURORA("aurora", "Aurora Veil", "Soft curtains of moving light"),
    FIELD("field", "Harmonic Field", "A field of dots carrying a wave of sound");

    companion object {
        const val PREFERENCE = "voice_transcription_style"
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: LIQUID
        fun read(context: Context) = fromId(context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
            .getString(PREFERENCE, null))
        fun save(context: Context, style: VoiceStyle) {
            context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE).edit()
                .putString(PREFERENCE, style.id).apply()
        }
    }
}
