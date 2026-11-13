// Player stats & leaderboard. Only signed-in (Google) players are recorded; guests play untracked.
// Each signed-in player writes just their own rows:
//   users/{uid}/stats            private running totals (+ the games already counted)
//   leaderboard/{uid}            public summary, read by everyone so rankings can be shown
import { getDatabase, get, onValue, ref, runTransaction, serverTimestamp, type DatabaseReference } from "firebase/database";
import { currentAccount, signIn } from "./firebase";
import { getApp } from "firebase/app";

export interface Totals {
  games: number; wins: number; losses: number; catches: number;
  survivedMs: number; distanceMeters: number; topSpeedMps: number;
}
export const EMPTY_TOTALS: Totals = { games: 0, wins: 0, losses: 0, catches: 0, survivedMs: 0, distanceMeters: 0, topSpeedMps: 0 };

export interface LeaderEntry extends Totals { uid: string; name: string; avatar: string }

/** A single finished game for one player, as computed by the engine. */
export interface GameRecord {
  gameKey: string;       // unique per game (lobby code + round + start time)
  won: boolean;
  catches: number;
  survivedMs: number;
  distanceMeters: number;
  topSpeedMps: number;
}

const db = () => getDatabase(getApp());

/** Records one finished game for the signed-in player. Idempotent per gameKey. */
export async function recordGame(rec: GameRecord, name: string, avatar: string): Promise<boolean> {
  const acct = currentAccount();
  if (!acct || !acct.google) return false; // guests don't save stats
  await signIn();
  const uid = acct.uid;
  const seen = (await get(ref(db(), `users/${uid}/games/${safeKey(rec.gameKey)}`))).val();
  if (seen) return false;

  // Mark the game and bump private totals in one go.
  const userRef = ref(db(), `users/${uid}/stats`);
  await runTransaction(userRef, (cur: Totals | null) => {
    const t: Totals = { ...EMPTY_TOTALS, ...(cur ?? {}) };
    return add(t, rec);
  });
  await runTransaction(ref(db(), `users/${uid}/games/${safeKey(rec.gameKey)}`), (cur) => (cur ? undefined : { won: rec.won, at: serverTimestamp() }));

  // Public leaderboard summary.
  await runTransaction(ref(db(), `leaderboard/${uid}`), (cur: (LeaderEntry & { updated?: unknown }) | null) => {
    const base: LeaderEntry = { ...EMPTY_TOTALS, uid, name, avatar, ...(cur ?? {}) };
    return { ...add(base, rec), uid, name, avatar };
  });
  return true;
}

function add<T extends Totals>(t: T, r: GameRecord): T {
  return {
    ...t,
    games: t.games + 1,
    wins: t.wins + (r.won ? 1 : 0),
    losses: t.losses + (r.won ? 0 : 1),
    catches: t.catches + r.catches,
    survivedMs: t.survivedMs + r.survivedMs,
    distanceMeters: t.distanceMeters + r.distanceMeters,
    topSpeedMps: Math.max(t.topSpeedMps, r.topSpeedMps),
  };
}

/** Firebase keys can't contain . $ # [ ] / — make a safe key from the game id. */
const safeKey = (s: string) => s.replace(/[.#$\[\]/]/g, "_");

/** Personal totals for the signed-in user (live). */
export function watchMyStats(uid: string, cb: (t: Totals) => void): () => void {
  return onValue(ref(db(), `users/${uid}/stats`), (snap) => cb({ ...EMPTY_TOTALS, ...(snap.val() ?? {}) }));
}

/** Top players, ranked by wins, then win rate, then catches. Live. */
export function watchLeaderboard(limit: number, cb: (rows: LeaderEntry[]) => void): () => void {
  const r: DatabaseReference = ref(db(), "leaderboard");
  return onValue(r, (snap) => {
    const rows = Object.values((snap.val() ?? {}) as Record<string, LeaderEntry>)
      .filter((x) => x && x.games > 0)
      .sort((a, b) => b.wins - a.wins || rate(b) - rate(a) || b.catches - a.catches)
      .slice(0, limit);
    cb(rows);
  });
}

export const rate = (t: Totals) => (t.games ? t.wins / t.games : 0);
