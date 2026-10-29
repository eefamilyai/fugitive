// Small spherical-geometry helpers for drawing on the map (the engine does the game maths).
import type { Circle } from "../types";
const R = 6371008.8, rad = (d: number) => (d * Math.PI) / 180, deg = (r: number) => (r * 180) / Math.PI;

export function destination(lat: number, lng: number, bearing: number, dist: number): [number, number] {
  const d = dist / R, b = rad(bearing), p1 = rad(lat), l1 = rad(lng);
  const p2 = Math.asin(Math.sin(p1) * Math.cos(d) + Math.cos(p1) * Math.sin(d) * Math.cos(b));
  const l2 = l1 + Math.atan2(Math.sin(b) * Math.sin(d) * Math.cos(p1), Math.cos(d) - Math.sin(p1) * Math.sin(p2));
  return [((deg(l2) + 540) % 360) - 180, deg(p2)];
}
export function ring(c: Circle, n = 96): [number, number][] {
  return Array.from({ length: n + 1 }, (_, i) => destination(c.lat, c.lng, (i * 360) / n, c.radius));
}
export function distance(a: { lat: number; lng: number }, b: { lat: number; lng: number }) {
  const dp = rad(b.lat - a.lat), dl = rad(b.lng - a.lng);
  const h = Math.sin(dp / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dl / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(Math.min(1, h)));
}
export const circlePolygon = (c: Circle, n = 64) => ({ type: "Polygon" as const, coordinates: [ring(c, n)] });
export const circleLine = (c: Circle, n = 128) => ({ type: "LineString" as const, coordinates: ring(c, n) });
export function worldWithHole(c: Circle) {
  const world: [number, number][] = [[-180, -85], [180, -85], [180, 85], [-180, 85], [-180, -85]];
  return { type: "Polygon" as const, coordinates: [world, ring(c, 128).reverse()] };
}
export function circleBounds(c: Circle): [[number, number], [number, number]] {
  const pts = [0, 90, 180, 270].map((b) => destination(c.lat, c.lng, b, c.radius));
  return [[Math.min(...pts.map((p) => p[0])), Math.min(...pts.map((p) => p[1]))], [Math.max(...pts.map((p) => p[0])), Math.max(...pts.map((p) => p[1]))]];
}
