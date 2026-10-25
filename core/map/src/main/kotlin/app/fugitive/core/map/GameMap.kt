package app.fugitive.core.map

import android.annotation.SuppressLint
import android.graphics.PointF
import android.view.Gravity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.fugitive.model.GeoPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * MapLibre map for Compose.
 *
 * @param style base map (see [MapStyles]).
 * @param layers overlays drawn bottom-to-top; keep instances stable with `remember`.
 * @param contentPadding insets for HUD chrome; the camera centres within the remaining area.
 * @param onFeatureClick receives (layerId, featureId) for taps on clickable layers; return true if handled.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun GameMap(
    style: MapStyle,
    layers: List<MapLayer>,
    camera: MapCameraState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    tilt3d: Boolean = false,
    terrainShading: Boolean = true,
    rotateGestures: Boolean = true,
    onMapClick: (GeoPoint) -> Unit = {},
    onMapLongClick: (GeoPoint) -> Unit = {},
    onFeatureClick: (layerId: String, featureId: String) -> Boolean = { _, _ -> false },
    onUserGesture: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val currentLayers by rememberUpdatedState(layers)
    val clickHandler by rememberUpdatedState(onFeatureClick)
    val mapClick by rememberUpdatedState(onMapClick)
    val longClick by rememberUpdatedState(onMapLongClick)
    val gesture by rememberUpdatedState(onUserGesture)

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    var installedLayers by remember { mutableStateOf<List<MapLayer>>(emptyList()) }
    val ctx = remember(style.dark) { MapContext(context, density.density, style.dark) }

    val composer = remember { StyleComposer.get(context) }
    var styleError by remember { mutableStateOf<String?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapCache.ensureConfigured(context)
        val options = MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true)
            .attributionEnabled(true)
            .logoEnabled(false)
            .compassEnabled(false)
        MapView(context, options).apply { onCreate(null) }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            camera.map = null
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.apply {
                isAttributionEnabled = true
                attributionGravity = Gravity.BOTTOM or Gravity.START
                isRotateGesturesEnabled = rotateGestures
                isTiltGesturesEnabled = true
                isCompassEnabled = false
                isLogoEnabled = false
            }
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) gesture()
            }
            m.addOnCameraMoveListener { camera.syncFrom(m) }
            m.addOnCameraIdleListener { camera.syncFrom(m) }
            m.addOnMapClickListener { latLng ->
                val pt = m.projection.toScreenLocation(latLng)
                val hit = currentLayers.firstNotNullOfOrNull { layer ->
                    layer.clickableLayerIds.firstNotNullOfOrNull { lid ->
                        m.queryRenderedFeatures(PointF(pt.x, pt.y), lid).firstOrNull()?.getStringProperty("id")?.let { lid to it }
                    }
                }
                val handled = hit?.let { (lid, fid) -> clickHandler(lid, fid) } ?: false
                if (!handled) mapClick(GeoPoint(latLng.latitude, latLng.longitude))
                true
            }
            m.addOnMapLongClickListener { latLng -> longClick(GeoPoint(latLng.latitude, latLng.longitude)); true }
            camera.attach(m)
        }
    }

    // (Re)load the style; layers re-install on every load.
    LaunchedEffect(map, style, terrainShading) {
        val m = map ?: return@LaunchedEffect
        m.setMaxZoomPreference(style.maxZoom)
        val resolved = try {
            composer.resolve(style, terrainShading).also { styleError = null }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            styleError = e.message
            ResolvedStyle.Uri((MapStyles.STREETS.source as StyleSource.Url).uri)
        }
        val builder = when (resolved) {
            is ResolvedStyle.Uri -> Style.Builder().fromUri(resolved.uri)
            is ResolvedStyle.Json -> Style.Builder().fromJson(resolved.json)
        }
        m.setStyle(builder) { s ->
            loadedStyle = s
            installedLayers = emptyList()
            camera.syncFrom(m)
        }
    }

    // Camera padding follows the HUD insets.
    LaunchedEffect(map, contentPadding, density) {
        val m = map ?: return@LaunchedEffect
        with(density) {
            m.setPadding(
                contentPadding.calculateLeftPadding(layoutDirection).roundToPx(),
                contentPadding.calculateTopPadding().roundToPx(),
                contentPadding.calculateRightPadding(layoutDirection).roundToPx(),
                contentPadding.calculateBottomPadding().roundToPx(),
            )
        }
    }

    LaunchedEffect(map, tilt3d) {
        val m = map ?: return@LaunchedEffect
        m.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.tiltTo(if (tilt3d) 55.0 else 0.0), 600)
    }

    // Frame loop: install new layers, render dirty or animating ones.
    LaunchedEffect(loadedStyle) {
        val s = loadedStyle ?: return@LaunchedEffect
        var lastRender = 0L
        while (true) {
            withFrameMillis { }
            if (!s.isFullyLoaded) continue
            val now = System.currentTimeMillis()
            val layersNow = currentLayers
            if (layersNow != installedLayers) {
                layersNow.filter { it !in installedLayers }.forEach { runCatching { it.install(s, ctx); it.markDirty() } }
                installedLayers = layersNow
            }
            val animating = layersNow.any { it.isAnimating(now) }
            if (animating && now - lastRender < 33) {
                layersNow.forEach { l -> if (l.consumeDirty()) runCatching { l.render(s, ctx, now) } }
                continue
            }
            lastRender = now
            layersNow.forEach { l ->
                val dirty = l.consumeDirty()
                if (dirty || l.isAnimating(now)) runCatching { l.render(s, ctx, now) }
            }
        }
    }

    androidx.compose.foundation.layout.Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        styleError?.let {
            androidx.compose.material3.Text(
                "Map type unavailable offline — showing streets",
                color = androidx.compose.ui.graphics.Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                    .background(androidx.compose.ui.graphics.Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
