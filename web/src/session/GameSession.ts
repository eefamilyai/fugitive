// A live connection to one lobby plus everything that runs while you're in it: location sharing,
// the referee loop (on the host), host take-over and the projection the UI renders.
// Same responsibilities as core/data GameSession on Android, using the same shared engine.
import { engine } from "../engine";
import { LobbyConnection, type Profile } from "../backend/firebase";
import { getPrefs } from "../prefs";
import type { ChatMessage, GameAction, GameEvent, GameState, Json, Lobby, LocationFix, MapMarker, Projection } from "../types";
import { locationSources, type LocationProblem } from "./location";
import { cue, type Cue } from "./feedback";

export interface SessionState {
  code: string;
  selfId: string;
  lobby: Lobby | null;
  locations: Record<string, LocationFix>;
  events: GameEvent[];
  chat: ChatMessage[];
  markers: MapMarker[];
  connected: boolean;
  projection: Projection | null;
  myFix: LocationFix | null;
  locationProblem: LocationProblem;
  /** Set when the lobby disappears or we were removed. */
  closedReason: string | null;
  banners: GameEvent[];
}

const REFEREE_MS = 1000, PROJECT_MS = 500, TRAIL_MS = 5000, HOST_TIMEOUT_MS = 8000, HOST_STALE_MS = 50_000;

export class GameSession {
  readonly conn: LobbyConnection;
  private listeners = new Set<() => void>();
  private timers: number[] = [];
  private stopLocation: (() => void) | null = null;
  private actions: Record<string, GameAction> = {};
  private lastWritten: GameState | null = null;
  private processed = new Set<string>();
  private refereeBusy = false;
  private lastTrail = 0;
  private everLoaded = false;
  private seenEvents = new Set<string>();
  private eventsPrimed = false;
  private hostSuspectSince = 0;
  private lastBeat = 0;
  private lastDanger = 0;

  state: SessionState;

  constructor(readonly code: string, readonly selfId: string) {
    this.state = {
      code, selfId, lobby: null, locations: {}, events: [], chat: [], markers: [], connected: false, projection: null,
      myFix: null, locationProblem: null, closedReason: null, banners: [],
    };
    engine.clearHistory();
    this.conn = new LobbyConnection(code, selfId, {
      lobby: (l) => this.onLobby(l),
      locations: (l) => this.patch({ locations: this.mergeSelf(l) }),
      events: (e) => this.onEvents(e),
      actions: (a) => { this.actions = a; },
      markers: (m) => this.patch({ markers: m }),
      chat: (c) => this.patch({ chat: c }),
      connected: (c) => this.patch({ connected: c }),
    });
    this.restartLocation();
    this.timers.push(window.setInterval(() => this.project(), PROJECT_MS));
    this.timers.push(window.setInterval(() => this.refereeTick(), REFEREE_MS));
    this.timers.push(window.setInterval(() => this.checkHost(), 2000));
    this.timers.push(window.setInterval(() => this.dangerFeedback(), 250));
  }

  // ── store plumbing ───────────────────────────────────────────────
  subscribe = (l: () => void) => { this.listeners.add(l); return () => { this.listeners.delete(l); }; };
  getState = () => this.state;
  private patch(p: Partial<SessionState>) { this.state = { ...this.state, ...p }; this.listeners.forEach((l) => l()); }

  get isHost() { return this.state.lobby?.info.hostId === this.selfId; }
  now() { return this.conn.now(); }

  private mergeSelf(remote: Record<string, LocationFix>) {
    return this.state.myFix ? { ...remote, [this.selfId]: this.state.myFix } : remote;
  }

  private onLobby(l: Lobby | null) {
    if (l && this.selfId in l.members) this.everLoaded = true;
    else if (this.everLoaded && !this.state.closedReason) {
      this.patch({ closedReason: l ? "You were removed from the lobby" : "The lobby was closed" });
    }
    const prevRound = this.state.lobby?.game?.round;
    if (l?.game && l.game.round !== prevRound) { this.lastWritten = null; this.processed.clear(); }
    this.patch({ lobby: l });
    this.project();
  }

  private onEvents(all: GameEvent[]) {
    const l = this.state.lobby;
    const round = l?.info.round ?? 0;
    const role = l?.game?.players?.[this.selfId]?.role;
    const visible = all.filter((e) => e.round === round && (!e.audience?.length || e.audience.includes(this.selfId) || (role && e.audience.includes(role))));
    if (!this.eventsPrimed) { visible.forEach((e) => this.seenEvents.add(e.id)); this.eventsPrimed = true; this.patch({ events: visible }); return; }
    const fresh = visible.filter((e) => !this.seenEvents.has(e.id));
    fresh.forEach((e) => this.seenEvents.add(e.id));
    const banners = [...this.state.banners];
    for (const e of fresh) {
      const aboutMe = e.target === this.selfId || e.audience?.includes(this.selfId);
      const c = cueFor(e, this.selfId);
      if (e.importance >= 1 || aboutMe) { if (c) cue(c); banners.unshift(e); }
    }
    this.patch({ events: visible, banners: banners.slice(0, 3) });
  }

  dismissBanner(id: string) { this.patch({ banners: this.state.banners.filter((b) => b.id !== id) }); }

  // ── location ─────────────────────────────────────────────────────
  restartLocation() {
    this.stopLocation?.();
    const src = locationSources[getPrefs().locationSource] ?? locationSources.gps;
    this.stopLocation = src.start(
      (f) => this.onFix({ ...f, time: this.now() }),
      (p) => this.patch({ locationProblem: p }),
    );
  }

  private lastPublished = 0;
  private onFix(fix: LocationFix) {
    this.patch({ myFix: fix, locations: { ...this.state.locations, [this.selfId]: fix } });
    const l = this.state.lobby;
    const playing = l?.info.phase === "PLAYING" && l.game && !l.game.result;
    const intervalSec = playing ? Number(l!.game!.config.values?.["location_interval_sec"] ?? 2) : 4;
    if (fix.time - this.lastPublished >= Math.max(800, intervalSec * 1000 - 300)) {
      this.lastPublished = fix.time;
      this.conn.publishLocation(fix).catch(() => {});
    }
    if (playing && fix.time - this.lastTrail >= TRAIL_MS) { this.lastTrail = fix.time; this.conn.appendTrail(fix).catch(() => {}); }
  }

  // ── projection ───────────────────────────────────────────────────
  private project() {
    const l = this.state.lobby;
    if (!l?.game) { if (this.state.projection) this.patch({ projection: null }); return; }
    try { this.patch({ projection: engine.project(l, this.state.locations, this.selfId, this.now()) }); }
    catch (e) { console.error("projection failed", e); }
  }

  // ── referee (host only) ──────────────────────────────────────────
  private async refereeTick() {
    const l = this.state.lobby;
    if (!l || !this.isHost || l.info.phase !== "PLAYING" || !l.game || this.refereeBusy) return;
    const base = this.lastWritten && this.lastWritten.round === l.game.round ? this.lastWritten : l.game;
    const fresh: Record<string, GameAction> = {};
    for (const [id, a] of Object.entries(this.actions)) if (!this.processed.has(id)) fresh[id] = a;
    this.refereeBusy = true;
    try {
      const out = engine.refereeTick(base, l.members, this.state.locations, fresh, this.selfId, this.now());
      // Keep committing a finished game until the phase flips to RESULTS (retries a failed final write).
      if (!out.changed && !out.over) return;
      await this.conn.commitTick(out.game, out.events, out.consumed, out.over ? "RESULTS" : null);
      this.lastWritten = out.game;
      out.consumed.forEach((id) => this.processed.add(id));
    } catch (e) {
      console.error("referee tick failed", e);
    } finally { this.refereeBusy = false; }
  }

  // ── host take-over ───────────────────────────────────────────────
  private async checkHost() {
    const l = this.state.lobby;
    if (!l || this.isHost) { this.hostSuspectSince = 0; return; }
    const host = l.members[l.info.hostId];
    const stale = !host || this.now() - (host.lastActive ?? 0) > HOST_STALE_MS;
    const hostOk = host?.online && !stale;
    const successor = Object.values(l.members).filter((m) => m.online).sort((a, b) => a.joinedAt - b.joinedAt)[0]?.id;
    if (hostOk || successor !== this.selfId) { this.hostSuspectSince = 0; return; }
    if (!this.hostSuspectSince) { this.hostSuspectSince = Date.now(); return; }
    if (Date.now() - this.hostSuspectSince >= HOST_TIMEOUT_MS) {
      this.hostSuspectSince = 0;
      await this.conn.claimHost(l.info.hostId).catch(() => false);
    }
  }

  private dangerFeedback() {
    const p = this.state.projection;
    if (!p || p.result || p.me?.status !== "ACTIVE") return;
    const now = Date.now();
    const t = p.threats[0];
    if (t) {
      const radius = Math.max(1, Number(p.settings["proximity_alert_m"] ?? 100));
      const period = 600 + 2400 * Math.min(1, t.distance / radius);
      if (now - this.lastBeat >= period) { this.lastBeat = now; cue("heartbeat"); }
    }
    const zs = p.zoneStatus;
    if (zs?.applies && !zs.inside && now - this.lastDanger >= 2500) { this.lastDanger = now; cue("danger"); }
  }

  // ── lobby commands ───────────────────────────────────────────────
  setReady = (ready: boolean) => this.conn.updateSelf({ ready });
  setWantsToHunt = (wantsToHunt: boolean) => this.conn.updateSelf({ wantsToHunt });
  updateProfile = (p: Profile) => this.conn.updateSelf({ name: p.name, avatar: p.avatar, color: p.color });
  updateConfig = (c: Lobby["config"]) => this.conn.updateConfig(c);
  setRole = (id: string, role: string | null) => {
    const r = { ...(this.state.lobby?.roster ?? {}) };
    if (role) r[id] = role; else delete r[id];
    return this.conn.setRoster(r);
  };
  kick = (id: string) => this.conn.kick(id);
  transferHost = (id: string) => this.conn.transferHost(id);
  setLocked = (b: boolean) => this.conn.setLocked(b);

  participants(): string[] {
    const l = this.state.lobby;
    return l ? Object.values(l.members).filter((m) => m.online).sort((a, b) => a.joinedAt - b.joinedAt).map((m) => m.id) : [];
  }

  async startGame(): Promise<string | null> {
    const l = this.state.lobby;
    if (!l || !this.isHost) return "Only the host can start";
    const res = engine.startGame(l, this.state.locations, this.participants(), this.now());
    if (!res.ok || !res.game) return res.error ?? "Couldn't start the game";
    this.lastWritten = null; this.processed.clear(); engine.clearHistory();
    await this.conn.startGame(res.game, res.events);
    return null;
  }
  playAgain = () => this.conn.resetToLobby();

  // ── game commands (validated by the referee) ─────────────────────
  private submit(type: string, target?: string, payload?: Record<string, Json>) {
    const round = this.state.lobby?.game?.round;
    if (round == null) return Promise.resolve();
    return this.conn.submitAction({ round, type, by: this.selfId, target, payload, createdAt: this.now() });
  }
  claimCatch = (target: string) => this.submit("catch_claim", target);
  respondToCatch = (id: string, accept: boolean) => this.submit("catch_response", undefined, { catch: id, accept });
  submitCode = (code: string) => this.submit("code_catch", undefined, { code });
  reportCaught = (by?: string) => this.submit("self_report", by);
  useAbility = (id: string) => this.submit("use_ability", undefined, { ability: id });
  forfeit = () => this.submit("leave");
  endGame = () => this.submit("host_end");

  sendChat(text: string, teamOnly: boolean) {
    const clean = text.trim().slice(0, 280);
    if (!clean) return;
    const me = this.state.lobby?.game?.players?.[this.selfId];
    const role = me?.status === "ACTIVE" ? me.role : undefined;
    return this.conn.sendChat({ by: this.selfId, text: clean, at: this.now(), audience: teamOnly && role ? [role] : undefined });
  }
  dropMarker(kind: string, lat: number, lng: number) {
    const role = this.state.lobby?.game?.players?.[this.selfId]?.role;
    const now = this.now();
    return this.conn.placeMarker({ by: this.selfId, kind, lat, lng, createdAt: now, expiresAt: now + 120_000, audience: role ? [role] : undefined });
  }
  trails = () => this.conn.trails();

  async leave() {
    try { if (!this.state.lobby?.game?.result) await this.forfeit(); } catch { /* ignore */ }
    try { await this.conn.leave(); } catch { /* ignore */ }
    this.close();
  }

  close() {
    this.timers.forEach((t) => window.clearInterval(t));
    this.stopLocation?.();
    this.conn.close();
  }
}

function cueFor(e: GameEvent, selfId: string): Cue | null {
  switch (e.type) {
    case "catch_claimed": case "caught": case "infected": case "eliminated": return e.target === selfId ? "caught" : "notice";
    case "released": case "zone_shrinking": case "zone_warning": case "revealed": case "target": return "alert";
    case "outside_zone": return "danger";
    case "escaped": case "game_over": return "success";
    default: return e.importance >= 1 ? "notice" : null;
  }
}
