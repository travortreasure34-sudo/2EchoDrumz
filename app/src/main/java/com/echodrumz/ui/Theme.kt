package com.echodrumz.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class EchoTheme(
    val id: String, val label: String, val dark: Boolean,
    val bg: Color, val surface: Color, val surfaceHigh: Color, val text: Color,
    val accent: Color, val pads: List<Color>,
) {
    fun padColor(i: Int) = pads[(i / 4) % pads.size]
}

private fun c(v: Long) = Color(v or 0xFF000000)

fun allThemes(hue: Float): List<EchoTheme> = listOf(
    EchoTheme("midnight", "Midnight Black", true, c(0x000000), c(0x0E0E12), c(0x1A1A22), c(0xEDEDED), c(0x00E5FF),
        listOf(c(0x00B8D4), c(0x7C4DFF), c(0xFF4081), c(0xFFAB00))),
    EchoTheme("graphite", "Graphite", true, c(0x1C1E21), c(0x26292D), c(0x33373C), c(0xE6E8EA), c(0x4FC3F7),
        listOf(c(0x5C7C8A), c(0x7A8F9C), c(0x6B8E7F), c(0xA18A6B))),
    EchoTheme("studio", "Studio Dark", true, c(0x15171C), c(0x1E2128), c(0x2A2E38), c(0xE8E8E8), c(0xFF9800),
        listOf(c(0xFF7043), c(0xFFA726), c(0xFFCA28), c(0x66BB6A))),
    EchoTheme("light", "Light Studio", false, c(0xF4F5F7), c(0xFFFFFF), c(0xE3E6EA), c(0x1B1D21), c(0x2962FF),
        listOf(c(0x2962FF), c(0x00897B), c(0xF57C00), c(0xC2185B))),
    EchoTheme("neon", "Neon", true, c(0x0A0014), c(0x150028), c(0x22003D), c(0xF5E9FF), c(0xFF00E5),
        listOf(c(0xFF00E5), c(0x00FFEA), c(0xAEFF00), c(0xFF6D00))),
    EchoTheme("custom", "Custom", true, c(0x101114), c(0x1A1C20), c(0x272A30), c(0xEDEDED),
        Color.hsv(hue.coerceIn(0f, 359f), 0.75f, 1f),
        List(4) { Color.hsv(((hue + it * 90f) % 360f), 0.7f, 0.95f) }),
)

fun resolveTheme(id: String, hue: Float): EchoTheme = allThemes(hue).firstOrNull { it.id == id } ?: allThemes(hue)[0]

val LocalEcho = compositionLocalOf { allThemes(190f)[0] }

@Composable
fun EchoMaterialTheme(theme: EchoTheme, content: @Composable () -> Unit) {
    val onAccent = if (theme.accent.luminance() > 0.5f) Color.Black else Color.White
    val scheme =
        if (theme.dark) darkColorScheme(
            primary = theme.accent, onPrimary = onAccent, secondary = theme.accent,
            background = theme.bg, onBackground = theme.text, surface = theme.surface, onSurface = theme.text,
            surfaceVariant = theme.surfaceHigh, onSurfaceVariant = theme.text,
        ) else lightColorScheme(
            primary = theme.accent, onPrimary = onAccent, secondary = theme.accent,
            background = theme.bg, onBackground = theme.text, surface = theme.surface, onSurface = theme.text,
            surfaceVariant = theme.surfaceHigh, onSurfaceVariant = theme.text,
        )
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !theme.dark
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalEcho provides theme) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
