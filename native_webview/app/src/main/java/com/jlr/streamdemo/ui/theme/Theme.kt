package com.jlr.streamdemo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JLRDark = darkColorScheme(
    primary = Color(0xFFE8B84B),       // JLR gold
    onPrimary = Color(0xFF1A1A2E),
    background = Color(0xFF0D0D0D),
    surface = Color(0xFF1A1A1A),
    onBackground = Color(0xFFEEEEEE),
    onSurface = Color(0xFFEEEEEE),
)

@Composable
fun JLRStreamDemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JLRDark,
        content = content,
    )
}
