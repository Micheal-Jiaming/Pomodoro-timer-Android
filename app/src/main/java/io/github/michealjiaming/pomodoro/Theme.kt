package io.github.michealjiaming.pomodoro

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Hands the palette to Material so the parts this app does not colour by hand —
 * dialog surfaces, the text field in the custom-duration dialog, ripples — come
 * out right. Without this they fall back to Material's default light scheme,
 * which puts dark text on the Black theme's dark dialogs.
 */
@Composable
fun PomodoroTheme(palette: Palette, content: @Composable () -> Unit) {
    val scheme = if (palette.light) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accentBreak,
            onSecondary = palette.onAccent,
            background = palette.bg,
            onBackground = palette.fg,
            surface = palette.card,
            onSurface = palette.fg,
            surfaceVariant = palette.card,
            onSurfaceVariant = palette.muted,
            outline = palette.muted,
            error = palette.accent,
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            secondary = palette.accentBreak,
            onSecondary = palette.onAccent,
            background = palette.bg,
            onBackground = palette.fg,
            surface = palette.card,
            onSurface = palette.fg,
            surfaceVariant = palette.card,
            onSurfaceVariant = palette.muted,
            outline = palette.muted,
            error = palette.accent,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
