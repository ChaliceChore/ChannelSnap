package dev.chalicechore.channelsnap.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.chalicechore.channelsnap.data.ThemeMode

// Fallback palette (pre-Android 12, or dynamic colour off): deep teal, matching the launcher icon.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6A70),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA2EFF5),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E9),
    onSecondaryContainer = Color(0xFF051F21),
    tertiary = Color(0xFF4D5F7C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5E3FF),
    onTertiaryContainer = Color(0xFF071C36),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4949),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC8C9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F7F6),
    surfaceContainer = Color(0xFFEEF1F1),
    surfaceContainerHigh = Color(0xFFE8ECEB),
    surfaceContainerHighest = Color(0xFFE3E6E5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF85D3D9),
    onPrimary = Color(0xFF00373A),
    primaryContainer = Color(0xFF004F54),
    onPrimaryContainer = Color(0xFFA2EFF5),
    secondary = Color(0xFFB1CBCD),
    onSecondary = Color(0xFF1C3436),
    secondaryContainer = Color(0xFF324B4D),
    onSecondaryContainer = Color(0xFFCCE8E9),
    tertiary = Color(0xFFB5C7E9),
    onTertiary = Color(0xFF1F314C),
    tertiaryContainer = Color(0xFF364764),
    onTertiaryContainer = Color(0xFFD5E3FF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101414),
    onBackground = Color(0xFFE0E3E3),
    surface = Color(0xFF101414),
    onSurface = Color(0xFFE0E3E3),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC8C9),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4949),
    surfaceContainerLowest = Color(0xFF0B0F0F),
    surfaceContainerLow = Color(0xFF191C1C),
    surfaceContainer = Color(0xFF1D2020),
    surfaceContainerHigh = Color(0xFF272B2B),
    surfaceContainerHighest = Color(0xFF323535),
)

@Composable
fun ChannelSnapTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
