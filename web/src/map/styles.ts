// Base maps — the same catalogue as the Android app (core/map MapStyles + StyleComposer).
// Defaults need no API key: OpenFreeMap vector tiles, Esri imagery/topo, AWS Terrain Tiles for elevation.
import type { StyleSpecification } from "maplibre-gl";

export type StyleCategory = "satellite" | "streets" | "terrain" | "dark";
type Recipe =
  | { kind: "url"; url: string }
  | { kind: "imagery"; tiles: string; attribution: string; maxzoom: number; labels: boolean }
  | { kind: "shaded"; base: string; exaggeration: number; dark: boolean }
  | { kind: "raster"; tiles: string; attribution: string; maxzoom: number };

export interface MapStyle {
  id: string; name: string; description: string; category: StyleCategory; dark: boolean; maxZoom: number;
  recipe: Recipe; terrainShading: boolean; preview?: string; premium?: boolean;
}

const OFM = "https://tiles.openfreemap.org/styles";
const ESRI_IMAGERY = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}";
const ESRI_TOPO = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}";
const ESRI_ATTR = "Imagery © Esri, Maxar, Earthstar Geographics, and the GIS User Community";
export const TERRARIUM = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png";
const GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf";
const pv = (id: string) => `${import.meta.env.BASE_URL}map-previews/${id}.webp`;

export const STYLES: MapStyle[] = [
  { id: "hybrid", name: "Satellite", description: "Aerial photos with roads and names", category: "satellite", dark: true, maxZoom: 19.6, terrainShading: false, preview: pv("hybrid"),
    recipe: { kind: "imagery", tiles: ESRI_IMAGERY, attribution: ESRI_ATTR, maxzoom: 19, labels: true } },
  { id: "satellite", name: "Satellite (clean)", description: "Pure aerial imagery, no labels", category: "satellite", dark: true, maxZoom: 19.6, terrainShading: false, preview: pv("satellite"),
    recipe: { kind: "imagery", tiles: ESRI_IMAGERY, attribution: ESRI_ATTR, maxzoom: 19, labels: false } },
  { id: "liberty", name: "Streets 3D", description: "Detailed streets, paths and 3D buildings", category: "streets", dark: false, maxZoom: 21, terrainShading: true, preview: pv("liberty"),
    recipe: { kind: "url", url: `${OFM}/liberty` } },
  { id: "terrain", name: "Terrain", description: "Streets with strong hill shading", category: "terrain", dark: false, maxZoom: 21, terrainShading: false, preview: pv("terrain"),
    recipe: { kind: "shaded", base: `${OFM}/liberty`, exaggeration: 0.6, dark: false } },
  { id: "topo", name: "Topographic", description: "Contours, trails and landmarks", category: "terrain", dark: false, maxZoom: 20, terrainShading: false, preview: pv("topo"),
    recipe: { kind: "raster", tiles: ESRI_TOPO, attribution: "Map © Esri, HERE, Garmin, USGS, NGA, OpenStreetMap contributors", maxzoom: 19 } },
  { id: "bright", name: "Bright", description: "High-contrast classic map", category: "streets", dark: false, maxZoom: 21, terrainShading: true, preview: pv("bright"), recipe: { kind: "url", url: `${OFM}/bright` } },
  { id: "positron", name: "Minimal", description: "Clean and quiet — players stand out", category: "streets", dark: false, maxZoom: 21, terrainShading: true, preview: pv("positron"), recipe: { kind: "url", url: `${OFM}/positron` } },
  { id: "dark", name: "Night", description: "Dark map, easy on the eyes at night", category: "dark", dark: true, maxZoom: 21, terrainShading: true, preview: pv("dark"), recipe: { kind: "url", url: `${OFM}/dark` } },
  { id: "fiord", name: "Midnight", description: "Moody blue dark map", category: "dark", dark: true, maxZoom: 21, terrainShading: true, preview: pv("fiord"), recipe: { kind: "url", url: `${OFM}/fiord` } },
];

export function mapTilerStyles(key: string): MapStyle[] {
  if (!key.trim()) return [];
  const mt = (id: string, name: string, description: string, category: StyleCategory, dark: boolean): MapStyle => ({
    id: `mt-${id}`, name, description, category, dark, maxZoom: 22, terrainShading: false, premium: true,
    recipe: { kind: "url", url: `https://api.maptiler.com/maps/${id}/style.json?key=${key.trim()}` },
  });
  return [
    mt("hybrid", "Satellite HD", "MapTiler high-resolution imagery", "satellite", true),
    mt("outdoor-v2", "Outdoor", "Contours, trails, hill shading", "terrain", false),
    mt("topo-v2", "Topo", "Detailed topographic map", "terrain", false),
    mt("streets-v2", "Streets HD", "MapTiler streets with 3D buildings", "streets", false),
    mt("winter-v2", "Winter", "Ski & snow outdoor map", "terrain", false),
    mt("streets-v2-dark", "Streets Dark", "MapTiler dark streets", "dark", true),
  ];
}

export function allStyles(mapTilerKey = "", customUrl = ""): MapStyle[] {
  const custom: MapStyle[] = customUrl.trim()
    ? [{ id: "custom", name: "Custom", description: customUrl, category: "streets", dark: false, maxZoom: 22, terrainShading: false, recipe: { kind: "url", url: customUrl.trim() } }]
    : [];
  return [...STYLES, ...mapTilerStyles(mapTilerKey), ...custom];
}
export function resolveStyle(id: string, mapTilerKey = "", customUrl = ""): MapStyle {
  return allStyles(mapTilerKey, customUrl).find((s) => s.id === id) ?? STYLES[0];
}

const cache = new Map<string, Promise<any>>();
function fetchJson(url: string) {
  if (!cache.has(url)) cache.set(url, fetch(url).then((r) => { if (!r.ok) throw new Error(`style ${r.status}`); return r.json(); }));
  return cache.get(url)!;
}

const demSource = () => ({ type: "raster-dem", tiles: [TERRARIUM], tileSize: 256, maxzoom: 15, encoding: "terrarium", attribution: "Elevation: Mapzen Terrain Tiles (AWS Open Data)" });

/** Builds the MapLibre style document for [s]; [shading] adds hillshade to vector styles. Always includes a DEM source for 3D terrain. */
export async function buildStyle(s: MapStyle, shading: boolean): Promise<StyleSpecification | string> {
  const r = s.recipe;
  if (r.kind === "url" && !(shading && s.terrainShading)) {
    if (r.url.startsWith(OFM)) return withDem(structuredClone(await fetchJson(r.url)));
    return r.url;
  }
  if (r.kind === "url") return shaded(r.url, 0.35, s.dark);
  if (r.kind === "shaded") return shaded(r.base, r.exaggeration, r.dark);
  if (r.kind === "raster") {
    return withDem({
      version: 8, glyphs: GLYPHS,
      sources: { raster: { type: "raster", tiles: [r.tiles], tileSize: 256, maxzoom: r.maxzoom, attribution: r.attribution } },
      layers: [{ id: "fug-bg", type: "background", paint: { "background-color": "#DDDDDD" } }, { id: "raster", type: "raster", source: "raster" }],
    } as any);
  }
  // imagery
  const style: any = {
    version: 8, glyphs: GLYPHS,
    sources: { imagery: { type: "raster", tiles: [r.tiles], tileSize: 256, maxzoom: r.maxzoom, attribution: r.attribution } },
    layers: [
      { id: "fug-bg", type: "background", paint: { "background-color": "#0B0F15" } },
      { id: "imagery", type: "raster", source: "imagery", paint: { "raster-fade-duration": 150, "raster-contrast": 0.08, "raster-saturation": 0.05 } },
    ],
  };
  if (r.labels) {
    try {
      const base = await fetchJson(`${OFM}/liberty`);
      if (base.sprite) style.sprite = base.sprite;
      style.sources.openmaptiles = base.sources.openmaptiles;
      for (const l0 of base.layers) {
        if (l0.source !== "openmaptiles") continue;
        const l = structuredClone(l0);
        const sl = l["source-layer"], id: string = l.id;
        l.paint ??= {};
        if (l.type === "line" && sl === "transportation") {
          if (id.includes("casing") || id.includes("hatching")) continue;
          const minor = /path|service|minor|track/.test(id);
          l.paint["line-opacity"] = minor ? 0.45 : 0.6;
          if (id.includes("path")) l.paint["line-color"] = "#FFF3D6";
          style.layers.push(l);
        } else if (l.type === "line" && sl === "boundary") style.layers.push(l);
        else if (l.type === "symbol") {
          if (l.layout?.["text-field"]) {
            l.paint["text-color"] = sl === "water_name" ? "#CDE8FF" : "#FFFFFF";
            l.paint["text-halo-color"] = "rgba(0,0,0,0.85)"; l.paint["text-halo-width"] = 1.4; l.paint["text-halo-blur"] = 0.4;
          }
          style.layers.push(l);
        }
      }
    } catch { /* labels unavailable: imagery only */ }
  }
  return withDem(style);
}

function withDem(style: any) { style.sources["fug-dem"] = demSource(); return style; }

async function shaded(url: string, exaggeration: number, dark: boolean) {
  const base = structuredClone(await fetchJson(url));
  base.sources["fug-dem"] = demSource();
  const hill = {
    id: "fug-hillshade", type: "hillshade", source: "fug-dem",
    paint: {
      "hillshade-exaggeration": exaggeration, "hillshade-shadow-color": dark ? "#000000" : "#3E3426",
      "hillshade-highlight-color": dark ? "#2A3342" : "#FFFFFF", "hillshade-accent-color": dark ? "#05070A" : "#6B5E4B",
      "hillshade-illumination-anchor": "viewport",
    },
  };
  const at = base.layers.findIndex((l: any) => l["source-layer"] === "transportation" || l["source-layer"] === "building" || l.type === "symbol");
  base.layers.splice(at < 0 ? base.layers.length : at, 0, hill);
  return base;
}
