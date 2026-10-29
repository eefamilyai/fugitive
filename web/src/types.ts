// Shapes of the JSON exchanged with the shared Kotlin engine and stored in Firebase.
// They mirror core/model (Kotlin) — the database format is identical on Android and web.

export type PlayerId = string;
export type Json = string | number | boolean | null | Json[] | { [k: string]: Json };

export interface GameConfig { modeId: string; presetId?: string | null; values: Record<string, Json> }

export interface LobbyInfo {
  code: string; hostId: string; createdAt: number; phase: "LOBBY" | "PLAYING" | "RESULTS";
  round: number; locked: boolean; schema: number;
}

export interface Member {
  id: string; name: string; avatar: string; color: number; joinedAt: number; ready: boolean;
  online: boolean; lastActive: number; wantsToHunt: boolean; appVersion: number;
}

export interface LocationFix {
  lat: number; lng: number; accuracy: number; bearing?: number; speed?: number; altitude?: number;
  time: number; battery?: number; source?: string;
}

export interface Circle { lat: number; lng: number; radius: number }

export interface GameResult { endedAt: number; reason: string; winningRole?: string; winnerIds: string[]; headline: string; detail: string }

export interface PlayerState {
  id: string; name: string; avatar: string; color: number; role: string; originalRole: string; status: string;
  catches: number; caughtBy?: string; outReason?: string; catchCode?: string; targetId?: string;
  statusChangedAt?: number;
}

/** Game node. Only fields the web UI reads are typed; the engine owns the rest. */
export interface GameState {
  round: number; modeId: string; config: GameConfig; startAt: number; headstartEndsAt: number; endsAt?: number;
  players: Record<string, PlayerState>; result?: GameResult;
  zone?: { stages: { from: Circle; to: Circle }[] };
  [k: string]: unknown;
}

export interface GameEvent {
  id: string; round: number; type: string; at: number; actor?: string; target?: string; text: string;
  importance: number; audience?: string[];
}
export interface GameAction { round: number; type: string; by: string; target?: string; payload?: Record<string, Json>; createdAt: number }
export interface ChatMessage { id: string; by: string; text: string; at: number; audience?: string[] }
export interface MapMarker {
  id: string; by: string; kind: string; lat: number; lng: number; createdAt: number; expiresAt: number;
  audience?: string[]; label?: string;
}

export interface Lobby {
  info: LobbyInfo; config: GameConfig; members: Record<string, Member>; roster: Record<string, string>; game: GameState | null;
}

// ── Engine catalog & settings form ──────────────────────────────────
export interface WebMode {
  id: string; name: string; tagline: string; icon: string; accent: string; rules: string[]; minPlayers: number;
  usesHunterSelection: boolean; roleLabels: Record<string, string>; roles: string[];
}
export interface WebPreset { id: string; name: string; description: string; modeId: string; emoji: string }
export interface WebAbility { id: string; name: string; description: string; icon: string; roles: string[] }
export interface WebRole { id: string; label: string; color: string; icon: string }
export interface Catalog { modes: WebMode[]; presets: WebPreset[]; abilities: WebAbility[]; roles: WebRole[]; schemaVersion: number }

export interface FormOption { id: string; label: string; description: string }
export interface FormSetting {
  key: string; type: "toggle" | "number" | "choice" | "point"; title: string; description: string;
  advanced: boolean; nested: boolean; visible: boolean; value: Json; display: string;
  min?: number; max?: number; step?: number; unit?: string; specialLabels: Record<string, string>; suggestions: number[];
  options: FormOption[];
}
export interface FormCategory { id: string; title: string; icon: string; settings: FormSetting[] }
export interface WebForm { modeId: string; presetId?: string; categories: FormCategory[]; values: Record<string, Json> }
export interface Issue { message: string; blocking: boolean; settingKey?: string }

// ── Projection (what the local player sees) ─────────────────────────
export interface WebFix { lat: number; lng: number; accuracy: number; bearing?: number; speed?: number; time: number; battery?: number }
export interface WebPlayer {
  id: string; name: string; avatar: string; color: number; role: string; originalRole: string; roleLabel: string;
  status: "ACTIVE" | "CAUGHT" | "ELIMINATED" | "ESCAPED" | "LEFT"; outReason?: string; caughtBy?: string; catches: number;
  visibility: "LIVE" | "SNAPSHOT" | "HIDDEN"; visibilityReason?: string; fix?: WebFix; ageMs?: number; isStale: boolean;
  distance?: number; bearing?: number; isMe: boolean; online: boolean; trail: [number, number][];
}
export interface WebZone { current: Circle; next?: Circle; phase: "WAITING" | "ANNOUNCED" | "SHRINKING" | "FINAL"; phaseEndsAt?: number; stageIndex: number; stageCount: number }
export interface Pending { id: string; hunterId: string; runnerId: string; createdAt: number; expiresAt: number; distanceMeters?: number }
export interface AbilityState { id: string; name: string; icon: string; usesLeft: number; readyAt: number; activeUntil?: number; blockedReason?: string }
export interface Objective { id: string; type: string; lat: number; lng: number; radius: number; activeFrom: number; activeUntil?: number; label?: string }
export interface Projection {
  now: number; stage: "COUNTDOWN" | "HEADSTART" | "ACTIVE" | "ENDED"; round: number; modeId: string; modeName: string;
  myRoleLabel: string; objective: string; me?: WebPlayer; isSpectating: boolean; isPrey: boolean; players: WebPlayer[];
  zone?: WebZone; zoneStatus?: { inside: boolean; marginMeters: number; outsideSince?: number; graceLeftMs?: number; applies: boolean };
  threats: { playerId: string; distance: number; bearing: number }[];
  catchOptions: { targetId: string; allowed: boolean; reason?: string; distance?: number }[];
  incomingCatch?: Pending; outgoingCatches: Pending[]; abilities: AbilityState[]; objectives: Objective[];
  timeLeftMs?: number; stageEndsInMs?: number; nextPingAt?: number; activeCounts: Record<string, number>;
  startAt: number; headstartEndsAt: number; endsAt?: number; result?: GameResult; codeLockedMs?: number;
  settings: Record<string, Json>;
}
export interface PlayerStats { playerId: string; survivedMs: number; catches: number; distanceMeters: number; topSpeedMps: number; won: boolean }
