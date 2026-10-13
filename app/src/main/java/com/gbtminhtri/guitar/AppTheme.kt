package com.gbtminhtri.guitar

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Bảng màu lấy cảm hứng từ cây đàn gỗ: hổ phách (amber) trên nền tối ấm.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB347),
    onPrimary = Color(0xFF3A2000),
    primaryContainer = Color(0xFF5A3A12),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFFE08A2E),
    onSecondary = Color(0xFF2E1800),
    secondaryContainer = Color(0xFF3B2A1A),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFF6FD6A0),   // xanh lá: thành công, đúng nốt
    onTertiary = Color(0xFF00391F),
    background = Color(0xFF14181F),
    onBackground = Color(0xFFEDE6DA),
    surface = Color(0xFF14181F),
    onSurface = Color(0xFFEDE6DA),
    surfaceVariant = Color(0xFF232A35),
    onSurfaceVariant = Color(0xFFC9C0B2),
    outline = Color(0xFF5B6574),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0000)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFC2661A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2C2),
    onPrimaryContainer = Color(0xFF3A1D00),
    secondary = Color(0xFF8A5A2B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E3D0),
    onSecondaryContainer = Color(0xFF2E1800),
    tertiary = Color(0xFF1F8A5B),
    onTertiary = Color.White,
    background = Color(0xFFFFFAF3),
    onBackground = Color(0xFF241A10),
    surface = Color(0xFFFFFAF3),
    onSurface = Color(0xFF241A10),
    surfaceVariant = Color(0xFFF1E6D8),
    onSurfaceVariant = Color(0xFF50443A),
    outline = Color(0xFF8C7F72),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val baseTypography = Typography()

private val AppTypography = Typography(
    headlineMedium = baseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = baseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = baseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = baseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = baseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp)
)

/** Theo chế độ sáng/tối của điện thoại, để thanh trạng thái luôn đọc được. */
@Composable
fun GuitarUiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}