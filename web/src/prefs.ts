// Local preferences (profile, units, map) persisted in localStorage, with change notifications.
import { useSyncExternalStore } from "react";

export interface Prefs {
  name: string; avatar: string; color: number; onboarded: boolean;
  units: "metric" | "imperial"; mapStyle: string; terrain: boolean; map3d: boolean; mapTilerKey: string; customStyleUrl: string;
  haptics: boolean; sounds: boolean; keepScreenOn: boolean;
  /** gps | manual — manual lets you place yourself by tapping the map (desktop testing / spectating). */
  locationSource: "gps" | "manual";
  lastLobby: string | null;
  lastConfig: unknown | null;
}

const KEY = "fugitive.prefs.v1";
const defaults: Prefs = {
  name: "", avatar: "🦊", color: Math.floor(Math.random() * 12), onboarded: false,
  units: "metric", mapStyle: "hybrid", terrain: true, map3d: false, mapTilerKey: "", customStyleUrl: "",
  haptics: true, sounds: true, keepScreenOn: true, locationSource: "gps", lastLobby: null, lastConfig: null,
};

let current: Prefs = { ...defaults, ...(() => { try { return JSON.parse(localStorage.getItem(KEY) || "{}"); } catch { return {}; } })() };
const listeners = new Set<() => void>();

export function getPrefs(): Prefs { return current; }
export function setPrefs(patch: Partial<Prefs>) {
  current = { ...current, ...patch };
  localStorage.setItem(KEY, JSON.stringify(current));
  listeners.forEach((l) => l());
}
export function usePrefs(): Prefs {
  return useSyncExternalStore((l) => { listeners.add(l); return () => listeners.delete(l); }, () => current);
}
