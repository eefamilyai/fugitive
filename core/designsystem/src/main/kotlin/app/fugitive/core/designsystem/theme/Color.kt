package app.fugitive.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/** Brand and semantic colours. Role colours match the engine's RoleCatalog. */
object FugitiveColors {
    val Signal = Color(0xFFFF5A36)       // brand / primary
    val SignalDeep = Color(0xFFE0401F)
    val Hunter = Color(0xFFFF3B5C)
    val Runner = Color(0xFF22D3EE)
    val Zone = Color(0xFFA855F7)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Danger = Color(0xFFEF4444)
    val Spectator = Color(0xFF94A3B8)

    val Night0 = Color(0xFF07090D)
    val Night1 = Color(0xFF0B0F15)
    val Night2 = Color(0xFF121822)
    val Night3 = Color(0xFF1A2230)
    val Night4 = Color(0xFF243044)
    val Mist = Color(0xFF8A94A6)

    /** Player colour palette, indexed by Member.color. */
    val players = listOf(
        Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFF59E0B), Color(0xFF84CC16),
        Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFF3B82F6), Color(0xFF6366F1),
        Color(0xFF8B5CF6), Color(0xFFD946EF), Color(0xFFEC4899), Color(0xFF64748B),
    )

    fun player(index: Int): Color = players[((index % players.size) + players.size) % players.size]
}
