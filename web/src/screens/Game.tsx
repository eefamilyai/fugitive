import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Avatar, Button, Dialog, Pill, Sheet, abilityIcon, eventIcon, roleColor, roleIcon } from "../ui/components";
import { sessions, useManager, useSession } from "../session/manager";
import { setPrefs, usePrefs } from "../prefs";
import { ageBadge, ago, clock, compass, distance, playerColor, speed } from "../util/format";
import { GameMap, objectivePins, type MapApi, type MarkerSpec, type PinSpec } from "../map/GameMap";
import { MapButtons, MapStyleSheet, useScaleBar } from "../map/MapControls";
import { manualSource } from "../session/location";
import { ChatPanel } from "./Chat";
import type { GameEvent, Projection, WebPlayer } from "../types";

type Mode = "follow" | "free";

export function GameScreen() {
  const m = useManager();
  const session = m.kind === "active" ? m.session : null;
  const p = useSession(session, (s) => s.projection) ?? null;
  const lobby = useSession(session, (s) => s.lobby);
  const events = useSession(session, (s) => s.events) ?? [];
  const chat = useSession(session, (s) => s.chat) ?? [];
  const markers = useSession(session, (s) => s.markers) ?? [];
  const banners = useSession(session, (s) => s.banners) ?? [];
  const connected = useSession(session, (s) => s.connected);
  const problem = useSession(session, (s) => s.locationProblem);
  const myFix = useSession(session, (s) => s.myFix);
  const prefs = usePrefs();
  const nav = useNavigate();
  const [api, setApi] = useState<MapApi | null>(null);
  const [camMode, setCamMode] = useState<Mode>("follow");
  const [cam, setCam] = useState({ zoom: 0, bearing: 0, maxZoom: 22 });
  const [selected, setSelected] = useState<string | null>(null);
  const [sheet, setSheet] = useState<null | "players" | "feed" | "chat">(null);
  const [dialog, setDialog] = useState<null | "code" | "report" | "leave" | "end" | "ping">(null);
  const [ping, setPing] = useState<{ lat: number; lng: number } | null>(null);
  const [styles, setStyles] = useState(false);
  const [menu, setMenu] = useState(false);
  const [seenEvents, setSeenEvents] = useState(0);
  const [seenChat, setSeenChat] = useState(0);
  const [status, setStatus] = useState<{ title: string; body: string; emoji: string; color: string } | null>(null);
  const lastStatus = useRef<string | null>(null);
  const framed = useRef(false);
  useScaleBar(api?.map ?? null);

  // Camera follows me.
  useEffect(() => {
    if (!api || !myFix) return;
    if (!framed.current) { api.map?.jumpTo({ center: [myFix.lng, myFix.lat], zoom: 16.5 }); framed.current = true; return; }
    if (camMode === "follow") api.map?.easeTo({ center: [myFix.lng, myFix.lat], duration: 900 });
  }, [api, myFix?.lat, myFix?.lng, camMode]);

  // Banners auto-dismiss.
  useEffect(() => {
    if (!banners.length) return;
    const t = setTimeout(() => session?.dismissBanner(banners[banners.length - 1].id), 4500);
    return () => clearTimeout(t);
  }, [banners]);

  // One-time overlay when my status changes.
  useEffect(() => {
    const me = p?.me; if (!me) return;
    const key = `${me.status}/${me.role}`;
    const prev = lastStatus.current; lastStatus.current = key;
    if (!prev || prev === key) return;
    const by = me.caughtBy ? p!.players.find((x) => x.id === me.caughtBy)?.name : null;
    if (me.status === "CAUGHT") setStatus({ title: "Caught!", body: `${by ?? "A hunter"} got you. You can keep watching the game.`, emoji: "🫵", color: "var(--hunter)" });
    else if (me.status === "ELIMINATED") setStatus({ title: "Eliminated", body: me.outReason === "zone" ? "You were outside the zone." : "You lost connection for too long.", emoji: "☠️", color: "var(--danger)" });
    else if (me.status === "ESCAPED") setStatus({ title: "You escaped!", body: "The chopper's got you. Watch the others try.", emoji: "🚁", color: "var(--success)" });
    else if (prev.endsWith("/runner") && me.role === "hunter") setStatus({ title: "Infected!", body: "You're one of them now. Hunt down the survivors.", emoji: "🧟", color: "#84CC16" });
  }, [p?.me?.status, p?.me?.role]);

  const overlays = useMemo(() => p ? buildOverlays(p, markers, selected) : {}, [p, markers, selected]);

  if (!session || !lobby) return null;
  if (!p) return <div className="page"><div className="container"><p>Loading game…</p></div></div>;
  const isHost = lobby.info.hostId === session.selfId;
  const manual = prefs.locationSource === "manual";

  return (
    <div className="game">
      <GameMap styleId={prefs.mapStyle} mapTilerKey={prefs.mapTilerKey} customUrl={prefs.customStyleUrl} terrainShading={prefs.terrain} tilt3d={prefs.map3d}
        overlays={overlays} onReady={setApi} padding={{ top: 150, bottom: 240, left: 0, right: 0 }}
        onCamera={(c) => setCam({ zoom: c.zoom, bearing: c.bearing, maxZoom: c.maxZoom })}
        onUserMove={() => setCamMode("free")}
        onMarkerClick={(id) => setSelected(selected === id ? null : id)}
        onClick={(lat, lng) => { if (manual) manualSource.set(lat, lng); else setSelected(null); }}
        onLongPress={(lat, lng) => { if (!p.isSpectating) { setPing({ lat, lng }); setDialog("ping"); } }} />

      <div className="hud-top">
        <TopHud p={p} connected={!!connected} />
        <Warning p={p} problem={problem ?? null} bearing={cam.bearing} manual={manual} />
        {p.stage === "HEADSTART" && <Headstart p={p} />}
        {banners.map((e) => <Banner key={e.id} e={e} />)}
      </div>

      <MapButtons map={api?.map ?? null} onLayers={() => setStyles(true)} zoom={cam.zoom} maxZoom={cam.maxZoom}>
        <div style={{ position: "relative" }}>
          <button className="map-btn" onClick={() => setMenu(!menu)} aria-label="Menu">⋮</button>
          {menu && (
            <div className="glass col" style={{ position: "absolute", right: 56, top: 0, width: 220, gap: 4, padding: 8 }} onClick={() => setMenu(false)}>
              {isHost && <Button kind="ghost" small onClick={() => setDialog("end")}>🏁 End game for everyone</Button>}
              <Button kind="ghost" small onClick={() => nav("/settings")}>⚙️ App settings</Button>
              <Button kind="ghost" small onClick={() => setDialog("leave")}>🚪 {p.isSpectating ? "Leave game" : "Give up & leave"}</Button>
            </div>
          )}
        </div>
        <button className={`map-btn ${camMode === "follow" ? "on" : ""}`} aria-label="Follow me" onClick={() => {
          setCamMode("follow"); if (myFix) api?.flyTo(myFix.lat, myFix.lng, Math.max(cam.zoom, 16));
        }}>◎</button>
        <button className="map-btn" aria-label="Show everything" onClick={() => {
          setCamMode("free");
          const pts: [number, number][] = p.players.filter((x) => x.fix).map((x) => [x.fix!.lng, x.fix!.lat]);
          if (p.zone) { const z = p.zone.current; pts.push(...([0, 90, 180, 270].map((b) => { const d = z.radius / 111320; return b === 0 ? [z.lng, z.lat + d] : b === 180 ? [z.lng, z.lat - d] : b === 90 ? [z.lng + d / Math.cos(z.lat * Math.PI / 180), z.lat] : [z.lng - d / Math.cos(z.lat * Math.PI / 180), z.lat]; }) as [number, number][])); }
          p.objectives.forEach((o) => pts.push([o.lng, o.lat]));
          api?.fit(pts, 80);
        }}>⛶</button>
        {Math.abs(cam.bearing) > 0.5 && <button className="map-btn" aria-label="Reset north" onClick={() => api?.resetNorth()}><span style={{ transform: `rotate(${-cam.bearing}deg)`, display: "inline-block" }}>🧭</span></button>}
      </MapButtons>

      <div className="hud-bottom">
        {manual && <div className="glass small center">📍 Manual location: tap the map to move yourself</div>}
        {selected && (() => { const sp = p.players.find((x) => x.id === selected); return sp ? <Selected p={p} sp={sp} onClose={() => setSelected(null)}
          onFind={() => { if (sp.fix) { setCamMode("free"); api?.flyTo(sp.fix.lat, sp.fix.lng); } }} onCatch={() => session.claimCatch(sp.id)} /> : null; })()}
        <Dock p={p} unreadEvents={Math.max(0, events.length - seenEvents)} unreadChat={Math.max(0, chat.length - seenChat)}
          onCatch={(id) => session.claimCatch(id)} onCode={() => setDialog("code")} onReport={() => setDialog("report")}
          onAbility={(id) => session.useAbility(id)}
          onPlayers={() => setSheet("players")} onFeed={() => { setSheet("feed"); setSeenEvents(events.length); }} onChat={() => { setSheet("chat"); setSeenChat(chat.length); }} />
      </div>

      {p.stage === "COUNTDOWN" && <Countdown p={p} />}
      {status && <div className="overlay"><div style={{ fontSize: 72 }}>{status.emoji}</div>
        <div className="big-role" style={{ color: status.color, fontSize: 36 }}>{status.title}</div><p style={{ fontSize: 17 }}>{status.body}</p>
        <Button color={status.color} onClick={() => setStatus(null)}>Continue</Button></div>}

      {p.incomingCatch && <IncomingCatch p={p} onRespond={(a) => session.respondToCatch(p.incomingCatch!.id, a)} />}
      {dialog === "code" && <CodeDialog lockedMs={p.codeLockedMs} onClose={() => setDialog(null)} onSubmit={(c) => { session.submitCode(c); setDialog(null); }} />}
      {dialog === "report" && <Dialog title="Who caught you?" onClose={() => setDialog(null)} actions={<Button kind="ghost" onClick={() => setDialog(null)}>Cancel — I'm still free</Button>}>
        <div className="col">{p.players.filter((x) => !x.isMe && x.status === "ACTIVE" && x.role !== p.me?.role).map((h) => (
          <button key={h.id} className="list-row" onClick={() => { session.reportCaught(h.id); setDialog(null); }}><Avatar emoji={h.avatar} color={h.color} size={36} /><strong>{h.name}</strong></button>))}
          <Button kind="ghost" onClick={() => { session.reportCaught(); setDialog(null); }}>Not sure / someone else</Button></div>
      </Dialog>}
      {dialog === "leave" && <Dialog title={p.isSpectating ? "Leave the game?" : "Give up?"} onClose={() => setDialog(null)}
        actions={<><Button kind="ghost" onClick={() => setDialog(null)}>Keep playing</Button><Button kind="ghost" color="var(--danger)" onClick={() => { setDialog(null); sessions.leave(); }}>Leave</Button></>}>
        <p className="muted">{p.isSpectating ? "You'll return home. You can rejoin with the code." : "You'll be out of this game and leave the lobby."}</p></Dialog>}
      {dialog === "end" && <Dialog title="End the game for everyone?" onClose={() => setDialog(null)}
        actions={<><Button kind="ghost" onClick={() => setDialog(null)}>Cancel</Button><Button kind="ghost" color="var(--danger)" onClick={() => { setDialog(null); session.endGame(); }}>End game</Button></>}>
        <p className="muted">The game stops now and everyone sees the results.</p></Dialog>}
      {dialog === "ping" && ping && <Dialog title="Ping your team" onClose={() => setDialog(null)} actions={<Button kind="ghost" onClick={() => setDialog(null)}>Cancel</Button>}>
        <div className="col">{[["spotted", "👀  Spotted someone here"], ["going", "🏃  I'm heading here"], ["danger", "⚠️  Danger — avoid"]].map(([k, l]) => (
          <Button key={k} kind="secondary" onClick={() => { session.dropMarker(k, ping.lat, ping.lng); setDialog(null); }}>{l}</Button>))}</div>
      </Dialog>}

      {sheet === "players" && <Sheet title="Players" onClose={() => setSheet(null)}>
        <div className="col">{p.players.map((x) => <PlayerRow key={x.id} p={p} x={x} onClick={() => { setSelected(x.id); setSheet(null); if (x.fix) { setCamMode("free"); api?.flyTo(x.fix.lat, x.fix.lng); } }} />)}</div>
      </Sheet>}
      {sheet === "feed" && <Sheet title="Game feed" onClose={() => setSheet(null)}>
        {events.length === 0 && <p className="muted">Nothing has happened yet.</p>}
        {[...events].reverse().map((e) => <div key={e.id} className="feed-item"><span>{eventIcon(e.type)}</span>
          <div><div style={{ fontWeight: e.importance >= 2 ? 600 : 400 }}>{e.text}</div><div className="tiny muted">{new Date(e.at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</div></div></div>)}
      </Sheet>}
      {sheet === "chat" && <Sheet title="Chat" onClose={() => setSheet(null)}>
        <ChatPanel messages={chat} members={lobby.members} selfId={session.selfId} allowTeam={!p.isSpectating} onSend={(t, team) => session.sendChat(t, team)} />
      </Sheet>}
      {styles && <MapStyleSheet onClose={() => setStyles(false)} />}
    </div>
  );
}

function buildOverlays(p: Projection, mk: { id: string; by: string; kind: string; lat: number; lng: number; createdAt: number; expiresAt: number }[], selected: string | null) {
  const interval = Number(p.settings["location_interval_sec"] ?? 2) * 1000;
  const markers: MarkerSpec[] = p.players.filter((x) => x.fix).map((x) => {
    const look = x.status !== "ACTIVE" ? "out" : x.visibility === "SNAPSHOT" ? "snapshot" : x.isStale ? "stale" : "live";
    return {
      id: x.id, lat: x.fix!.lat, lng: x.fix!.lng, avatar: x.avatar, ring: roleColor(x.role), fill: playerColor(x.color),
      label: x.isMe ? "You" : x.name, bearing: x.fix!.bearing, accuracy: x.fix!.accuracy, look, isMe: x.isMe, selected: x.id === selected,
      badge: look === "snapshot" || look === "stale" ? ageBadge(p.now - x.fix!.time) : undefined, glideMs: interval,
    } as MarkerSpec;
  });
  const names = Object.fromEntries(p.players.map((x) => [x.id, x.name]));
  const pins: PinSpec[] = [
    ...objectivePins(p.objectives),
    ...mk.filter((m) => m.expiresAt > p.now).map((m) => ({ id: m.id, lat: m.lat, lng: m.lng, emoji: m.kind === "spotted" ? "👀" : m.kind === "going" ? "🏃" : "⚠️",
      color: "#22D3EE", label: `${names[m.by] ?? "Teammate"} · ${ageBadge(p.now - m.createdAt)}` })),
  ];
  const me = p.me?.fix;
  const method = String(p.settings["catch_method"]);
  const rangeMatters = method === "proximity" || method === "auto" || p.settings["catch_require_range"] === true;
  const alert = Number(p.settings["proximity_alert_m"] ?? 0);
  return {
    zone: p.zone?.current ?? null, nextZone: p.zone?.next ?? null,
    zoneDanger: !!(p.zoneStatus?.applies && !p.zoneStatus.inside),
    markers, pins,
    trails: p.players.filter((x) => x.trail.length > 1).map((x) => ({ id: x.id, coords: x.trail, color: playerColor(x.color) })),
    ring: me && p.catchOptions.length && rangeMatters ? { circle: { lat: me.lat, lng: me.lng, radius: Number(p.settings["catch_radius_m"] ?? 15) }, color: roleColor(p.me?.role) } : null,
    alertRing: me && p.threats.length && alert > 0 ? { circle: { lat: me.lat, lng: me.lng, radius: alert }, color: "#FF3B5C" } : null,
  };
}

function TopHud({ p, connected }: { p: Projection; connected: boolean }) {
  const role = p.me?.role ?? "spectator";
  const color = p.isSpectating ? "#94A3B8" : roleColor(role);
  const [label, ms] = p.stage === "COUNTDOWN" ? ["Starts in", p.stageEndsInMs ?? 0] : p.stage === "HEADSTART" ? ["Head start", p.stageEndsInMs ?? 0]
    : p.timeLeftMs != null ? ["Time left", p.timeLeftMs] : ["Elapsed", p.now - p.headstartEndsAt];
  const urgent = p.stage === "ACTIVE" && p.timeLeftMs != null && p.timeLeftMs < 60000;
  const z = p.zone;
  const zoneText = !z ? null : z.phase === "WAITING" ? `Zone ${z.phaseEndsAt ? clock(z.phaseEndsAt - p.now) : ""}` : z.phase === "ANNOUNCED" ? `Moves in ${clock((z.phaseEndsAt ?? p.now) - p.now)}` : z.phase === "SHRINKING" ? "Shrinking" : "Final zone";
  return (
    <div className="glass">
      <div className="row">
        <span className="role-icon" style={{ background: `${color}33` }}>{p.isSpectating ? "👁️" : roleIcon(role)}</span>
        <div className="grow">
          <div className="label-caps" style={{ color }}>{p.me && p.me.status !== "ACTIVE" ? `${p.myRoleLabel} · out` : p.myRoleLabel}</div>
          <div className="ellipsis">{p.objective}</div>
        </div>
        <div style={{ textAlign: "right" }}><div className="label-caps">{label}</div><div className="clock" style={{ color: urgent ? "var(--danger)" : undefined }}>{clock(ms)}</div></div>
      </div>
      <div className="row" style={{ marginTop: 8, gap: 6 }}>
        {Object.entries(p.activeCounts).filter(([r]) => r !== "spectator").sort().map(([r, n]) => <Pill key={r} color={roleColor(r)}>{roleIcon(r)} {n}</Pill>)}
        {zoneText && <Pill color="var(--zone)" filled={z?.phase === "SHRINKING"}>🌀 {zoneText}</Pill>}
        {p.nextPingAt && p.stage === "ACTIVE" && <Pill color="var(--runner)">Ping {clock(p.nextPingAt - p.now)}</Pill>}
        <span className="grow" />
        {connected ? <span className="tiny" style={{ color: "var(--success)", fontWeight: 700 }}>● LIVE</span> : <Pill color="var(--danger)" filled>Offline</Pill>}
      </div>
    </div>
  );
}

function Warning({ p, problem, bearing, manual }: { p: Projection; problem: string | null; bearing: number; manual: boolean }) {
  const zs = p.zoneStatus, me = p.me;
  let w: { color: string; title: string; body: string; pulse?: boolean; arrow?: number; icon: string; action?: () => void } | null = null;
  if (problem && !manual) w = { color: "var(--danger)", icon: "📍", title: problem === "insecure" ? "Location needs HTTPS" : problem === "permission" ? "Location permission needed" : "Can't get your location",
    body: problem === "insecure" ? "Open this site over https:// to share your position." : "Others can't see you and the zone can't check you.",
    action: problem === "permission" ? () => setPrefs({ locationSource: "manual" }) : undefined };
  else if (zs?.applies && !zs.inside) w = { color: "var(--danger)", icon: "⚠️", pulse: true, title: "OUTSIDE THE ZONE",
    body: `${distance(-zs.marginMeters)} to safety${zs.graceLeftMs && p.settings["zone_penalty"] === "eliminate" ? ` · out in ${clock(zs.graceLeftMs)}` : ""}` };
  else if (p.threats.length) {
    const t = p.threats[0]; const dir = p.settings["proximity_direction"] === true;
    w = { color: "var(--hunter)", icon: "⚠️", pulse: true, title: "Hunter nearby",
      body: dir ? `${distance(t.distance)} · ${compass(t.bearing)}` : `Within ${distance(Number(p.settings["proximity_alert_m"]))}`, arrow: dir ? t.bearing - bearing : undefined };
  }
  if (!w) return null;
  return (
    <div className={`warning ${w.pulse ? "pulse" : ""}`} style={{ background: w.color }}>
      <span style={{ fontSize: 22, display: "inline-block", transform: w.arrow != null ? `rotate(${w.arrow}deg)` : undefined }}>{w.arrow != null ? "⬆️" : w.icon}</span>
      <div className="grow"><strong>{w.title}</strong><div className="small">{w.body}</div></div>
      {w.action && <Button kind="ghost" small color="#fff" onClick={w.action}>Use manual</Button>}
    </div>
  );
}

function Headstart({ p }: { p: Projection }) {
  const hunter = p.me?.role === "hunter" && p.me.status === "ACTIVE";
  return <div className="warning center" style={{ background: hunter ? "var(--hunter)" : "var(--runner)", flexDirection: "column", gap: 0, color: hunter ? "#fff" : "var(--n1)" }}>
    <strong className="grotesk" style={{ letterSpacing: 1.5 }}>{hunter ? "HOLD YOUR POSITION" : "RUN! HEAD START"}</strong>
    <span className="clock" style={{ fontSize: 22 }}>Hunters released in {clock(p.stageEndsInMs ?? 0)}</span></div>;
}

function Banner({ e }: { e: GameEvent }) {
  return <div className="banner"><span>{eventIcon(e.type)}</span><span style={{ fontWeight: e.importance >= 2 ? 600 : 400 }}>{e.text}</span></div>;
}

function Countdown({ p }: { p: Projection }) {
  const role = p.me?.role ?? "spectator";
  const secs = Math.ceil((p.stageEndsInMs ?? 0) / 1000);
  const hs = p.headstartEndsAt - p.startAt;
  return (
    <div className="overlay">
      <div className="label-caps" style={{ letterSpacing: 4, fontSize: 15 }}>You are</div>
      <div className="big-role" style={{ color: roleColor(role) }}>{p.myRoleLabel.toUpperCase()}</div>
      <div style={{ fontSize: 48 }}>{roleIcon(role)}</div>
      <div style={{ fontSize: 17 }}>{p.objective}</div>
      <div className="countdown" key={secs}>{secs}</div>
      <div className="muted">{role === "hunter" && hs > 0 ? `Runners get ${clock(hs)} to hide. Stay put!` : role === "runner" && hs > 0 ? `You'll have ${clock(hs)} head start. Get ready to run!` : "Get ready…"}</div>
    </div>
  );
}

function Dock(p: { p: Projection; unreadEvents: number; unreadChat: number; onCatch: (id: string) => void; onCode: () => void; onReport: () => void;
  onAbility: (id: string) => void; onPlayers: () => void; onFeed: () => void; onChat: () => void }) {
  const g = p.p;
  const method = String(g.settings["catch_method"]);
  const me = g.me;
  const name = (id: string) => g.players.find((x) => x.id === id)?.name ?? "Runner";
  let main: React.ReactNode = null;
  if (g.isSpectating) {
    main = <div className="center grotesk" style={{ padding: 8, fontSize: 17 }}>{me?.status === "ESCAPED" ? "🚁 You escaped — spectating" : me?.status === "CAUGHT" ? "You were caught — spectating" : me?.status === "ELIMINATED" ? "You're out — spectating" : "Spectating"}</div>;
  } else if (g.catchOptions.length) {
    const pending = g.outgoingCatches[0];
    const best = g.catchOptions.filter((o) => o.allowed).sort((a, b) => (a.distance ?? 1e9) - (b.distance ?? 1e9))[0];
    const nearest = [...g.catchOptions].sort((a, b) => (a.distance ?? 1e9) - (b.distance ?? 1e9))[0];
    let label = "CATCH", sub: string | undefined, enabled = false, onClick = () => {};
    if (pending) { label = "WAITING…"; sub = `${name(pending.runnerId)} must confirm · ${clock(pending.expiresAt - g.now)}`; }
    else if (method === "code") { label = "ENTER CODE"; enabled = g.stage === "ACTIVE"; sub = enabled ? "Tagged someone? Type their code" : "Hunters aren't released yet"; onClick = p.onCode; }
    else if (method === "auto") { label = "AUTO-CATCH ON"; sub = nearest?.distance != null ? `Nearest ${distance(nearest.distance)} · get within ${distance(Number(g.settings["catch_radius_m"]))}` : undefined; }
    else if (best) { label = `CATCH ${name(best.targetId).toUpperCase()}`; sub = best.distance != null ? `${distance(best.distance)} away` : undefined; enabled = true; onClick = () => p.onCatch(best.targetId); }
    else sub = nearest?.reason;
    main = <button className="catch-btn" style={{ background: roleColor(me?.role) }} disabled={!enabled} onClick={onClick}>
      <span style={{ fontSize: 26 }}>{method === "code" ? "🔑" : "✋"}</span><div className="grow"><div className="t ellipsis">{label}</div>{sub && <div className="small ellipsis">{sub}</div>}</div></button>;
  }
  return (
    <div className="dock">
      {g.abilities.length > 0 && !g.isSpectating && <div className="row">{g.abilities.map((a) => {
        const active = a.activeUntil != null, recharging = !active && g.now < a.readyAt;
        const color = active ? "var(--success)" : !a.blockedReason ? "var(--signal)" : "var(--text2)";
        return <button key={a.id} className="ability" disabled={!!a.blockedReason} onClick={() => p.onAbility(a.id)}
          style={{ borderColor: color, color, background: `color-mix(in srgb, ${color} ${a.blockedReason && !active ? 6 : 16}%, transparent)` }}>
          <span style={{ fontSize: 18 }}>{abilityIcon(a.icon)}</span>
          <div><div style={{ fontWeight: 700, fontSize: 14 }}>{a.name}</div><div className="tiny muted">{active ? `Active ${clock(a.activeUntil! - g.now)}` : recharging ? clock(a.readyAt - g.now) : a.usesLeft <= 0 ? "Used" : `${a.usesLeft} left`}</div></div>
        </button>;
      })}</div>}
      {main}
      {method === "code" && !g.catchOptions.length && !g.isSpectating && <MyCode session={sessions.current} />}
      <div className="row">
        <button className="icon-btn" onClick={p.onPlayers} aria-label="Players">☰</button>
        <button className="icon-btn" onClick={p.onFeed} aria-label="Game feed">🔔{p.unreadEvents > 0 && <span className="badge">{Math.min(99, p.unreadEvents)}</span>}</button>
        <button className="icon-btn" onClick={p.onChat} aria-label="Chat">💬{p.unreadChat > 0 && <span className="badge">{Math.min(99, p.unreadChat)}</span>}</button>
        <span className="grow" />
        {g.isPrey && g.settings["allow_self_report"] === true && g.stage !== "COUNTDOWN" && <Button kind="ghost" small color="var(--text2)" onClick={p.onReport}>I was caught</Button>}
      </div>
    </div>
  );
}

/** Prey in "secret code" games: show my 4-digit code on demand. */
function MyCode({ session }: { session: ReturnType<() => typeof sessions.current> }) {
  const [show, setShow] = useState(false);
  const code = useSession(session, (s) => (s.lobby?.game?.players?.[s.selfId] as any)?.catchCode as string | undefined);
  if (!code) return null;
  return <div className="row"><span>🔢</span><span className="grow">Your code</span>
    <button className="btn ghost" onClick={() => setShow(!show)} style={{ fontSize: 22, letterSpacing: 4 }}>{show ? code : "••••"}</button></div>;
}

function Selected({ p, sp, onClose, onFind, onCatch }: { p: Projection; sp: WebPlayer; onClose: () => void; onFind: () => void; onCatch: () => void }) {
  const opt = p.catchOptions.find((o) => o.targetId === sp.id);
  const method = String(p.settings["catch_method"]);
  const parts = [sp.distance != null ? `${distance(sp.distance)} ${sp.bearing != null ? compass(sp.bearing) : ""}` : null,
    sp.ageMs != null ? (sp.ageMs < 5000 ? "live" : ago(sp.ageMs)) : null,
    sp.visibilityReason && sp.visibilityReason !== "Teammate" ? sp.visibilityReason : null,
    sp.fix?.speed && sp.fix.speed > 0.8 ? speed(sp.fix.speed) : null].filter(Boolean);
  return (
    <div className="glass col" style={{ gap: 6 }}>
      <div className="row">
        <Avatar emoji={sp.avatar} color={sp.color} ring={roleColor(sp.role)} />
        <div className="grow"><div className="row" style={{ gap: 8 }}><strong>{sp.name}</strong><Pill color={roleColor(sp.role)}>{sp.roleLabel}</Pill></div>
          <div className="muted small">{parts.join(" · ")}</div></div>
        <Button kind="ghost" small onClick={onFind}>Find</Button>
        <button className="icon-btn" onClick={onClose} aria-label="Close">✕</button>
      </div>
      {opt?.allowed && (method === "confirm" || method === "proximity") && <Button kind="ghost" color="var(--hunter)" onClick={onCatch}>Catch {sp.name}</Button>}
    </div>
  );
}

function PlayerRow({ p, x, onClick }: { p: Projection; x: WebPlayer; onClick: () => void }) {
  const status = x.status === "ACTIVE"
    ? x.visibility === "LIVE" ? (x.isStale ? `Signal lost · ${ago(x.ageMs ?? 0)}` : x.visibilityReason ?? "Live")
      : x.visibility === "SNAPSHOT" ? `Pinged ${ago(x.ageMs ?? 0)}` : x.visibilityReason ? `Hidden · ${x.visibilityReason}` : "Hidden"
    : x.status === "CAUGHT" ? `Caught${x.caughtBy ? ` by ${p.players.find((y) => y.id === x.caughtBy)?.name ?? "?"}` : ""}`
    : x.status === "ELIMINATED" ? (x.outReason === "zone" ? "Eliminated by the zone" : "Eliminated") : x.status === "ESCAPED" ? "Escaped" : "Left the game";
  return (
    <button className="list-row" style={{ background: "var(--n3)" }} onClick={onClick}>
      <Avatar emoji={x.avatar} color={x.color} ring={roleColor(x.role)} dim={x.status !== "ACTIVE"} />
      <div className="grow"><strong>{x.isMe ? `${x.name} (you)` : x.name}</strong><div className="muted small">{status}</div></div>
      <div className="col" style={{ alignItems: "flex-end", gap: 2 }}>
        <Pill color={roleColor(x.role)}>{x.roleLabel}</Pill>
        {x.distance != null && <span className="small">{distance(x.distance)} {x.bearing != null ? compass(x.bearing) : ""}</span>}
        {x.fix?.battery != null && x.fix.battery <= 20 && <span className="tiny" style={{ color: "var(--warning)" }}>🔋 {x.fix.battery}%</span>}
        {x.catches > 0 && <span className="tiny">✋ {x.catches}</span>}
      </div>
    </button>
  );
}

function IncomingCatch({ p, onRespond }: { p: Projection; onRespond: (accept: boolean) => void }) {
  const c = p.incomingCatch!;
  const hunter = p.players.find((x) => x.id === c.hunterId);
  const total = Math.max(1, c.expiresAt - c.createdAt), left = Math.max(0, c.expiresAt - p.now);
  return (
    <Dialog icon="🚨" title={`${hunter?.name ?? "A hunter"} says they caught you!`}
      actions={<><Button kind="ghost" onClick={() => onRespond(false)}>No, they missed</Button><Button kind="ghost" color="var(--hunter)" onClick={() => onRespond(true)}>Yes, I'm caught</Button></>}>
      <p className="center muted" style={{ margin: 0 }}>Did they tag you? Be honest — it's more fun that way.</p>
      {c.distanceMeters != null && <p className="center small muted" style={{ margin: 0 }}>GPS says you were {distance(c.distanceMeters)} apart.</p>}
      <div style={{ height: 6, background: "var(--n4)", borderRadius: 3 }}><div style={{ height: 6, width: `${(left / total) * 100}%`, background: "var(--signal)", borderRadius: 3 }} /></div>
      <div className="tiny center">Auto-answer in {clock(left)}</div>
    </Dialog>
  );
}

function CodeDialog({ onClose, onSubmit, lockedMs }: { onClose: () => void; onSubmit: (c: string) => void; lockedMs?: number }) {
  const [code, setCode] = useState("");
  return (
    <Dialog title="Enter the runner's code" onClose={onClose}
      actions={<><Button kind="ghost" onClick={onClose}>Cancel</Button><Button kind="ghost" disabled={code.length !== 4 || !!lockedMs} onClick={() => onSubmit(code)}>Catch</Button></>}>
      <p className="muted" style={{ margin: 0 }}>Ask the runner you tagged to show you the 4-digit code on their phone.</p>
      {lockedMs ? <p style={{ color: "var(--danger)" }}>Too many wrong codes — wait {clock(lockedMs)}</p> : null}
      <input className="text grotesk center" inputMode="numeric" autoFocus value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 4))}
        style={{ fontSize: 30, letterSpacing: 10 }} onKeyDown={(e) => e.key === "Enter" && code.length === 4 && onSubmit(code)} />
    </Dialog>
  );
}

