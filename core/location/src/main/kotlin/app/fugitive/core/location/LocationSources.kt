package app.fugitive.core.location

/**
 * Registry of available location sources. "auto" picks fused when Google Play
 * services is present, otherwise the platform provider.
 */
class LocationSources(
    val fused: FusedLocationSource,
    val platform: PlatformLocationSource,
    val simulated: SimulatedLocationSource,
    extra: List<LocationSource> = emptyList(),
) {
    val all: List<LocationSource> = listOf(fused, platform, simulated) + extra

    fun resolve(preference: String): LocationSource = when (preference) {
        AUTO -> if (fused.isAvailable()) fused else platform
        else -> all.firstOrNull { it.id == preference && it.isAvailable() } ?: resolve(AUTO)
    }

    companion object { const val AUTO = "auto" }
}
