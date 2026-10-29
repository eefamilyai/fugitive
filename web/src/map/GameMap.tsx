// MapLibre GL map for React: base styles, overlays (zone, trails, objectives, rings, pings) and
// gliding HTML player markers. Overlays are plain props, so screens just describe what to draw.
import { useEffect, useRef, useState } from "react";
import { AttributionControl, Map as MlMap, Marker, setWorkerUrl } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
// MapLibre finds its worker relative to its own file, which breaks once bundled — point it at the real file.
import workerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url";

setWorkerUrl(workerUrl);
import type { Circle, Objective } from "../types";
import { buildStyle, resolveStyle } from "./styles";
import { circleBounds, circleLine, circlePolygon, worldWithHole } from "./geo";

export interface MarkerSpec {
  id: string; lat: number; lng: number; avatar: string; ring: string; fill: string; label?: string;
  bearing?: number; accuracy?: number; look?: "live" | "snapshot" | "stale" | "out"; isMe?: boolean; selected?: boolean;
  badge?: string; glideMs?: number;
}
export interface PinSpec { id: string; lat: number; lng: number; emoji: string; color: string; label?: string; radius?: number }
export interface TrailSpec { id: string; coords: [number, number][]; color: string }

export interface Overlays {
  zone?: Circle | null; nextZone?: Circle | null; zoneDanger?: boolean;
  markers?: MarkerSpec[]; trails?: TrailSpec[]; pins?: PinSpec[];
  ring?: { circle: Circle; color: string } | null; alertRing?: { circle: Circle; color: string } | null;
}

export interface MapApi {
  map: MlMap | null;
  flyTo(lat: number, lng: number, zoom?: number): void;
  fit(points: [number, number][], padding?: number, maxZoom?: number): void;
  fitCircle(c: Circle, padding?: number): void;
  zoomBy(d: number): void;
  resetNorth(): void;
}

interface Props {
  styleId: string; mapTilerKey?: string; customUrl?: string; terrainShading: boolean; tilt3d: boolean;
  overlays: Overlays; className?: string; interactive?: boolean;
  padding?: { top: number; bottom: number; left: number; right: number };
  onReady?: (api: MapApi) => void; onClick?: (lat: number, lng: number) => void; onLongPress?: (lat: number, lng: number) => void;
  onMarkerClick?: (id: string) => void; onUserMove?: () => void; onCamera?: (c: { zoom: number; bearing: number; lat: number; maxZoom: number }) => void;
}

const EMPTY = { type: "FeatureCollection" as const, features: [] as any[] };
const fc = (features: any[]) => ({ type: "FeatureCollection" as const, features });

export function GameMap(p: Props) {
  const el = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MlMap | null>(null);
  const markers = useRef(new Map<string, { m: Marker; el: HTMLDivElement; key: string; from: [number, number]; to: [number, number]; start: number; dur: number }>());
  const props = useRef(p); props.current = p;
  const [styleReady, setStyleReady] = useState(0);
  const [styleError, setStyleError] = useState<string | null>(null);

  // Create the map once.
  useEffect(() => {
    const map = new MlMap({
      container: el.current!, style: { version: 8, sources: {}, layers: [{ id: "bg", type: "background", paint: { "background-color": "#0B0F15" } }] },
      center: [103.8198, 1.3521], zoom: 2, attributionControl: false, maxPitch: 75, interactive: p.interactive ?? true,
    });
    map.addControl(new AttributionControl({ compact: true }), "bottom-left");
    mapRef.current = map;
    map.on("style.load", () => {
      setStyleReady((n) => n + 1);
      // Compact attribution starts expanded; keep it as the small (i) button until tapped.
      const collapse = () => el.current?.querySelector(".maplibregl-ctrl-attrib")?.classList.remove("maplibregl-compact-show");
      collapse(); map.once("idle", collapse);
    });
    map.on("click", (e) => {
      const hit = (e.originalEvent.target as HTMLElement | null)?.closest?.("[data-marker-id]");
      if (hit) return;
      props.current.onClick?.(e.lngLat.lat, e.lngLat.lng);
    });
    map.on("contextmenu", (e) => props.current.onLongPress?.(e.lngLat.lat, e.lngLat.lng));
    let pressTimer: number | undefined;
    map.on("touchstart", (e) => {
      if (e.points.length !== 1) return;
      const ll = e.lngLat;
      pressTimer = window.setTimeout(() => props.current.onLongPress?.(ll.lat, ll.lng), 600);
    });
    ["touchend", "touchmove", "touchcancel", "dragstart"].forEach((ev) => map.on(ev as any, () => window.clearTimeout(pressTimer)));
    map.on("dragstart", () => props.current.onUserMove?.());
    map.on("rotatestart", () => props.current.onUserMove?.());
    const cam = () => props.current.onCamera?.({ zoom: map.getZoom(), bearing: map.getBearing(), lat: map.getCenter().lat, maxZoom: map.getMaxZoom() });
    map.on("move", cam);
    const api: MapApi = {
      map,
      flyTo: (lat, lng, zoom) => map.easeTo({ center: [lng, lat], zoom: zoom ?? map.getZoom(), duration: 700 }),
      fit: (points, padding = 80, maxZoom = 17) => {
        if (!points.length) return;
        const lngs = points.map((q) => q[0]), lats = points.map((q) => q[1]);
        map.fitBounds([[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]], { padding, maxZoom, duration: 0 });
      },
      fitCircle: (c, padding = 40) => map.fitBounds(circleBounds(c), { padding, duration: 0 }),
      zoomBy: (d) => map.easeTo({ zoom: map.getZoom() + d, duration: 250 }),
      resetNorth: () => map.easeTo({ bearing: 0, duration: 400 }),
    };
    p.onReady?.(api);
    const ro = new ResizeObserver(() => map.resize()); ro.observe(el.current!);
    let raf = 0;
    const animate = () => { glide(); raf = requestAnimationFrame(animate); };
    raf = requestAnimationFrame(animate);
    return () => { cancelAnimationFrame(raf); ro.disconnect(); markers.current.forEach((x) => x.m.remove()); markers.current.clear(); map.remove(); mapRef.current = null; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Base style.
  const style = resolveStyle(p.styleId, p.mapTilerKey, p.customUrl);
  useEffect(() => {
    let cancelled = false;
    const map = mapRef.current!;
    map.setMaxZoom(style.maxZoom);
    buildStyle(style, p.terrainShading)
      .then((s) => { if (!cancelled) { setStyleError(null); map.setStyle(s as any, { diff: false }); } })
      .catch(() => { if (!cancelled) { setStyleError("Map type unavailable — showing streets"); buildStyle(resolveStyle("liberty"), false).then((s) => map.setStyle(s as any)); } });
    return () => { cancelled = true; };
  }, [style.id, p.terrainShading, p.mapTilerKey, p.customUrl]);

  // 3D: tilt + real terrain elevation.
  useEffect(() => {
    const map = mapRef.current; if (!map || !styleReady) return;
    try { map.setTerrain(p.tilt3d && map.getSource("fug-dem") ? { source: "fug-dem", exaggeration: 1.3 } : null); } catch { /* style without DEM */ }
    map.easeTo({ pitch: p.tilt3d ? 60 : 0, duration: 600 });
  }, [p.tilt3d, styleReady]);

  useEffect(() => {
    const map = mapRef.current; if (!map || !p.padding) return;
    map.setPadding(p.padding);
  }, [p.padding?.top, p.padding?.bottom, p.padding?.left, p.padding?.right]);

  // Overlay layers (re-added after every style load).
  useEffect(() => {
    const map = mapRef.current; if (!map || !styleReady) return;
    // After 'style.load' sources/layers can be added; isStyleLoaded() would also wait for every tile.
    try { ensureLayers(map); } catch { return; }
    const o = p.overlays;
    const zoneColor = o.zoneDanger ? "#EF4444" : "#A855F7";
    const dark = style.dark;
    setData(map, "fug-zone-shade", o.zone ? fc([{ type: "Feature", geometry: worldWithHole(o.zone), properties: {} }]) : EMPTY);
    setData(map, "fug-zone-edge", o.zone ? fc([{ type: "Feature", geometry: circleLine(o.zone), properties: {} }]) : EMPTY);
    setData(map, "fug-zone-next", o.nextZone ? fc([{ type: "Feature", geometry: circleLine(o.nextZone), properties: {} }]) : EMPTY);
    map.setPaintProperty("fug-zone-shade-l", "fill-color", zoneColor);
    map.setPaintProperty("fug-zone-shade-l", "fill-opacity", dark ? 0.3 : 0.13);
    map.setPaintProperty("fug-zone-edge-l", "line-color", zoneColor);
    map.setPaintProperty("fug-zone-glow-l", "line-color", zoneColor);
    setData(map, "fug-trails", fc((o.trails ?? []).filter((t) => t.coords.length > 1).map((t) => ({ type: "Feature", geometry: { type: "LineString", coordinates: t.coords }, properties: { color: t.color } }))));
    const rings = [o.ring, o.alertRing].filter(Boolean) as { circle: Circle; color: string }[];
    setData(map, "fug-rings", fc(rings.map((r) => ({ type: "Feature", geometry: circlePolygon(r.circle), properties: { color: r.color } }))));
    setData(map, "fug-pins-area", fc((o.pins ?? []).filter((x) => x.radius).map((x) => ({ type: "Feature", geometry: circlePolygon({ lat: x.lat, lng: x.lng, radius: x.radius! }), properties: { color: x.color } }))));
    const acc = (o.markers ?? []).filter((m) => (m.accuracy ?? 0) > 6 && (m.look ?? "live") === "live")
      .map((m) => ({ type: "Feature", geometry: circlePolygon({ lat: m.lat, lng: m.lng, radius: m.accuracy! }, 40), properties: { color: m.isMe ? "#3B82F6" : m.ring } }));
    setData(map, "fug-accuracy", fc(acc));
  }, [p.overlays, styleReady, style.dark]);

  // HTML markers (players + pins).
  useEffect(() => {
    const map = mapRef.current; if (!map) return;
    const want = new Map<string, { spec: MarkerSpec | null; pin: PinSpec | null }>();
    (p.overlays.markers ?? []).forEach((m) => want.set("p:" + m.id, { spec: m, pin: null }));
    (p.overlays.pins ?? []).forEach((x) => want.set("x:" + x.id, { spec: null, pin: x }));
    const now = performance.now();
    for (const [key, entry] of markers.current) if (!want.has(key)) { entry.m.remove(); markers.current.delete(key); }
    for (const [key, { spec, pin }] of want) {
      const pos: [number, number] = spec ? [spec.lng, spec.lat] : [pin!.lng, pin!.lat];
      const visualKey = JSON.stringify(spec ? { ...spec, lat: 0, lng: 0, accuracy: 0, bearing: Math.round((spec.bearing ?? -1) / 10) } : { ...pin, lat: 0, lng: 0 });
      let entry = markers.current.get(key);
      if (!entry) {
        const div = document.createElement("div");
        const m = new Marker({ element: div, anchor: "center" }).setLngLat(pos).addTo(map);
        div.addEventListener("click", (e) => { e.stopPropagation(); props.current.onMarkerClick?.(key.slice(2)); });
        entry = { m, el: div, key: "", from: pos, to: pos, start: now, dur: 0 };
        markers.current.set(key, entry);
      }
      if (entry.key !== visualKey) { entry.el.innerHTML = spec ? playerHtml(spec) : pinHtml(pin!); entry.el.dataset.markerId = key.slice(2); entry.key = visualKey; }
      entry.el.style.zIndex = spec?.isMe ? "30" : spec?.selected ? "25" : spec ? "20" : "10";
      if (entry.to[0] !== pos[0] || entry.to[1] !== pos[1]) {
        const cur = entry.m.getLngLat();
        const far = Math.abs(cur.lng - pos[0]) + Math.abs(cur.lat - pos[1]) > 0.004; // ~400 m: teleport
        if (far || spec?.look === "snapshot" || !spec) { entry.from = pos; entry.to = pos; entry.dur = 0; entry.m.setLngLat(pos); }
        else { entry.from = [cur.lng, cur.lat]; entry.to = pos; entry.start = now; entry.dur = Math.min(2500, Math.max(250, spec?.glideMs ?? 1000)); }
      }
    }
  }, [p.overlays.markers, p.overlays.pins]);

  function glide() {
    const now = performance.now();
    markers.current.forEach((e) => {
      if (e.dur <= 0) return;
      const t = Math.min(1, (now - e.start) / e.dur), k = t * (2 - t);
      e.m.setLngLat([e.from[0] + (e.to[0] - e.from[0]) * k, e.from[1] + (e.to[1] - e.from[1]) * k]);
      if (t >= 1) e.dur = 0;
    });
  }

  return (
    <div className={`map-wrap ${p.className ?? ""}`}>
      <div ref={el} className="map-canvas" />
      {styleError && <div className="map-error">{styleError}</div>}
    </div>
  );
}

function setData(map: MlMap, id: string, data: any) { (map.getSource(id) as any)?.setData(data); }

function ensureLayers(map: MlMap) {
  if (map.getSource("fug-zone-shade")) return;
  for (const id of ["fug-zone-shade", "fug-zone-edge", "fug-zone-next", "fug-trails", "fug-rings", "fug-pins-area", "fug-accuracy"]) {
    map.addSource(id, { type: "geojson", data: EMPTY });
  }
  map.addLayer({ id: "fug-zone-shade-l", type: "fill", source: "fug-zone-shade", paint: { "fill-color": "#A855F7", "fill-opacity": 0.2 } });
  map.addLayer({ id: "fug-zone-glow-l", type: "line", source: "fug-zone-edge", paint: { "line-color": "#A855F7", "line-width": 9, "line-opacity": 0.25 } });
  map.addLayer({ id: "fug-zone-edge-l", type: "line", source: "fug-zone-edge", paint: { "line-color": "#A855F7", "line-width": 3 } });
  map.addLayer({ id: "fug-zone-next-l", type: "line", source: "fug-zone-next", layout: { "line-cap": "round" }, paint: { "line-color": "#FFFFFF", "line-width": 2.5, "line-dasharray": [2, 1.5] } });
  map.addLayer({ id: "fug-pins-area-l", type: "fill", source: "fug-pins-area", paint: { "fill-color": ["get", "color"], "fill-opacity": 0.22 } });
  map.addLayer({ id: "fug-rings-l", type: "fill", source: "fug-rings", paint: { "fill-color": ["get", "color"], "fill-opacity": 0.08 } });
  map.addLayer({ id: "fug-rings-line", type: "line", source: "fug-rings", paint: { "line-color": ["get", "color"], "line-width": 1.8, "line-dasharray": [1.5, 1.5] } });
  map.addLayer({ id: "fug-trails-casing", type: "line", source: "fug-trails", layout: { "line-join": "round", "line-cap": "round" }, paint: { "line-color": "#0B0F15", "line-width": 6, "line-opacity": 0.3 } });
  map.addLayer({ id: "fug-trails-l", type: "line", source: "fug-trails", layout: { "line-join": "round", "line-cap": "round" }, paint: { "line-color": ["get", "color"], "line-width": 3.5, "line-opacity": 0.75 } });
  map.addLayer({ id: "fug-accuracy-l", type: "fill", source: "fug-accuracy", paint: { "fill-color": ["get", "color"], "fill-opacity": 0.12 } });
}

const esc = (s: string) => s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]!));

function playerHtml(m: MarkerSpec) {
  const look = m.look ?? "live";
  const arrow = m.bearing != null && look === "live"
    ? `<div class="pm-heading" style="transform: rotate(${m.bearing}deg)"><span style="border-bottom-color:${m.ring}"></span></div>` : "";
  return `<div class="pm pm-${look}${m.isMe ? " pm-me" : ""}${m.selected ? " pm-sel" : ""}">
    ${arrow}
    <div class="pm-dot" style="background:${m.fill};border-color:${m.ring}">${esc(m.avatar)}${look === "out" ? '<i class="pm-x"></i>' : ""}</div>
    ${m.badge ? `<div class="pm-badge">${esc(m.badge)}</div>` : ""}
    ${m.label ? `<div class="pm-label" style="border-color:${m.ring}">${esc(m.label)}</div>` : ""}
  </div>`;
}
function pinHtml(p: PinSpec) {
  return `<div class="pin"><div class="pin-dot" style="background:${p.color}">${esc(p.emoji)}</div>${p.label ? `<div class="pm-label">${esc(p.label)}</div>` : ""}</div>`;
}

/** Objectives → pins (used by game & results). */
export const objectivePins = (objs: Objective[]): PinSpec[] =>
  objs.map((o) => ({ id: o.id, lat: o.lat, lng: o.lng, emoji: o.type === "extraction" ? "🚁" : "⭐", color: "#FBBF24", label: o.label, radius: o.radius }));
