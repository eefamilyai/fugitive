import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Avatar, Button, Card, Dialog, Notice, Pill, QrCode, SectionHeader, Sheet, modeIcon, roleColor, roleIcon } from "../ui/components";
import { sessions, useManager, useSession } from "../session/manager";
import { engine } from "../engine";
import { usePrefs } from "../prefs";
import { distance, settingValue } from "../util/format";
import { distance as geoDistance } from "../map/geo";
import { GameMap, type MapApi, type MarkerSpec } from "../map/GameMap";
import { MapButtons, MapStyleSheet } from "../map/MapControls";
import { ChatPanel } from "./Chat";
import { playerColor } from "../util/format";
import type { Lobby, LocationFix } from "../types";

export function lobbyMarkers(l: Lobby, locs: Record<string, LocationFix>, selfId: string): MarkerSpec[] {
  return Object.values(l.members).flatMap((m) => {
    const f = locs[m.id]; if (!f) return [];
    const role = l.roster[m.id];
    return [{ id: m.id, lat: f.lat, lng: f.lng, avatar: m.avatar, ring: role ? roleColor(role) : "#8A94A6", fill: playerColor(m.color),
      label: m.id === selfId ? "You" : m.name, bearing: f.bearing, accuracy: f.accuracy, isMe: m.id === selfId }];
  });
}

export function LobbyScreen() {
  const m = useManager();
  const session = m.kind === "active" ? m.session : null;
  const lobby = useSession(session, (s) => s.lobby);
  const locs = useSession(session, (s) => s.locations) ?? {};
  const chat = useSession(session, (s) => s.chat) ?? [];
  const connected = useSession(session, (s) => s.connected);
  const prefs = usePrefs();
  const nav = useNavigate();
  const [showChat, setShowChat] = useState(false);
  const [seenChat, setSeenChat] = useState(0);
  const [qr, setQr] = useState(false);
  const [confirm, setConfirm] = useState<null | "leave" | "start">(null);
  const [menuFor, setMenuFor] = useState<string | null>(null);
  const [styles, setStyles] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [api, setApi] = useState<MapApi | null>(null);
  const [cam, setCam] = useState({ zoom: 0, maxZoom: 22 });
  const [copied, setCopied] = useState(false);

  const participants = useMemo(() => (lobby ? Object.values(lobby.members).filter((x) => x.online).map((x) => x.id) : []), [lobby]);
  const issues = useMemo(() => (lobby ? engine.validate(lobby, locs, participants) : []), [lobby, locs[lobby?.info.hostId ?? ""] != null, participants.join()]);
  const area = useMemo(() => (lobby ? engine.playArea(lobby, locs, participants) : null), [lobby?.config, lobby?.info.hostId, locs[lobby?.info.hostId ?? ""]?.lat, participants.join()]);
  const markers = useMemo(() => (lobby && session ? lobbyMarkers(lobby, locs, session.selfId) : []), [lobby, locs]);
  const [framed, setFramed] = useState<string>("");
  const frameKey = area ? `${area.lat.toFixed(4)},${area.lng.toFixed(4)},${area.radius}` : markers.length ? "pts" : "";
  if (api && frameKey && framed !== frameKey) {
    if (area) api.fitCircle(area, 30); else api.fit(markers.map((x) => [x.lng, x.lat]), 60, 16);
    setFramed(frameKey);
  }

  if (!session || !lobby) return <div className="page"><div className="container"><p>Connecting…</p></div></div>;
  const isHost = lobby.info.hostId === session.selfId;
  const mode = engine.mode(lobby.config.modeId);
  const form = engine.settingsForm(lobby.config);
  const v = form.values;
  const blocking = issues.find((i) => i.blocking);
  const online = Object.values(lobby.members).filter((x) => x.online);
  const ready = online.filter((x) => x.ready).length;
  const me = lobby.members[session.selfId];
  const preset = engine.catalog.presets.find((p) => p.id === lobby.config.presetId);
  const link = `${location.origin}/join/${lobby.info.code}`;
  const unread = Math.max(0, chat.length - seenChat);

  async function start() {
    setError(null);
    const e = await session!.startGame();
    if (e) setError(e);
  }

  const members = Object.values(lobby.members).sort((a, b) => a.joinedAt - b.joinedAt);
  const myFix = locs[session.selfId];

  return (
    <div className="page"><div className="container" style={{ paddingBottom: 150 }}>
      <div className="topbar">
        <div className="grow"><h1>Lobby</h1><div className="muted small">{mode.name}</div></div>
        {!connected && <span title="Offline">📵</span>}
        <button className="icon-btn" onClick={() => { setShowChat(true); setSeenChat(chat.length); }} aria-label="Chat">💬{unread > 0 && <span className="badge">{unread}</span>}</button>
        <button className="icon-btn" onClick={() => nav("/lobby/settings")} aria-label="Game settings">🎛️</button>
        <button className="icon-btn" onClick={() => setConfirm("leave")} aria-label="Leave lobby">🚪</button>
      </div>

      <Card>
        <div className="row"><span className="label-caps grow">Join code</span>{lobby.info.locked && <Pill color="var(--warning)">🔒 Locked</Pill>}</div>
        <div className="row" style={{ marginTop: 6 }}>
          <span className="join-code grow">{lobby.info.code.slice(0, 3)} {lobby.info.code.slice(3)}</span>
          <button className="icon-btn" aria-label="Copy" onClick={() => { navigator.clipboard?.writeText(lobby.info.code); setCopied(true); setTimeout(() => setCopied(false), 1500); }}>{copied ? "✅" : "📋"}</button>
          <button className="icon-btn" aria-label="QR code" onClick={() => setQr(!qr)}>🔳</button>
          <button className="icon-btn" aria-label="Share" onClick={() => {
            const text = `Join my Fugitive manhunt! Code: ${lobby.info.code}`;
            if (navigator.share) navigator.share({ title: "Fugitive", text, url: link }).catch(() => {}); else navigator.clipboard?.writeText(`${text}\n${link}`);
          }}>📤</button>
        </div>
        {qr && <div className="center" style={{ marginTop: 10 }}><QrCode text={link} /><div className="muted small">Scan to join in the browser</div></div>}
      </Card>

      <div className="card" style={{ padding: 0, height: 260, overflow: "hidden", position: "relative" }}>
        <GameMap styleId={prefs.mapStyle} mapTilerKey={prefs.mapTilerKey} customUrl={prefs.customStyleUrl} terrainShading={prefs.terrain} tilt3d={false}
          overlays={{ zone: area, markers }} onReady={setApi} className="fill" onCamera={(c) => setCam({ zoom: c.zoom, maxZoom: c.maxZoom })} />
        <MapButtons embedded map={api?.map ?? null} onLayers={() => setStyles(true)} zoom={cam.zoom} maxZoom={cam.maxZoom} />
      </div>

      <Card onClick={() => nav("/lobby/settings")}>
        <div className="row">
          <span className="role-icon" style={{ background: `${mode.accent}2e`, borderRadius: 12 }}>{modeIcon(mode.icon)}</span>
          <div className="grow"><h3>{preset ? `${preset.emoji} ${preset.name}` : mode.name}</h3><div className="muted small">{mode.tagline}</div></div>
          <span style={{ color: "var(--signal)", fontWeight: 600 }}>{isHost ? "Edit" : "Rules"}</span>
        </div>
        <div className="chips" style={{ marginTop: 10 }}>
          <Pill color="var(--text2)">⏱ {v.time_limit ? settingValue(Number(v.duration_min), "MINUTES") : "No limit"}</Pill>
          <Pill color="var(--text2)">🏁 Head start {Number(v.headstart_sec) === 0 ? "None" : settingValue(Number(v.headstart_sec), "SECONDS")}</Pill>
          {v.zone_enabled && <Pill color="var(--zone)">🌀 {distance(Number(v.zone_start_radius_m))} → {distance(Number(v.zone_end_radius_m))}</Pill>}
          <Pill color="var(--runner)">👁 Runners: {label(form, "runner_reveal")}</Pill>
          <Pill color="var(--hunter)">🎯 Hunters: {label(form, "hunter_reveal")}</Pill>
          <Pill color="var(--text2)">✋ {label(form, "catch_method")}</Pill>
          <Pill color="var(--text2)">📡 Every {String(v.location_interval_sec)}s</Pill>
        </div>
      </Card>

      {issues.map((i) => <Notice key={i.message} title={i.message} icon={i.blocking ? "⛔" : "⚠️"} color={i.blocking ? "var(--danger)" : "var(--warning)"} />)}
      {error && <Notice title={error} icon="⛔" color="var(--danger)" />}

      <SectionHeader title={`Players · ${members.length}`} />
      {isHost && mode.usesHunterSelection && v.hunter_selection === "manual" && <div className="muted small">Tap a player to make them a hunter.</div>}
      {members.map((x) => {
        const role = lobby.roster[x.id];
        const d = myFix && locs[x.id] && x.id !== session.selfId ? geoDistance(myFix, locs[x.id]) : null;
        return (
          <button key={x.id} className="list-row" onClick={() => isHost && setMenuFor(x.id)} style={{ cursor: isHost ? "pointer" : "default" }}>
            <Avatar emoji={x.avatar} color={x.color} ring={role ? roleColor(role) : undefined} dim={!x.online} />
            <div className="grow">
              <div className="row" style={{ gap: 6 }}>
                <strong className="ellipsis">{x.name}</strong>
                {x.id === session.selfId && <Pill>You</Pill>}
                {x.id === lobby.info.hostId && <span title="Host">⭐</span>}
              </div>
              <div className="muted small">
                {!x.online ? "Offline" : x.ready ? "✅ Ready" : "Not ready"}
                {x.wantsToHunt && <span style={{ color: "var(--hunter)" }}> · wants to hunt</span>}
                {d != null ? ` · ${distance(d)} away` : !locs[x.id] ? <span style={{ color: "var(--warning)" }}> · no GPS yet</span> : ""}
                {x.appVersion && x.appVersion < 0 ? "" : ""}
              </div>
            </div>
            {role ? <Pill color={roleColor(role)}>{roleIcon(role)} {mode.roleLabels[role] ?? role}</Pill> : mode.usesHunterSelection ? <Pill color="var(--text2)">Auto</Pill> : null}
          </button>
        );
      })}
    </div>

    <div style={{ position: "fixed", left: 0, right: 0, bottom: 0, background: "var(--n0)", borderTop: "1px solid var(--outline)", padding: "14px 16px calc(14px + var(--safe-bottom))" }}>
      <div style={{ maxWidth: 640, margin: "0 auto" }} className="col">
        {v.hunter_selection === "volunteers" && mode.usesHunterSelection && me && (
          <button className={`chip ${me.wantsToHunt ? "on" : ""}`} style={{ alignSelf: "flex-start" }} onClick={() => session.setWantsToHunt(!me.wantsToHunt)}>🎯 I want to hunt</button>
        )}
        <div className="row">
          {me && <Button kind="secondary" color={me.ready ? "var(--success)" : undefined} style={{ flex: 1 }} onClick={() => session.setReady(!me.ready)}>{me.ready ? "Ready ✓" : "Ready up"}</Button>}
          {isHost ? <Button style={{ flex: 1.4 }} disabled={!!blocking} onClick={() => (ready >= online.length ? start() : setConfirm("start"))}>▶ Start</Button>
            : <span className="muted grow">Waiting for the host…</span>}
        </div>
        <span className="muted small">{ready} of {online.length} ready{blocking ? ` · ${blocking.message}` : ""}</span>
      </div>
    </div>

    {menuFor && (() => {
      const x = lobby.members[menuFor]; if (!x) return null;
      const close = () => setMenuFor(null);
      return (
        <Sheet title={x.name} onClose={close}>
          <div className="col">
            {mode.usesHunterSelection && <Button kind="secondary" color="var(--hunter)" onClick={() => { session.setRole(x.id, "hunter"); close(); }}>🎯 Make {mode.roleLabels.hunter.toLowerCase()}</Button>}
            {mode.usesHunterSelection && <Button kind="secondary" color="var(--runner)" onClick={() => { session.setRole(x.id, "runner"); close(); }}>🏃 Make {mode.roleLabels.runner.toLowerCase()}</Button>}
            <Button kind="secondary" onClick={() => { session.setRole(x.id, "spectator"); close(); }}>👁️ Spectator</Button>
            {lobby.roster[x.id] && <Button kind="secondary" onClick={() => { session.setRole(x.id, null); close(); }}>Automatic</Button>}
            {x.id !== session.selfId && <>
              <Button kind="secondary" onClick={() => { session.transferHost(x.id); close(); }}>⭐ Make host</Button>
              <Button kind="secondary" color="var(--danger)" onClick={() => { session.kick(x.id); close(); }}>Kick</Button>
            </>}
            {x.id === session.selfId && <Button kind="secondary" onClick={() => { session.setLocked(!lobby.info.locked); close(); }}>{lobby.info.locked ? "🔓 Unlock lobby" : "🔒 Lock lobby"}</Button>}
          </div>
        </Sheet>
      );
    })()}
    {showChat && <Sheet title="Lobby chat" onClose={() => { setShowChat(false); setSeenChat(chat.length); }}>
      <ChatPanel messages={chat} members={lobby.members} selfId={session.selfId} onSend={(t) => session.sendChat(t, false)} />
    </Sheet>}
    {styles && <MapStyleSheet onClose={() => setStyles(false)} />}
    {confirm === "leave" && <Dialog title="Leave the lobby?" onClose={() => setConfirm(null)}
      actions={<><Button kind="ghost" onClick={() => setConfirm(null)}>Stay</Button><Button kind="ghost" color="var(--danger)" onClick={() => { setConfirm(null); sessions.leave(); }}>Leave</Button></>}>
      <p className="muted">{isHost ? "Another player will become the host." : "You can join again with the code."}</p></Dialog>}
    {confirm === "start" && <Dialog title="Not everyone is ready" onClose={() => setConfirm(null)}
      actions={<><Button kind="ghost" onClick={() => setConfirm(null)}>Wait</Button><Button kind="ghost" onClick={() => { setConfirm(null); start(); }}>Start</Button></>}>
      <p className="muted">Start anyway? Players who aren't ready will still take part.</p></Dialog>}
    </div>
  );
}

function label(form: ReturnType<typeof engine.settingsForm>, key: string) {
  for (const c of form.categories) for (const s of c.settings) if (s.key === key) return s.display;
  return "";
}
