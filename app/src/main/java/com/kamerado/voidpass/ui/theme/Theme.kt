package com.kamerado.voidpass.ui.theme


import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

// ── Palette ──────────────────────────────────────────────────────────────────
// Deep charcoal backgrounds, cold blue-grey accents.
// Feels like a secure terminal — not a cute consumer app.

val BackgroundDeep   = Color(0xFF0A0C0F)   // near-black with a cool blue tint
val BackgroundCard   = Color(0xFF111519)   // card surfaces
val BackgroundInput  = Color(0xFF181D23)   // input fields
val SurfaceElevated  = Color(0xFF1C2128)   // slightly lifted surface

val AccentBlue       = Color(0xFF4A9EFF)   // primary interactive colour
val AccentBlueDim    = Color(0xFF1E3A5F)   // muted accent for backgrounds
val AccentGreen      = Color(0xFF3DDC84)   // success / unlocked
val AccentRed        = Color(0xFFFF5370)   // error / danger

val TextPrimary      = Color(0xFFE8EDF2)   // main readable text
val TextSecondary    = Color(0xFF8892A0)   // subdued labels
val TextDisabled     = Color(0xFF3D4754)   // placeholder / disabled
val TextBlack        = Color(0xFFFFFFFF)

val Divider          = Color(0xFF1E2730)   // subtle separators

// ── Color scheme ─────────────────────────────────────────────────────────────

private val DarkColors = darkColorScheme(
    primary          = AccentBlue,
    onPrimary        = Color(0xFF001433),
    primaryContainer = AccentBlueDim,
    secondary        = AccentGreen,
    background       = BackgroundDeep,
    surface          = BackgroundCard,
    surfaceVariant   = SurfaceElevated,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error            = AccentRed,
    outline          = Color(0xFF2A3340),
    outlineVariant   = Divider,
)

// ── Typography ────────────────────────────────────────────────────────────────
// System default sans for UI, monospace for passwords/secrets.
// We avoid loading custom font files to keep the APK lean —
// the system monospace is Roboto Mono on most Android devices.

val MonoFamily = FontFamily.Monospace

private val VaultTypography = Typography(
    // Large titles (e.g. "VAULT" on unlock screen)
    displayLarge = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.W300,
        fontSize    = 48.sp,
        letterSpacing = 8.sp,
        color       = TextPrimary,
    ),
    // Screen headings
    headlineMedium = TextStyle(
        fontFamily  = FontFamily.SansSerif,
        fontWeight  = FontWeight.W500,
        fontSize    = 20.sp,
        letterSpacing = 0.15.sp,
        color       = TextPrimary,
    ),
    // Body text, entry titles
    bodyLarge = TextStyle(
        fontFamily  = FontFamily.SansSerif,
        fontWeight  = FontWeight.Normal,
        fontSize    = 16.sp,
        color       = TextPrimary,
    ),
    // Subtitles, usernames, metadata
    bodyMedium = TextStyle(
        fontFamily  = FontFamily.SansSerif,
        fontWeight  = FontWeight.Normal,
        fontSize    = 14.sp,
        color       = TextSecondary,
    ),
    // Small labels
    labelSmall = TextStyle(
        fontFamily  = FontFamily.Monospace,
        fontWeight  = FontWeight.Normal,
        fontSize    = 11.sp,
        letterSpacing = 1.sp,
        color       = TextSecondary,
    ),
)

// ── Theme composable ──────────────────────────────────────────────────────────

@Composable
fun VaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography  = VaultTypography,
        content     = content,
    )
}