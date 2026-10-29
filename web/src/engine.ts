// Typed wrapper around the shared Kotlin game engine (compiled to JS by core/engine).
// Regenerate the engine after changing rules: ./gradlew :core:engine:syncWebEngine
import { WebEngine } from "./engine/fugitive-engine.mjs";
import type {
  Catalog, Circle, GameConfig, GameEvent, GameState, Issue, Lobby, LocationFix, Member, PlayerStats, Projection, WebForm, Json,
} from "./types";

const kt = new WebEngine();
const s = JSON.stringify;

export const catalog: Catalog = JSON.parse(kt.catalog());

export const engine = {
  catalog,
  mode: (id: string) => catalog.modes.find((m) => m.id === id) ?? catalog.modes[0],
  role: (id: string) => catalog.roles.find((r) => r.id === id),
  settingsForm: (c: GameConfig): WebForm => JSON.parse(kt.settingsForm(s(c))),
  setSetting: (c: GameConfig, key: string, value: Json): GameConfig => JSON.parse(kt.setSetting(s(c), key, s(value))),
  applyPreset: (id: string): GameConfig => JSON.parse(kt.applyPreset(id)),
  switchMode: (c: GameConfig, modeId: string): GameConfig => JSON.parse(kt.switchMode(s(c), modeId)),
  resetConfig: (c: GameConfig): GameConfig => JSON.parse(kt.resetConfig(s(c))),
  validate: (l: Lobby, locs: Record<string, LocationFix>, ids: string[]): Issue[] => JSON.parse(kt.validate(s(l), s(locs), s(ids))),
  playArea: (l: Lobby, locs: Record<string, LocationFix>, ids: string[]): Circle | null => JSON.parse(kt.playArea(s(l), s(locs), s(ids))),
  startGame: (l: Lobby, locs: Record<string, LocationFix>, ids: string[], now: number):
    { ok: boolean; game?: GameState; events: GameEvent[]; error?: string } => JSON.parse(kt.startGame(s(l), s(locs), s(ids), now)),
  refereeTick: (game: GameState, members: Record<string, Member>, locs: Record<string, LocationFix>, actions: Record<string, unknown>, hostId: string, now: number):
    { changed: boolean; game: GameState; events: GameEvent[]; consumed: string[]; over: boolean } =>
    JSON.parse(kt.refereeTick(s(game), s(members), s(locs), s(actions), hostId, now)),
  project: (l: Lobby, locs: Record<string, LocationFix>, selfId: string, now: number): Projection | null =>
    JSON.parse(kt.project(s(l), s(locs), selfId, now)),
  clearHistory: () => kt.clearHistory(),
  stats: (game: GameState, trails: Record<string, LocationFix[]>): PlayerStats[] => JSON.parse(kt.stats(s(game), s(trails))),
  clock: (ms: number) => kt.formatClock(ms),
};
