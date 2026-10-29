import { getPrefs } from "../prefs";

export function distance(m: number, units = getPrefs().units): string {
  if (units === "imperial") {
    const ft = m * 3.28084;
    return ft < 1000 ? `${Math.round(ft)} ft` : `${(m / 1609.344).toFixed(1)} mi`;
  }
  if (m < 1000) return `${Math.round(m)} m`;
  if (m < 10000) return `${(m / 1000).toFixed(1)} km`;
  return `${Math.round(m / 1000)} km`;
}
export function speed(mps: number, units = getPrefs().units) {
  return units === "imperial" ? `${(mps * 2.23694).toFixed(1)} mph` : `${(mps * 3.6).toFixed(1)} km/h`;
}
export function ago(ms: number) {
  const s = Math.floor(ms / 1000);
  if (s < 5) return "now"; if (s < 60) return `${s}s ago`; if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  return `${Math.floor(s / 3600)}h ago`;
}
export function ageBadge(ms: number) { const m = Math.floor(ms / 60000); return m < 1 ? "now" : `${m}m`; }
export function clock(ms: number) {
  const total = Math.ceil(Math.max(0, ms) / 1000);
  const h = Math.floor(total / 3600), m = Math.floor((total % 3600) / 60), s = total % 60;
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}` : `${m}:${String(s).padStart(2, "0")}`;
}
export function compass(b: number) {
  const d = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];
  return d[Math.round((((b % 360) + 360) % 360) / 45) % 8];
}
export function settingValue(value: number, unit: string | undefined): string {
  switch (unit) {
    case "SECONDS": return value < 60 ? `${value}s` : value % 60 === 0 ? `${value / 60} min` : `${Math.floor(value / 60)}:${String(value % 60).padStart(2, "0")}`;
    case "MINUTES": return value >= 60 ? (value % 60 === 0 ? `${value / 60} h` : `${Math.floor(value / 60)} h ${value % 60} min`) : `${value} min`;
    case "METERS": return distance(value);
    case "PERCENT": return `${value}%`;
    default: return String(value);
  }
}

export const PLAYER_COLORS = [
  "#EF4444", "#F97316", "#F59E0B", "#84CC16", "#10B981", "#06B6D4",
  "#3B82F6", "#6366F1", "#8B5CF6", "#D946EF", "#EC4899", "#64748B",
];
export const playerColor = (i: number) => PLAYER_COLORS[((i % 12) + 12) % 12];
export const AVATARS = [
  "🦊", "🐺", "🦁", "🐯", "🐻", "🐼", "🐨", "🐸", "🦉", "🦅", "🐙", "🦈", "🐲", "🦄", "🐝", "🦇", "🐍", "🦖",
  "🐆", "🦌", "🐧", "🐵", "🐹", "🐰", "👻", "🤖", "👽", "🥷", "🕵️", "🧙", "🦸", "🧛", "🎃", "💀", "🔥", "⚡",
];
export const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
export const normalizeCode = (raw: string) => raw.toUpperCase().split("").filter((c) => CODE_ALPHABET.includes(c)).join("").slice(0, 6);
export const randomCode = () => Array.from({ length: 6 }, () => CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)]).join("");
