package io.github.michealjiaming.pomodoro

import androidx.compose.ui.graphics.Color

/**
 * A whole palette, not just a background: the two light themes carry their own
 * deeper red and green so white button text stays readable on them.
 *
 * The values are the same hex codes the desktop app uses.
 *
 * The fields, since their names are short: key is the string persisted to
 * preferences and must never change once written; label is what the theme button
 * displays; light tells [PomodoroTheme] which Material scheme to build. Then bg is
 * the screen, card is a raised surface (buttons, dialogs), fg is body text, muted
 * is secondary text, track is the unfilled part of the ring, accent and accentBreak
 * are the work and break colours, and onAccent is text drawn on top of either
 * accent — white in all three themes, which is exactly why the light themes need
 * darker accents.
 */
data class Palette(
    val key: String,
    val label: String,
    val light: Boolean,
    val bg: Color,
    val card: Color,
    val fg: Color,
    val muted: Color,
    val track: Color,
    val accent: Color,
    val accentBreak: Color,
    val onAccent: Color,
) {
    /** The colour that follows the current mode, as on the desktop. */
    fun accentFor(mode: Mode): Color = if (mode == Mode.BREAK) accentBreak else accent
}

val PALETTES: List<Palette> = listOf(
    Palette(
        key = "black", label = "Black", light = false,
        bg = Color(0xFF15171C), card = Color(0xFF1E2128), fg = Color(0xFFEEF1F6),
        muted = Color(0xFF8B93A5), track = Color(0xFF2B2F39),
        accent = Color(0xFFE2564A), accentBreak = Color(0xFF3FA66C),
        onAccent = Color(0xFFFFFFFF),
    ),
    Palette(
        key = "paper", label = "Paper", light = true,
        bg = Color(0xFFF4EFE6), card = Color(0xFFE5DED0), fg = Color(0xFF2E2A24),
        muted = Color(0xFF7B7267), track = Color(0xFFD6CEBE),
        accent = Color(0xFFCF4A3D), accentBreak = Color(0xFF2F8F5C),
        onAccent = Color(0xFFFFFFFF),
    ),
    Palette(
        key = "mist", label = "Mist", light = true,
        bg = Color(0xFFEAEEF3), card = Color(0xFFD9E0E9), fg = Color(0xFF26303C),
        muted = Color(0xFF6C7B8D), track = Color(0xFFC8D2DD),
        accent = Color(0xFFCF4A3D), accentBreak = Color(0xFF2F8F5C),
        onAccent = Color(0xFFFFFFFF),
    ),
)

/** Unknown names fall back to the default rather than failing to start. */
fun paletteFor(key: String?): Palette = PALETTES.firstOrNull { it.key == key } ?: PALETTES[0]

/**
 * The next palette in the list, wrapping round from the last back to the first.
 * This is what the theme button cycles through.
 *
 * An unrecognised key returns the *first* palette rather than advancing from
 * anywhere, so a corrupt stored theme resolves to Black on the next press instead
 * of leaving the button dead. Note the difference from [paletteFor], which also
 * falls back to index 0 — here that fallback means "start the cycle over".
 */
fun nextPalette(key: String): Palette {
    val index = PALETTES.indexOfFirst { it.key == key }
    return PALETTES[if (index < 0) 0 else (index + 1) % PALETTES.size]
}
