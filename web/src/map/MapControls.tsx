import { useEffect, useRef, useState } from "react";
import { ScaleControl, type Map as MlMap } from "maplibre-gl";
import { Sheet, ToggleRow } from "../ui/components";
import { allStyles, resolveStyle, type MapStyle } from "./styles";
import { setPrefs, usePrefs } from "../prefs";

const catEmoji: Record<string, string> = { satellite: "🛰️", streets: "🗺️", terrain: "⛰️", dark: "🌙" };

export function StyleThumb({ s }: { s: MapStyle }) {
  return <div className="thumb">{s.preview ? <img src={s.preview} alt="" /> : catEmoji[s.category]}{s.premium && <span className="hd">HD</span>}</div>;
}

export function MapStyleSheet({ onClose }: { onClose: () => void }) {
  const p = usePrefs();
  const styles = allStyles(p.mapTilerKey, p.customStyleUrl);
  const sel = resolveStyle(p.mapStyle, p.mapTilerKey, p.customStyleUrl);
  return (
    <Sheet title="Map type" onClose={onClose}>
      <div className="style-grid">
        {styles.map((s) => (
          <button key={s.id} className={`style-tile ${s.id === sel.id ? "on" : ""}`} onClick={() => setPrefs({ mapStyle: s.id })}>
            <StyleThumb s={s} /><span className="small ellipsis" style={{ maxWidth: "100%" }}>{s.name}</span>
          </button>
        ))}
      </div>
      <p className="muted small">{sel.description}</p>
      <ToggleRow title="Terrain shading" subtitle={sel.terrainShading ? "Shade hills and valleys" : sel.category === "satellite" ? "Not needed on aerial photos" : "Built into this map type"}
        checked={p.terrain && sel.terrainShading} disabled={!sel.terrainShading} onChange={(v) => setPrefs({ terrain: v })} />
      <ToggleRow title="3D view" subtitle="Tilt the map — buildings and real mountains rise up" checked={p.map3d} onChange={(v) => setPrefs({ map3d: v })} />
    </Sheet>
  );
}

export function MapButtons(p: { map: MlMap | null; onLayers: () => void; zoom: number; maxZoom: number; children?: React.ReactNode; embedded?: boolean }) {
  const prefs = usePrefs();
  const s = resolveStyle(prefs.mapStyle, prefs.mapTilerKey, prefs.customStyleUrl);
  return (
    <div className={`map-btns ${p.embedded ? "embedded" : ""}`}>
      {p.children}
      <FullscreenButton />
      <button className="map-btn" onClick={p.onLayers} aria-label="Map type" title="Map type">
        {s.preview ? <img src={s.preview} alt="" /> : null}<span style={{ position: "relative" }}>🗂️</span>
      </button>
      <div className="zoom-stack">
        <button aria-label="Zoom in" disabled={p.zoom >= p.maxZoom - 0.05} onClick={() => p.map?.easeTo({ zoom: p.map.getZoom() + 1, duration: 250 })}>+</button>
        <button aria-label="Zoom out" onClick={() => p.map?.easeTo({ zoom: p.map.getZoom() - 1, duration: 250 })}>−</button>
      </div>
    </div>
  );
}

/** Full screen for the map's wrapper. The button flips to "Exit full screen" while active. */
function FullscreenButton() {
  const [on, setOn] = useState(() => !!document.fullscreenElement);
  useEffect(() => {
    const sync = () => setOn(!!document.fullscreenElement);
    document.addEventListener("fullscreenchange", sync);
    return () => document.removeEventListener("fullscreenchange", sync);
  }, []);
  const toggle = () => {
    if (document.fullscreenElement) void document.exitFullscreen();
    else void document.querySelector<HTMLElement>(".map-wrap")?.requestFullscreen?.();
  };
  return (
    <button className="map-btn" onClick={toggle} aria-label={on ? "Exit full screen" : "Full screen"} title={on ? "Exit full screen" : "Full screen"}>
      {on ? "✕" : "⛶"}
    </button>
  );
}

/** MapLibre's scale bar, following the unit preference. */
export function useScaleBar(map: MlMap | null) {
  const units = usePrefs().units;
  const ctl = useRef<ScaleControl | null>(null);
  useEffect(() => {
    if (!map) return;
    const c = new ScaleControl({ maxWidth: 100, unit: units === "imperial" ? "imperial" : "metric" });
    map.addControl(c, "bottom-left");
    ctl.current = c;
    return () => { try { map.removeControl(c); } catch { /* map gone */ } };
  }, [map, units]);
}
