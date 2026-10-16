package com.brian.dialbuddy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFFE8F0EC)
val InkMuted = Color(0xFFA8B5AE)
val Forest = Color(0xFF101614)
val SurfaceGreen = Color(0xFF1B2622)
val Accent = Color(0xFF8EE0A8)
val OnAccent = Color(0xFF06210F)
val TitleBand = Color(0xFF5E9B78)
val TitleInk = Color(0xFF04140C)
val DateBand = Color(0xFF3E6B54)
val PhoneBand = Color(0xFFC4A15A)
val PhoneInk = Color(0xFF2A2108)
val PhoneStroke = Color(0xFF8C6A32)
val HistoryBand = Color(0xFFC9B8FF)
val HistoryInk = Color(0xFF1A1430)
val HistoryStroke = Color(0xFF6E62A0)
val HistoryBody = Color(0xFF1C1730)
val HistoryWell = Color(0xFF2E2648)
val HistoryText = Color(0xFFF3EEFF)
val HistoryMuted = Color(0xFFC2B6DC)
val NoticeBand = Color(0xFF9FD4FF)
val NoticeInk = Color(0xFF0C1C2A)
val NoticeStroke = Color(0xFF4E7A9A)
val NoticeBody = Color(0xFF12202C)
val NoticeText = Color(0xFFF2F8FF)
val NoticeMuted = Color(0xFFA9C3D6)
val Danger = Color(0xFFFFB4AB)
val OnDanger = Color(0xFF690005)
val Digit = Color(0xFFF7FFF9)
val DigitWindow = Color(0xFF0C1612)
val DigitStroke = Color(0xFF5C8F74)
val OnBrass = Color(0xFF2A2108)
val BrassLight = Color(0xFFFFF0C4)
val BrassMid = Color(0xFFE7C27A)
val BrassDeep = Color(0xFFA67C2D)
val LilacInk = Color(0xFF1A1430)
val LilacLetters = Color(0xFF32285A)
val LilacLight = Color(0xFFE8E0FF)
val LilacDeep = Color(0xFF3E3568)
val GrayInk = Color(0xFFF4F6F8)
val GrayLight = Color(0xFFE6E8EA)
val GrayDeep = Color(0xFF2C3136)
val StatHoney = Color(0xFFE6C36A)
val StatLilac = Color(0xFFC9B8FF)
val StatCoral = Color(0xFFFFB4AB)
val IconWell = Color(0xFF143028)

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    secondary = StatLilac,
    background = Forest,
    surface = SurfaceGreen,
    onBackground = Ink,
    onSurface = Ink,
    surfaceVariant = IconWell,
    onSurfaceVariant = InkMuted,
    error = Danger,
    onError = OnDanger,
)

@Composable
fun DialBuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
