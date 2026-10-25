package app.fugitive.core.map

import android.content.Context
import org.maplibre.android.maps.Style

/** Context handed to layers: Android context, density and the current style's darkness. */
class MapContext(val context: Context, val density: Float, val darkStyle: Boolean)

/**
 * A self-contained map overlay. To add something to the map, write a MapLayer
 * and pass it to [GameMap] — layers own their sources, style layers and icons.
 *
 * Life-cycle: [install] after every style (re)load, then [render] whenever
 * [isDirty] or [isAnimating], on the main thread at up to 30 fps.
 */
abstract class MapLayer(val id: String) {
    @Volatile private var dirty = true

    /** Mark data as changed; the map re-renders on the next frame. */
    protected fun invalidate() { dirty = true }

    internal fun consumeDirty(): Boolean { val d = dirty; dirty = false; return d }
    internal fun markDirty() { dirty = true }

    /** Style layer ids that respond to taps (features need an "id" property). */
    open val clickableLayerIds: List<String> = emptyList()

    /** True while the layer wants per-frame renders (e.g. marker interpolation). */
    open fun isAnimating(nowMs: Long): Boolean = false

    abstract fun install(style: Style, ctx: MapContext)
    abstract fun render(style: Style, ctx: MapContext, nowMs: Long)

    /** Prefix for source/layer ids owned by this layer. */
    protected fun sid(name: String) = "fug-$id-$name"
}
