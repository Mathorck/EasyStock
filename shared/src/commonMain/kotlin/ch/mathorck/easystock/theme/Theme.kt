package ch.mathorck.easystock.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Generated from the brand color #485C43 (olive green) with
// material-color-utilities, "fidelity" scheme: primaryContainer is the brand color.

private val LightColors = lightColorScheme(
    primary = Color(0xFF31442D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF485C43),
    onPrimaryContainer = Color(0xFFBCD3B4),
    inversePrimary = Color(0xFFB6CDAE),
    secondary = Color(0xFF586155),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2D3),
    onSecondaryContainer = Color(0xFF5D6559),
    tertiary = Color(0xFF563643),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF6F4D5A),
    onTertiaryContainer = Color(0xFFEDC1D0),
    background = Color(0xFFFBF9F5),
    onBackground = Color(0xFF1B1C1A),
    surface = Color(0xFFFBF9F5),
    onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFE0E4DA),
    onSurfaceVariant = Color(0xFF444841),
    surfaceTint = Color(0xFF50644A),
    inverseSurface = Color(0xFF30312E),
    inverseOnSurface = Color(0xFFF2F1ED),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF747870),
    outlineVariant = Color(0xFFC4C8BE),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF9F5),
    surfaceContainer = Color(0xFFEFEEEA),
    surfaceContainerHigh = Color(0xFFE9E8E4),
    surfaceContainerHighest = Color(0xFFE4E2DE),
    surfaceContainerLow = Color(0xFFF5F3EF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDBDAD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB6CDAE),
    onPrimary = Color(0xFF22351F),
    primaryContainer = Color(0xFF485C43),
    onPrimaryContainer = Color(0xFFBCD3B4),
    inversePrimary = Color(0xFF50644A),
    secondary = Color(0xFFC1C9BA),
    onSecondary = Color(0xFF2B3328),
    secondaryContainer = Color(0xFF434B40),
    onSecondaryContainer = Color(0xFFB2BBAC),
    tertiary = Color(0xFFE7BBCA),
    onTertiary = Color(0xFF452834),
    tertiaryContainer = Color(0xFF6F4D5A),
    onTertiaryContainer = Color(0xFFEDC1D0),
    background = Color(0xFF131412),
    onBackground = Color(0xFFE4E2DE),
    surface = Color(0xFF131412),
    onSurface = Color(0xFFE4E2DE),
    surfaceVariant = Color(0xFF444841),
    onSurfaceVariant = Color(0xFFC4C8BE),
    surfaceTint = Color(0xFFB6CDAE),
    inverseSurface = Color(0xFFE4E2DE),
    inverseOnSurface = Color(0xFF30312E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8E9289),
    outlineVariant = Color(0xFF444841),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF393937),
    surfaceContainer = Color(0xFF1F201E),
    surfaceContainerHigh = Color(0xFF292A28),
    surfaceContainerHighest = Color(0xFF343533),
    surfaceContainerLow = Color(0xFF1B1C1A),
    surfaceContainerLowest = Color(0xFF0D0F0D),
    surfaceDim = Color(0xFF131412),
)

/** App theme. Follows the system dark mode unless [darkTheme] is set. */
@Composable
fun EasyStockTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        // Dynamic colors (Material You, Android 12+) when the platform has them, else the brand schemes.
        colorScheme = platformColorScheme(darkTheme) ?: if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
