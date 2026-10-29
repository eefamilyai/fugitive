import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Avatar, Card, ProfileEditor, SectionHeader, Segmented, Sheet, ToggleRow, Button } from "../ui/components";
import { setPrefs, usePrefs } from "../prefs";
import { sessions } from "../session/manager";
import { appConfig } from "../config";
import { allStyles, resolveStyle } from "../map/styles";
import { StyleThumb } from "../map/MapControls";

export function AppSettings() {
  const p = usePrefs();
  const nav = useNavigate();
  const [edit, setEdit] = useState(false);
  const [key, setKey] = useState(p.mapTilerKey);
  const [custom, setCustom] = useState(p.customStyleUrl);
  const sel = resolveStyle(p.mapStyle, p.mapTilerKey, p.customStyleUrl);
  const setSource = (v: "gps" | "manual") => { setPrefs({ locationSource: v }); sessions.current?.restartLocation(); };
  return (
    <div className="page"><div className="container">
      <div className="topbar"><button className="icon-btn" onClick={() => nav(-1)} aria-label="Back">←</button><h1>Settings</h1></div>
      <Card onClick={() => setEdit(true)}><div className="row"><Avatar emoji={p.avatar} color={p.color} size={56} />
        <div className="grow"><h2>{p.name || "Set your name"}</h2><div className="muted small">Tap to edit your profile</div></div></div></Card>

      <SectionHeader title="Map" />
      <Card>
        <div className="style-grid">{allStyles(p.mapTilerKey, p.customStyleUrl).map((s) => (
          <button key={s.id} className={`style-tile ${s.id === sel.id ? "on" : ""}`} onClick={() => setPrefs({ mapStyle: s.id })}>
            <StyleThumb s={s} /><span className="small ellipsis" style={{ maxWidth: "100%" }}>{s.name}</span></button>))}</div>
        <p className="muted small">{sel.description}</p>
        <ToggleRow title="Terrain shading" subtitle="Shade hills and valleys on street and night maps" checked={p.terrain} onChange={(v) => setPrefs({ terrain: v })} />
        <ToggleRow title="3D view" subtitle="Tilted view with real 3D terrain and buildings" checked={p.map3d} onChange={(v) => setPrefs({ map3d: v })} />
        <div className="col" style={{ marginTop: 8 }}>
          <strong>HD maps (optional)</strong>
          <span className="muted small">Paste a free MapTiler key to unlock HD satellite, outdoor maps with contours, topo and winter maps.</span>
          <div className="row"><input className="text" placeholder="MapTiler API key" value={key} onChange={(e) => setKey(e.target.value.trim())} />
            <Button small kind="secondary" disabled={key === p.mapTilerKey} onClick={() => setPrefs({ mapTilerKey: key })}>Save</Button></div>
          <strong>Custom style URL</strong>
          <div className="row"><input className="text" placeholder="https://…/style.json" value={custom} onChange={(e) => setCustom(e.target.value)} />
            <Button small kind="secondary" disabled={custom === p.customStyleUrl} onClick={() => setPrefs({ customStyleUrl: custom.trim(), mapStyle: custom.trim() ? "custom" : p.mapStyle })}>Apply</Button></div>
        </div>
        <p className="tiny muted">Map data © OpenStreetMap contributors · OpenFreeMap / OpenMapTiles · Imagery © Esri, Maxar, Earthstar Geographics · Elevation: Mapzen/AWS</p>
      </Card>

      <SectionHeader title="General" />
      <Card>
        <strong>Distance units</strong><div style={{ height: 6 }} />
        <Segmented options={[["metric", "Metric (m, km)"], ["imperial", "Imperial (ft, mi)"]]} value={p.units} onChange={(v) => setPrefs({ units: v })} />
        <ToggleRow title="Vibration" subtitle="Heartbeat when hunters are near (Android browsers)" checked={p.haptics} onChange={(v) => setPrefs({ haptics: v })} />
        <ToggleRow title="Sounds" subtitle="Audio cues — useful on iPhone, which can't vibrate from the web" checked={p.sounds} onChange={(v) => setPrefs({ sounds: v })} />
        <ToggleRow title="Keep screen on" subtitle="During games, so the map stays live" checked={p.keepScreenOn} onChange={(v) => setPrefs({ keepScreenOn: v })} />
      </Card>

      <SectionHeader title="Location" />
      <Card>
        <Segmented options={[["gps", "📡 GPS"], ["manual", "👆 Tap map"]]} value={p.locationSource} onChange={setSource} />
        <p className="muted small">GPS uses your phone's location (needs HTTPS and permission). "Tap map" lets you place yourself by tapping — for desktop testing or spectating. Don't use it in real games!</p>
        <p className="muted small">Browsers pause location when the tab is in the background or the screen is off. Keep Fugitive open on screen during a game — or use the Android app, which keeps sharing with the screen off.</p>
      </Card>

      <SectionHeader title="About" />
      <Card>
        {appConfig().privacyPolicyUrl && <a href={appConfig().privacyPolicyUrl} target="_blank" rel="noreferrer">Privacy policy ↗</a>}
        <p className="muted small">Fugitive web · shared rules engine v1 · {sessions.online() ? "Online play enabled" : "Online play not configured"}</p>
        <p className="tiny muted">Open-source: MapLibre GL JS (BSD-3), Firebase JS SDK (Apache-2.0), React (MIT), Kotlin/JS & kotlinx.serialization (Apache-2.0), qrcode (MIT), Space Grotesk (OFL-1.1).</p>
        <Button kind="ghost" small onClick={() => setPrefs({ onboarded: false })}>Replay the introduction</Button>
      </Card>
    </div>
    {edit && <Sheet title="Your profile" onClose={() => setEdit(false)}>
      <ProfileEditor initial={{ name: p.name, avatar: p.avatar, color: p.color }} confirm="Save" onConfirm={(v) => { setPrefs(v); sessions.current?.updateProfile(v); setEdit(false); }} />
    </Sheet>}
    </div>
  );
}
