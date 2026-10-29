import type React from "react";
// Game settings editor, generated entirely from the shared engine's settings schema.
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Card, Dialog, Notice, SectionHeader, Switch, categoryIcon, modeIcon } from "../ui/components";
import { useManager, useSession } from "../session/manager";
import { engine } from "../engine";
import { setPrefs, usePrefs } from "../prefs";
import { settingValue } from "../util/format";
import { GameMap, type MapApi } from "../map/GameMap";
import { MapButtons, MapStyleSheet } from "../map/MapControls";
import type { FormSetting, GameConfig, Json } from "../types";

export function GameSettingsScreen() {
  const m = useManager();
  const session = m.kind === "active" ? m.session : null;
  const lobby = useSession(session, (s) => s.lobby);
  const locs = useSession(session, (s) => s.locations) ?? {};
  const nav = useNavigate();
  const [draft, setDraft] = useState<GameConfig | null>(null);
  const dirty = useRef(false);
  const [advanced, setAdvanced] = useState(false);
  const [picking, setPicking] = useState(false);
  const [reset, setReset] = useState(false);
  const isHost = !!lobby && lobby.info.hostId === session?.selfId;

  useEffect(() => { if (lobby && !dirty.current) setDraft(lobby.config); }, [lobby?.config]);
  useEffect(() => {
    if (!draft || !dirty.current || !isHost) return;
    const t = setTimeout(() => { session?.updateConfig(draft); setPrefs({ lastConfig: draft }); dirty.current = false; }, 350);
    return () => clearTimeout(t);
  }, [draft]);

  const config = draft ?? lobby?.config;
  const form = useMemo(() => (config ? engine.settingsForm(config) : null), [config]);
  if (!session || !lobby || !config || !form) return null;
  const issues = engine.validate({ ...lobby, config }, locs, Object.values(lobby.members).filter((x) => x.online).map((x) => x.id));
  const highlight = new Set(issues.filter((i) => i.blocking).map((i) => i.settingKey));
  const edit = (f: (c: GameConfig) => GameConfig) => { if (!isHost) return; dirty.current = true; setDraft(f(config)); };
  const set = (key: string, v: Json) => edit((c) => engine.setSetting(c, key, v));

  return (
    <div className="page"><div className="container">
      <div className="topbar">
        <button className="icon-btn" onClick={() => nav("/lobby")} aria-label="Back">←</button>
        <h1 className="grow">{isHost ? "Game settings" : "Game rules"}</h1>
        {isHost && <button className="icon-btn" onClick={() => setReset(true)} aria-label="Reset">↺</button>}
      </div>
      {!isHost && <Notice title="Only the host can change the rules" body="Changes the host makes show up here live." icon="🔒" />}
      {issues.map((i) => <Notice key={i.message} title={i.message} icon={i.blocking ? "⛔" : "⚠️"} color={i.blocking ? "var(--danger)" : "var(--warning)"} />)}

      <SectionHeader title="Quick presets" />
      <div className="row" style={{ overflowX: "auto", paddingBottom: 6, alignItems: "stretch" }}>
        {engine.catalog.presets.map((p) => (
          <button key={p.id} className={`option ${config.presetId === p.id ? "on" : ""}`} disabled={!isHost}
            style={{ flexDirection: "column", minWidth: 156, maxWidth: 156, gap: 4 }} onClick={() => edit(() => engine.applyPreset(p.id))}>
            <span style={{ fontSize: 26 }}>{p.emoji}</span><strong className="ellipsis" style={{ maxWidth: "100%" }}>{p.name}</strong>
            <span className="muted small">{p.description}</span>
          </button>
        ))}
      </div>

      <SectionHeader title="Game mode" />
      {engine.catalog.modes.map((md) => (
        <button key={md.id} className={`option ${config.modeId === md.id ? "on" : ""}`} disabled={!isHost} onClick={() => edit((c) => engine.switchMode(c, md.id))}
          style={{ alignItems: "center", ["--signal" as string]: md.accent }}>
          <span className="role-icon" style={{ background: `${md.accent}2e`, borderRadius: 12 }}>{modeIcon(md.icon)}</span>
          <div className="grow"><strong>{md.name}</strong><div className="muted small">{md.tagline}</div></div>
          {config.modeId === md.id && <span style={{ color: md.accent }}>✔</span>}
        </button>
      ))}

      <label className="row" style={{ marginTop: 8 }}>
        <div className="grow"><strong>Show advanced settings</strong><div className="muted small">Fine-tune fairness, trails, timings and more</div></div>
        <Switch checked={advanced} onChange={setAdvanced} />
      </label>

      {form.categories.map((cat) => {
        const items = cat.settings.filter((s) => s.visible && (advanced || !s.advanced));
        if (!items.length) return null;
        return (
          <Card key={cat.id}>
            <div className="row" style={{ marginBottom: 8 }}><span style={{ fontSize: 20 }}>{categoryIcon(cat.icon)}</span><h3>{cat.title}</h3></div>
            {items.map((s, i) => (
              <div key={s.key} style={{ paddingLeft: s.nested ? 18 : 0, borderTop: i > 0 && !s.nested ? "1px solid var(--outline)" : "none", paddingTop: 6, paddingBottom: 6 }}>
                <SettingEditor s={s} editable={isHost} error={highlight.has(s.key)} onSet={(v) => set(s.key, v)} onPick={() => setPicking(true)} />
              </div>
            ))}
          </Card>
        );
      })}
    </div>
    {picking && <ZonePicker initial={String(form.values["zone_center_point"] ?? "")} radius={Number(form.values["zone_start_radius_m"] ?? 800)}
      fallback={locs[session.selfId]} onClose={() => setPicking(false)}
      onPick={(lat, lng) => { edit((c) => engine.setSetting(engine.setSetting(c, "zone_center_point", `${lat.toFixed(6)},${lng.toFixed(6)}`), "zone_center", "custom")); setPicking(false); }} />}
    {reset && <Dialog title="Reset settings?" onClose={() => setReset(false)}
      actions={<><Button kind="ghost" onClick={() => setReset(false)}>Cancel</Button><Button kind="ghost" onClick={() => { edit((c) => engine.resetConfig(c)); setReset(false); }}>Reset</Button></>}>
      <p className="muted">All options return to the defaults for this game mode.</p></Dialog>}
    </div>
  );
}

function SettingEditor({ s, editable, error, onSet, onPick }: { s: FormSetting; editable: boolean; error: boolean; onSet: (v: Json) => void; onPick: () => void }) {
  const title = (value?: string) => (
    <div className="grow">
      <div className="row" style={{ gap: 8 }}><strong style={{ color: error ? "var(--danger)" : undefined, fontWeight: 600 }}>{s.title}</strong>
        {value && <span style={{ color: "var(--signal)", fontWeight: 700 }}>{value}</span>}</div>
      {s.description && <div className="muted small">{s.description}</div>}
    </div>
  );
  if (s.type === "toggle") return <label className="row">{title()}<Switch checked={!!s.value} disabled={!editable} onChange={onSet} /></label>;
  if (s.type === "point") return <div className="row">{title(s.display)}<Button kind="secondary" small disabled={!editable} onClick={onPick}>📍 {s.value ? "Change" : "Pick"}</Button></div>;
  if (s.type === "choice") return (
    <div className="col" style={{ gap: 6 }}>{title()}
      {s.options.map((o) => (
        <button key={o.id} className={`option ${o.id === s.value ? "on" : ""}`} disabled={!editable} onClick={() => onSet(o.id)}>
          <span className="radio" /><div><div style={{ fontWeight: o.id === s.value ? 600 : 400 }}>{o.label}</div>{o.description && <div className="muted small">{o.description}</div>}</div>
        </button>
      ))}
    </div>
  );
  return <NumberEditor s={s} editable={editable} title={title} onSet={onSet} />;
}

/** Slider with log/sqrt scaling for wide ranges, mirroring the Android editor. */
function NumberEditor({ s, editable, title, onSet }: { s: FormSetting; editable: boolean; title: (v?: string) => React.ReactNode; onSet: (v: Json) => void }) {
  const min = s.min ?? 0, max = s.max ?? 100, step = s.step ?? 1;
  const mode = min > 0 && max / min >= 20 ? "log" : min === 0 && max >= 120 ? "sqrt" : "lin";
  const toPos = (v: number) => mode === "log" ? Math.log(v / min) / Math.log(max / min) : mode === "sqrt" ? Math.sqrt((v - min) / (max - min)) : (v - min) / Math.max(1, max - min);
  const toVal = (p: number) => {
    const raw = mode === "log" ? min * Math.exp(p * Math.log(max / min)) : mode === "sqrt" ? min + (max - min) * p * p : min + (max - min) * p;
    const st = raw >= 5000 ? Math.max(step, 250) : raw >= 1000 ? Math.max(step, 50) : raw >= 200 && step < 10 && max >= 1000 ? 10 : step;
    return Math.min(max, Math.max(min, Math.round(raw / st) * st));
  };
  const value = Number(s.value);
  const [drag, setDrag] = useState<number | null>(null);
  const shown = drag ?? value;
  const fmt = (v: number) => s.specialLabels[String(v)] ?? settingValue(v, s.unit);
  return (
    <div className="col" style={{ gap: 4 }}>
      {title(fmt(shown))}
      <input type="range" min={0} max={1000} value={Math.round(toPos(shown) * 1000)} disabled={!editable}
        onChange={(e) => setDrag(toVal(Number(e.target.value) / 1000))}
        onPointerUp={() => { if (drag != null) onSet(drag); setDrag(null); }} onKeyUp={() => { if (drag != null) onSet(drag); setDrag(null); }} />
      {s.suggestions.length > 0 && <div className="chips">{s.suggestions.filter((x) => x >= min && x <= max).map((x) => (
        <button key={x} className={`chip ${x === value ? "on" : ""}`} disabled={!editable} onClick={() => onSet(x)}>{fmt(x)}</button>
      ))}</div>}
    </div>
  );
}

function ZonePicker(p: { initial: string; radius: number; fallback?: { lat: number; lng: number }; onClose: () => void; onPick: (lat: number, lng: number) => void }) {
  const prefs = usePrefs();
  const init = p.initial.split(",").map(Number);
  const [c, setC] = useState<{ lat: number; lng: number } | null>(init.length === 2 && !init.some(isNaN) ? { lat: init[0], lng: init[1] } : p.fallback ?? null);
  const [api, setApi] = useState<MapApi | null>(null);
  const [styles, setStyles] = useState(false);
  const [cam, setCam] = useState({ zoom: 0, maxZoom: 22 });
  const [framed, setFramed] = useState(false);
  if (api && c && !framed) { api.fitCircle({ ...c, radius: p.radius * 1.15 }); setFramed(true); }
  return (
    <div style={{ position: "fixed", inset: 0, zIndex: 120, background: "var(--n1)" }}>
      <GameMap styleId={prefs.mapStyle} mapTilerKey={prefs.mapTilerKey} customUrl={prefs.customStyleUrl} terrainShading={prefs.terrain} tilt3d={false}
        className="fill" onReady={setApi} onClick={(lat, lng) => setC({ lat, lng })} onCamera={(x) => setCam({ zoom: x.zoom, maxZoom: x.maxZoom })}
        overlays={{ zone: c ? { ...c, radius: p.radius } : null, pins: c ? [{ id: "c", lat: c.lat, lng: c.lng, emoji: "🎯", color: "#A855F7", label: "Centre" }] : [] }} />
      <div className="hud-top"><div className="glass row"><div className="grow"><h3>Zone centre</h3><div className="muted small">Tap the map to move it.</div></div>
        <button className="icon-btn" onClick={p.onClose} aria-label="Cancel">✕</button></div></div>
      <MapButtons map={api?.map ?? null} onLayers={() => setStyles(true)} zoom={cam.zoom} maxZoom={cam.maxZoom} />
      <div className="hud-bottom"><Button block disabled={!c} onClick={() => c && p.onPick(c.lat, c.lng)}>Set centre here</Button></div>
      {styles && <MapStyleSheet onClose={() => setStyles(false)} />}
    </div>
  );
}
