package app.fugitive.core.map.internal

import app.fugitive.engine.geo.Geo
import app.fugitive.model.Circle
import app.fugitive.model.GeoPoint
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

internal fun GeoPoint.toPoint(): Point = Point.fromLngLat(lng, lat)

internal fun ring(center: GeoPoint, radius: Double, segments: Int = 96): List<Point> =
    Geo.circleRing(center, radius, segments).map { it.toPoint() }

internal fun circlePolygon(c: Circle, segments: Int = 96): Polygon = Polygon.fromLngLats(listOf(ring(c.center, c.radius, segments)))

internal fun circleOutline(c: Circle, segments: Int = 128): LineString = LineString.fromLngLats(ring(c.center, c.radius, segments))

/** The whole world with a circular hole: used to shade everything outside the zone. */
internal fun worldWithHole(c: Circle): Polygon {
    val world = listOf(
        Point.fromLngLat(-180.0, -85.0), Point.fromLngLat(180.0, -85.0), Point.fromLngLat(180.0, 85.0),
        Point.fromLngLat(-180.0, 85.0), Point.fromLngLat(-180.0, -85.0),
    )
    return Polygon.fromLngLats(listOf(world, ring(c.center, c.radius, 128).reversed()))
}

internal fun featureCollection(features: List<Feature>): FeatureCollection = FeatureCollection.fromFeatures(features)
internal val EMPTY: FeatureCollection = FeatureCollection.fromFeatures(emptyList())
