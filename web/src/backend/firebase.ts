// Firebase Realtime Database transport — same paths, formats and security rules as the Android app
// (backend/firebase in the Kotlin project). Players on web and Android share lobbies seamlessly.
import { initializeApp, type FirebaseApp } from "firebase/app";
import {
  connectAuthEmulator, getAuth, GoogleAuthProvider, onAuthStateChanged, signInAnonymously, signInWithPopup, signInWithRedirect,
  signOut as fbSignOut, type Auth, type User,
} from "firebase/auth";
import {
  connectDatabaseEmulator, get, getDatabase, limitToLast, onChildAdded, onChildChanged, onChildRemoved, onDisconnect,
  onValue, push, query, ref, runTransaction, serverTimestamp, set, update, type Database, type DatabaseReference, type Query,
} from "firebase/database";
import { appConfig } from "../config";
import { randomCode } from "../util/format";
import type { ChatMessage, GameAction, GameConfig, GameEvent, GameState, Lobby, LobbyInfo, LocationFix, MapMarker, Member } from "../types";

export const SCHEMA_VERSION = 1;
export const WEB_APP_VERSION = 1;

let app: FirebaseApp | null = null;
let db: Database;
let auth: Auth;

export function onlineAvailable() { return appConfig().firebase != null; }

function ensure() {
  if (app) return;
  const cfg = appConfig();
  if (!cfg.firebase) throw new Error("Online play isn't configured on this server");
  app = initializeApp(cfg.firebase);
  db = getDatabase(app);
  auth = getAuth(app);
  if (cfg.emulatorHost) {
    connectDatabaseEmulator(db, cfg.emulatorHost, 9000);
    connectAuthEmulator(auth, `http://${cfg.emulatorHost}:9099`, { disableWarnings: true });
  }
}

export async function signIn(): Promise<string> {
  ensure();
  await auth.authStateReady();
  if (auth.currentUser) return auth.currentUser.uid;
  const cred = await signInAnonymously(auth);
  return cred.user.uid;
}

/** The current user, or null when only a guest (anonymous) session exists. */
export function currentAccount(): { uid: string; name: string | null; photo: string | null; google: boolean } | null {
  if (!app) return null;
  const u = auth.currentUser;
  if (!u) return null;
  return { uid: u.uid, name: u.displayName, photo: u.photoURL, google: !u.isAnonymous };
}

/** Subscribe to Google-account changes. Returns an unsubscribe function. */
export function watchAccount(cb: (a: ReturnType<typeof currentAccount>) => void): () => void {
  ensure();
  return onAuthStateChanged(auth, () => cb(currentAccount()));
}

/** Sign in with Google. Popup on desktop/Android; redirect fallback when popups are blocked. */
export async function signInWithGoogle(): Promise<void> {
  ensure();
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: "select_account" });
  try {
    await signInWithPopup(auth, provider);
  } catch (e: any) {
    if (e?.code === "auth/popup-blocked" || e?.code === "auth/operation-not-supported-in-this-environment") {
      await signInWithRedirect(auth, provider);
      return;
    }
    throw e;
  }
}

export async function signOutAccount(): Promise<void> {
  ensure();
  await fbSignOut(auth);
}

export type { User };

/** Removes nulls/undefined and empty containers, like the Android FireJson encoder. */
export function clean<T>(v: T): T {
  if (Array.isArray(v)) return v.map(clean).filter((x) => x !== undefined && x !== null) as T;
  if (v && typeof v === "object" && !isServerValue(v)) {
    const out: Record<string, unknown> = {};
    for (const [k, x] of Object.entries(v as Record<string, unknown>)) {
      if (x === undefined || x === null) continue;
      const c = clean(x);
      if (c && typeof c === "object" && !isServerValue(c) && Object.keys(c as object).length === 0) continue;
      out[k] = c;
    }
    return out as T;
  }
  return v;
}
const isServerValue = (v: unknown) => !!v && typeof v === "object" && ".sv" in (v as object);

export class JoinError extends Error {
  constructor(public reason: "NOT_FOUND" | "LOCKED" | "INCOMPATIBLE" | "NETWORK", message: string) { super(message); }
}

export interface Profile { name: string; avatar: string; color: number }

const lobbyRef = (code: string) => ref(db, `lobbies/${code}`);

function memberValue(uid: string, p: Profile, joinedAt: number | null) {
  return {
    id: uid, name: p.name.trim().slice(0, 20) || "Player", avatar: p.avatar, color: p.color, online: true, ready: false,
    wantsToHunt: false, appVersion: WEB_APP_VERSION,
    joinedAt: joinedAt ?? serverTimestamp(), lastActive: serverTimestamp(),
  };
}

export async function createLobby(profile: Profile, config: GameConfig): Promise<string> {
  const uid = await signIn();
  for (let i = 0; i < 8; i++) {
    const code = randomCode();
    const info: Record<string, unknown> = { code, hostId: uid, createdAt: serverTimestamp(), phase: "LOBBY", round: 0, locked: false, schema: SCHEMA_VERSION };
    const tx = await runTransaction(ref(db, `lobbies/${code}/info`), (cur) => (cur === null ? info : undefined));
    if (!tx.committed) continue;
    await set(ref(db, `lobbies/${code}/config`), clean(config));
    await set(ref(db, `lobbies/${code}/members/${uid}`), { ...memberValue(uid, profile, null), ready: true });
    return code;
  }
  throw new Error("Couldn't create a lobby. Try again.");
}

export async function joinLobby(code: string, profile: Profile): Promise<void> {
  const uid = await signIn();
  let info: LobbyInfo | null;
  try { info = (await get(ref(db, `lobbies/${code}/info`))).val(); } catch { throw new JoinError("NETWORK", "Couldn't reach the server"); }
  if (!info) throw new JoinError("NOT_FOUND", `No game with code ${code}`);
  if ((info.schema ?? 1) > SCHEMA_VERSION) throw new JoinError("INCOMPATIBLE", "That lobby needs a newer version of the app");
  let existing: Member | null = null;
  try { existing = (await get(ref(db, `lobbies/${code}/members/${uid}`))).val(); } catch { /* not a member yet */ }
  if (info.locked && !existing?.name) throw new JoinError("LOCKED", "That lobby is locked");
  await update(ref(db, `lobbies/${code}/members/${uid}`), memberValue(uid, profile, existing?.joinedAt ?? null));
}

export interface Streams {
  lobby: (l: Lobby | null) => void;
  locations: (l: Record<string, LocationFix>) => void;
  events: (e: GameEvent[]) => void;
  actions: (a: Record<string, GameAction>) => void;
  markers: (m: MapMarker[]) => void;
  chat: (c: ChatMessage[]) => void;
  connected: (c: boolean) => void;
}

/** Live connection to one lobby. */
export class LobbyConnection {
  private unsubs: (() => void)[] = [];
  private offset = 0;
  private closed = false;
  private heartbeat: number | undefined;
  private parts: { info?: LobbyInfo | null; config?: GameConfig; members?: Record<string, Member>; roster?: Record<string, string>; game?: GameState | null } = {};
  readonly ref: DatabaseReference;

  constructor(readonly code: string, readonly selfId: string, private s: Streams) {
    this.ref = lobbyRef(code);
    const selfRef = ref(db, `lobbies/${code}/members/${selfId}`);
    this.unsubs.push(onValue(ref(db, ".info/serverTimeOffset"), (snap) => { this.offset = Number(snap.val() ?? 0); }));
    this.unsubs.push(onValue(ref(db, ".info/connected"), (snap) => {
      const c = snap.val() === true;
      s.connected(c);
      if (c && !this.closed) {
        // onDisconnect handlers belong to a connection, so re-arm them after every reconnect.
        onDisconnect(ref(db, `lobbies/${code}/members/${selfId}/online`)).set(false).catch(() => {});
        onDisconnect(ref(db, `lobbies/${code}/members/${selfId}/lastActive`)).set(serverTimestamp()).catch(() => {});
        this.markOnline(selfRef);
      }
    }));
    this.heartbeat = window.setInterval(() => this.markOnline(selfRef), 15_000);

    const emitLobby = () => {
      const p = this.parts;
      if (p.info === undefined || p.config === undefined || p.members === undefined || p.roster === undefined || p.game === undefined) return;
      if (p.info === null) { s.lobby(null); return; }
      s.lobby({ info: { ...p.info, code }, config: p.config, members: p.members, roster: p.roster, game: p.game });
    };
    // Only a real permission denial means "kicked / lobby deleted". Transient errors (network, token
    // refresh) keep the last known state so a brief blip never ends a live game.
    const onErr = (err: Error & { code?: string }) => {
      if (!/permission/i.test(String(err?.message ?? err?.code ?? ""))) return;
      this.parts.info = null; emitLobby();
    };
    const watch = (path: string, fn: (v: any) => void) =>
      this.unsubs.push(onValue(ref(db, `lobbies/${code}/${path}`), (snap) => { fn(snap.val()); emitLobby(); }, onErr));
    watch("info", (v) => { this.parts.info = v; });
    watch("config", (v) => { this.parts.config = { modeId: "classic", ...(v ?? {}), values: v?.values ?? {} }; });
    watch("members", (v) => {
      const m: Record<string, Member> = {};
      for (const [id, x] of Object.entries((v ?? {}) as Record<string, Member>)) if (x && x.name) m[id] = { ...x, id };
      this.parts.members = m;
    });
    watch("roster", (v) => { this.parts.roster = v ?? {}; });
    watch("game", (v) => { this.parts.game = v ?? null; });

    this.childMap(ref(db, `lobbies/${code}/locations`), (m) => s.locations(m as Record<string, LocationFix>));
    this.childMap(query(ref(db, `lobbies/${code}/events`), limitToLast(200)), (m) =>
      s.events(Object.entries(m).map(([id, e]) => ({ ...(e as GameEvent), id })).sort((a, b) => a.at - b.at)));
    this.childMap(ref(db, `lobbies/${code}/actions`), (m) => s.actions(m as Record<string, GameAction>));
    this.childMap(query(ref(db, `lobbies/${code}/markers`), limitToLast(100)), (m) =>
      s.markers(Object.entries(m).map(([id, x]) => ({ ...(x as MapMarker), id }))));
    this.childMap(query(ref(db, `lobbies/${code}/chat`), limitToLast(150)), (m) =>
      s.chat(Object.entries(m).map(([id, x]) => ({ ...(x as ChatMessage), id })).sort((a, b) => a.at - b.at)));
  }

  /** Incrementally maintained map of a node's children (cheap for high-frequency location updates). */
  private childMap(q: Query, emit: (m: Record<string, unknown>) => void) {
    const map: Record<string, unknown> = {};
    let scheduled = false;
    const flush = () => { scheduled = false; emit({ ...map }); };
    const later = () => { if (!scheduled) { scheduled = true; queueMicrotask(flush); } };
    const err = () => {};
    this.unsubs.push(onChildAdded(q, (snap) => { map[snap.key!] = snap.val(); later(); }, err));
    this.unsubs.push(onChildChanged(q, (snap) => { map[snap.key!] = snap.val(); later(); }, err));
    this.unsubs.push(onChildRemoved(q, (snap) => { delete map[snap.key!]; later(); }, err));
    get(q).then(() => later()).catch(() => later());
  }

  private markOnline(selfRef: DatabaseReference) {
    if (this.closed) return;
    update(selfRef, { online: true, lastActive: serverTimestamp() }).catch(() => { /* kicked or offline */ });
  }

  /** Server-synchronised clock (epoch ms). */
  now() { return Date.now() + this.offset; }

  // ── Player commands ────────────────────────────────────────────────
  updateSelf(patch: Partial<Member>) { return update(ref(db, `lobbies/${this.code}/members/${this.selfId}`), clean(patch)); }
  publishLocation(fix: LocationFix) { return set(ref(db, `lobbies/${this.code}/locations/${this.selfId}`), clean(fix)); }
  appendTrail(fix: LocationFix) { return push(ref(db, `lobbies/${this.code}/trails/${this.selfId}`), clean(fix)); }
  submitAction(a: GameAction) { return push(ref(db, `lobbies/${this.code}/actions`), clean({ ...a, id: "" })); }
  sendChat(m: Omit<ChatMessage, "id">) { return push(ref(db, `lobbies/${this.code}/chat`), clean(m)); }
  placeMarker(m: Omit<MapMarker, "id">) { return push(ref(db, `lobbies/${this.code}/markers`), clean(m)); }

  async trails(): Promise<Record<string, LocationFix[]>> {
    const v = (await get(ref(db, `lobbies/${this.code}/trails`))).val() ?? {};
    const out: Record<string, LocationFix[]> = {};
    for (const [id, pts] of Object.entries(v as Record<string, Record<string, LocationFix>>)) {
      out[id] = Object.values(pts).sort((a, b) => a.time - b.time);
    }
    return out;
  }

  async leave() {
    const snap = (await get(this.ref)).val() ?? {};
    const info: LobbyInfo | undefined = snap.info;
    const others = Object.entries((snap.members ?? {}) as Record<string, Member>).filter(([id, m]) => id !== this.selfId && m?.name);
    const selfOnline = ref(db, `lobbies/${this.code}/members/${this.selfId}/online`);
    const selfActive = ref(db, `lobbies/${this.code}/members/${this.selfId}/lastActive`);
    await onDisconnect(selfOnline).cancel().catch(() => {});
    await onDisconnect(selfActive).cancel().catch(() => {});
    if (info?.hostId === this.selfId) {
      const next = others.filter(([, m]) => m.online).sort((a, b) => (a[1].joinedAt ?? 0) - (b[1].joinedAt ?? 0))[0];
      if (!next) { await set(this.ref, null); return; } // last one out turns off the lights
      await set(ref(db, `lobbies/${this.code}/info/hostId`), next[0]);
    }
    await update(this.ref, { [`members/${this.selfId}`]: null, [`locations/${this.selfId}`]: null });
  }

  // ── Host commands ──────────────────────────────────────────────────
  updateConfig(c: GameConfig) { return set(ref(db, `lobbies/${this.code}/config`), clean(c)); }
  setRoster(r: Record<string, string>) { return set(ref(db, `lobbies/${this.code}/roster`), Object.keys(r).length ? r : null); }
  setLocked(locked: boolean) { return set(ref(db, `lobbies/${this.code}/info/locked`), locked); }
  kick(id: string) { return update(this.ref, { [`members/${id}`]: null, [`roster/${id}`]: null, [`locations/${id}`]: null }); }
  transferHost(id: string) { return set(ref(db, `lobbies/${this.code}/info/hostId`), id); }

  async startGame(game: GameState, events: GameEvent[]) {
    await update(this.ref, { events: null, actions: null, trails: null, markers: null });
    const u: Record<string, unknown> = { game: clean(game), "info/phase": "PLAYING", "info/round": game.round };
    for (const e of events) u[`events/${push(ref(db, `lobbies/${this.code}/events`)).key}`] = clean({ ...e, id: "" });
    await update(this.ref, u);
  }

  async commitTick(game: GameState, events: GameEvent[], consumed: string[], phase: string | null) {
    const u: Record<string, unknown> = { game: clean(game) };
    for (const e of events) u[`events/${push(ref(db, `lobbies/${this.code}/events`)).key}`] = clean({ ...e, id: "" });
    for (const id of consumed) u[`actions/${id}`] = null;
    if (phase) u["info/phase"] = phase;
    await update(this.ref, u);
  }

  async resetToLobby() {
    await update(this.ref, { "info/phase": "LOBBY", game: null, events: null, actions: null, trails: null, markers: null });
    const members = (await get(ref(db, `lobbies/${this.code}/members`))).val() ?? {};
    const u: Record<string, unknown> = {};
    for (const id of Object.keys(members)) u[`members/${id}/ready`] = false;
    if (Object.keys(u).length) await update(this.ref, u).catch(() => {});
  }

  async claimHost(expected: string): Promise<boolean> {
    const r = await runTransaction(ref(db, `lobbies/${this.code}/info/hostId`), (cur) => (cur === expected ? this.selfId : undefined));
    return r.committed;
  }

  close() {
    this.closed = true;
    window.clearInterval(this.heartbeat);
    this.unsubs.forEach((u) => u());
    this.unsubs = [];
  }
}
