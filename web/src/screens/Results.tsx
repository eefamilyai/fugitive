import { useEffect, useMemo, useState } from "react";
import { Avatar, Button, Card, Pill, SectionHeader, eventIcon, roleColor } from "../ui/components";
import { sessions, useManager, useSession } from "../session/manager";
import { engine } from "../engine";
import { usePrefs } from "../prefs";
import { clock, distance, playerColor, speed } from "../util/format";
import { GameMap, type MapApi } from "../map/GameMap";
import { MapButtons, MapStyleSheet } from "../map/MapControls";
import type { LocationFix, PlayerStats } from "../types";

export function ResultsScreen() {
  const m = useManager();
  const session = m.kind === "active" ? m.session : null;
  const lobby = useSession(session, (s) => s.lobby);
  const events = useSession(session, (s) => s.events) ?? [];
  const prefs = usePrefs();
  const [trails, setTrails] = useState<Record<string, LocationFix[]>>({});
  const [api, setApi] = useState<MapApi | null>(null);
  const [styles, setStyles] = useState(false);
  const [cam, setCam] = useState({ zoom: 0, maxZoom: 22 });
  const [framed, setFramed] = useState(false);
  const game = lobby?.game;

  useEffect(() => { session?.trails().then(setTrails).catch(() => {}); }, [session, game?.round]);
  const stats: PlayerStats[] = useMemo(() => (game ? engine.stats(game, trails) : []), [game, trails]);

  const pts = useMemo(() => Object.values(trails).flat().map((f) => [f.lng, f.lat] as [number, number]), [trails]);
  if (api && !framed && (pts.length || game?.zone)) {
    const z = game?.zone?.stages?.[0]?.from;
    if (z) api.fitCircle(z, 30); else api.fit(pts, 50);
    setFramed(true);
  }

  if (!session || !lobby || !game?.result) return <div className="page"><div className="container"><p>Waiting for results…</p></div></div>;
  const result = game.result;
  const mode = engine.mode(game.modeId);
  const decided = result.winnerIds.length > 0;
  const won = result.winnerIds.includes(session.selfId);
  const inGame = session.selfId in game.players && decided;
  const accent = result.winningRole ? roleColor(result.winningRole) : "var(--signal)";
  const isHost = lobby.info.hostId === session.selfId;
  const name = (id: string) => game.players[id]?.name;
  const finalZone = game.zone?.stages?.length ? game.zone.stages[game.zone.stages.length - 1].to : null;
  const markers = Object.entries(trails).flatMap(([id, fx]) => {
    const pl = game.players[id]; const last = fx[fx.length - 1];
    return pl && last ? [{ id, lat: last.lat, lng: last.lng, avatar: pl.avatar, ring: roleColor(pl.role), fill: playerColor(pl.color), label: pl.name,
      look: (pl.status === "ACTIVE" || pl.status === "ESCAPED" ? "live" : "out") as "live" | "out" }] : [];
  });
  const awards = [
    (() => { const s = [...stats].sort((a, b) => b.catches - a.catches)[0]; return s?.catches ? ["🎯", "Top hunter", name(s.playerId), `${s.catches} ${s.catches === 1 ? "catch" : "catches"}`] : null; })(),
    (() => { const s = stats.filter((x) => game.players[x.playerId]?.originalRole === "runner").sort((a, b) => b.survivedMs - a.survivedMs)[0]; return s ? ["🛡️", "Survivor", name(s.playerId), clock(s.survivedMs)] : null; })(),
    (() => { const s = [...stats].sort((a, b) => b.distanceMeters - a.distanceMeters)[0]; return s && s.distanceMeters > 10 ? ["👟", "Marathon", name(s.playerId), distance(s.distanceMeters)] : null; })(),
    (() => { const s = [...stats].sort((a, b) => b.topSpeedMps - a.topSpeedMps)[0]; return s && s.topSpeedMps > 1 ? ["⚡", "Speed demon", name(s.playerId), speed(s.topSpeedMps)] : null; })(),
  ].filter(Boolean) as string[][];

  return (
    <div className="page">
      <div className="hero" style={{ background: `linear-gradient(${accent}55, transparent)` }}>
        <div style={{ fontSize: 64 }}>{!inGame ? "🏁" : won ? "🏆" : "💀"}</div>
        {inGame && <div className="label-caps" style={{ color: won ? "var(--success)" : "var(--danger)", letterSpacing: 4 }}>{won ? "Victory" : "Defeat"}</div>}
        <h1 style={{ fontSize: 34 }}>{result.headline}</h1>
        <p className="muted">{result.detail}</p>
        <Pill color="var(--text2)">Game lasted {clock((result.endedAt ?? game.startAt) - game.startAt)}</Pill>
      </div>
      <div className="container" style={{ paddingTop: 0 }}>
        <div className="card" style={{ padding: 0, height: 300, overflow: "hidden", position: "relative" }}>
          <GameMap styleId={prefs.mapStyle} mapTilerKey={prefs.mapTilerKey} customUrl={prefs.customStyleUrl} terrainShading={prefs.terrain} tilt3d={false} className="fill"
            onReady={setApi} onCamera={(c) => setCam({ zoom: c.zoom, maxZoom: c.maxZoom })}
            overlays={{ zone: finalZone, markers, trails: Object.entries(trails).map(([id, fx]) => ({ id, coords: fx.map((f) => [f.lng, f.lat] as [number, number]), color: playerColor(game.players[id]?.color ?? 11) })) }} />
          <MapButtons embedded map={api?.map ?? null} onLayers={() => setStyles(true)} zoom={cam.zoom} maxZoom={cam.maxZoom} />
        </div>
        {awards.length > 0 && <div className="row" style={{ overflowX: "auto", alignItems: "stretch" }}>{awards.map(([e, t, w, v]) => (
          <Card key={t} style={{ minWidth: 150 }}><div style={{ fontSize: 28 }}>{e}</div><div className="label-caps">{t}</div><h3>{w ?? "—"}</h3><div className="small">{v}</div></Card>))}</div>}
        <SectionHeader title="Leaderboard" />
        {stats.map((s, i) => {
          const pl = game.players[s.playerId]; if (!pl) return null;
          const outcome = pl.status === "ACTIVE" ? (pl.originalRole === "hunter" && pl.role === "hunter" ? `${pl.catches} ${pl.catches === 1 ? "catch" : "catches"}` : "Survived")
            : pl.status === "CAUGHT" ? `Caught${pl.caughtBy ? ` by ${name(pl.caughtBy) ?? "?"}` : ""}` : pl.status === "ELIMINATED" ? (pl.outReason === "zone" ? "Zone" : "Eliminated") : pl.status === "ESCAPED" ? "Escaped" : "Left";
          return (
            <div key={s.playerId} className="list-row" style={{ background: s.playerId === session.selfId ? `color-mix(in srgb, ${accent} 12%, var(--n2))` : undefined }}>
              <span className="grotesk" style={{ width: 22, fontWeight: 700 }}>{i + 1}</span>
              <Avatar emoji={pl.avatar} color={pl.color} ring={roleColor(pl.originalRole)} dim={!s.won} />
              <div className="grow"><strong>{pl.name}{s.won ? " 🏆" : ""}</strong>
                <div className="muted small">{mode.roleLabels[pl.originalRole] ?? pl.originalRole}{pl.originalRole !== pl.role ? ` → ${mode.roleLabels[pl.role] ?? pl.role}` : ""} · {outcome}</div></div>
              <div style={{ textAlign: "right" }}><div className="small">✋ {s.catches} ⏱ {clock(s.survivedMs)}</div>
                <div className="tiny muted">{distance(s.distanceMeters)} · {speed(s.topSpeedMps)} top</div></div>
            </div>
          );
        })}
        <SectionHeader title="Timeline" />
        {events.filter((e) => e.importance >= 1).map((e) => (
          <div key={e.id} className="row small"><span className="muted" style={{ width: 52 }}>{clock(e.at - game.startAt)}</span><span>{eventIcon(e.type)}</span><span>{e.text}</span></div>
        ))}
        <div className="col" style={{ marginTop: 12 }}>
          {isHost ? <Button block onClick={() => session.playAgain()}>↻ Play again</Button> : <p className="muted center">Waiting for the host to start another round…</p>}
          <Button block kind="secondary" onClick={() => sessions.leave()}>Leave</Button>
        </div>
      </div>
      {styles && <MapStyleSheet onClose={() => setStyles(false)} />}
    </div>
  );
}
