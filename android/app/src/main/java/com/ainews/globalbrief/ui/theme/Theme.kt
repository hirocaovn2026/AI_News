package com.ainews.globalbrief.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val InkNavy = Color(0xFF0F172A)
val EditorialSlate = Color(0xFF334155)
val PaperCream = Color(0xFFF8F7F4)
val PaperCard = Color(0xFFFFFFFF)
val PaperBorder = Color(0xFFE2E8F0)

val DarkSurface = Color(0xFF0B1120)
val DarkCard = Color(0xFF141E33)
val DarkCardAlt = Color(0xFF1B2740)
val DarkBorder = Color(0xFF263554)

val EnglishBadgeBgLight = Color(0xFFE0ECFF)
val EnglishBadgeFgLight = Color(0xFF1D4ED8)
val EnglishBadgeBgDark = Color(0xFF1E3A5F)
val EnglishBadgeFgDark = Color(0xFF93C5FD)

val VietnameseBadgeBgLight = Color(0xFFDCFCE7)
val VietnameseBadgeFgLight = Color(0xFF15803D)
val VietnameseBadgeBgDark = Color(0xFF144232)
val VietnameseBadgeFgDark = Color(0xFF86EFAC)

private val LightColors = lightColorScheme(
    primary = Color(0xFF102542),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    background = PaperCream,
    onBackground = InkNavy,
    surface = PaperCard,
    onSurface = InkNavy,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = EditorialSlate,
    outline = PaperBorder,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF06162E),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFE2E8F0),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF042F2E),
    background = DarkSurface,
    onBackground = Color(0xFFF1F5F9),
    surface = DarkCard,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = DarkCardAlt,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = DarkBorder,
)

private val EditorialTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 29.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
)

fun topicAccentColor(topicId: String, hexFallback: String? = null): Color {
    return when (topicId) {
        "ai" -> Color(0xFF2563EB)
        "space" -> Color(0xFF7C3AED)
        "climate" -> Color(0xFF059669)
        "conflict" -> Color(0xFFDC2626)
        else -> {
            runCatching {
                if (!hexFallback.isNullOrBlank()) {
                    Color(android.graphics.Color.parseColor(hexFallback))
                } else {
                    Color(0xFF2563EB)
                }
            }.getOrDefault(Color(0xFF2563EB))
        }
    }
}

@Composable
fun GlobalBriefTheme(
    darkThemeOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val isDark = darkThemeOverride ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (isDark) DarkColors else LightColors,
        typography = EditorialTypography,
        content = content
    )
}
