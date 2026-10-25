package app.fugitive.engine.role

import app.fugitive.model.Role

/** Display metadata for a role. Colours are ARGB. */
data class RoleInfo(
    val role: Role,
    val label: String,
    val plural: String,
    val color: Long,
    /** Icon key resolved by the UI layer. */
    val icon: String,
)

/**
 * Registry of role display metadata. Modes may relabel roles for flavour
 * (Infection calls hunters "Infected"); custom roles register here.
 */
object RoleCatalog {
    private val roles = linkedMapOf(
        Role.HUNTER to RoleInfo(Role.HUNTER, "Hunter", "Hunters", 0xFFFF3B5C, "hunter"),
        Role.RUNNER to RoleInfo(Role.RUNNER, "Runner", "Runners", 0xFF22D3EE, "runner"),
        Role.SPECTATOR to RoleInfo(Role.SPECTATOR, "Spectator", "Spectators", 0xFF94A3B8, "spectator"),
    )

    fun register(info: RoleInfo) { roles[info.role] = info }

    fun info(role: Role): RoleInfo = roles[role] ?: RoleInfo(role, role.id.replaceFirstChar { it.uppercase() }, role.id + "s", 0xFFA3A3A3, "person")
}
