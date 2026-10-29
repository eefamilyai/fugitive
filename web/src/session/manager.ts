// Owns the single active GameSession (survives route changes), like GameSessionManager on Android.
import { useSyncExternalStore } from "react";
import { createLobby, JoinError, joinLobby, onlineAvailable, signIn } from "../backend/firebase";
import { getPrefs, setPrefs } from "../prefs";
import { engine } from "../engine";
import { normalizeCode } from "../util/format";
import type { GameConfig } from "../types";
import { GameSession } from "./GameSession";
import { unlockAudio } from "./feedback";

export type ManagerState =
  | { kind: "idle"; notice: string | null }
  | { kind: "connecting"; message: string }
  | { kind: "active"; session: GameSession };

let state: ManagerState = { kind: "idle", notice: null };
const listeners = new Set<() => void>();
function setState(s: ManagerState) { state = s; listeners.forEach((l) => l()); }

export const sessions = {
  get state() { return state; },
  get current(): GameSession | null { return state.kind === "active" ? state.session : null; },
  online: () => onlineAvailable(),

  async create(): Promise<string | null> {
    return run("Creating lobby…", async () => {
      const p = getPrefs();
      const cfg = (p.lastConfig as GameConfig | null) ?? engine.applyPreset("classic");
      const code = await createLobby(profile(), cfg);
      return code;
    });
  },

  async join(raw: string): Promise<string | null> {
    const code = normalizeCode(raw);
    if (code.length !== 6) return "Codes are 6 characters";
    return run(`Joining ${code}…`, async () => { await joinLobby(code, profile()); return code; });
  },

  async rejoin(): Promise<string | null> {
    const code = getPrefs().lastLobby;
    if (!code) return "Nothing to rejoin";
    return run(`Reconnecting to ${code}…`, async () => {
      try { await joinLobby(code, profile()); } catch (e) {
        if (e instanceof JoinError && e.reason === "NOT_FOUND") setPrefs({ lastLobby: null });
        throw e;
      }
      return code;
    });
  },

  async leave() {
    const s = sessions.current;
    setPrefs({ lastLobby: null });
    setState({ kind: "idle", notice: null });
    await s?.leave();
  },

  consumeNotice() { if (state.kind === "idle" && state.notice) setState({ kind: "idle", notice: null }); },
};

function profile() { const p = getPrefs(); return { name: p.name, avatar: p.avatar, color: p.color }; }

async function run(message: string, open: () => Promise<string>): Promise<string | null> {
  unlockAudio();
  setState({ kind: "connecting", message });
  try {
    const uid = await signIn();
    const code = await open();
    sessions.current?.close();
    const session = new GameSession(code, uid);
    const loaded = await new Promise<boolean>((resolve) => {
      const t = window.setTimeout(() => { un(); resolve(false); }, 15000);
      const un = session.subscribe(() => {
        if (session.state.lobby && uid in session.state.lobby.members) { window.clearTimeout(t); un(); resolve(true); }
      });
    });
    if (!loaded) { session.close(); throw new Error("Couldn't load the lobby. Check your connection."); }
    setPrefs({ lastLobby: code });
    setState({ kind: "active", session });
    const un = session.subscribe(() => {
      const reason = session.state.closedReason;
      if (reason && sessions.current === session) {
        un(); session.close(); setPrefs({ lastLobby: null }); setState({ kind: "idle", notice: reason });
      }
    });
    return null;
  } catch (e) {
    setState({ kind: "idle", notice: null });
    return e instanceof Error ? e.message : "Something went wrong";
  }
}

export function useManager(): ManagerState {
  return useSyncExternalStore((l) => { listeners.add(l); return () => listeners.delete(l); }, () => state);
}

export function useSession<T>(session: GameSession | null, pick: (s: GameSession["state"]) => T): T | undefined {
  return useSyncExternalStore(
    (l) => session?.subscribe(l) ?? (() => {}),
    () => (session ? pick(session.state) : undefined),
  );
}
