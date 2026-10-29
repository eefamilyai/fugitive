// Reusable building blocks (web counterparts of core/designsystem + core/ui).
import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";
import QRCode from "qrcode";
import { playerColor, AVATARS, PLAYER_COLORS, normalizeCode } from "../util/format";
import { engine } from "../engine";

export function Button(p: { children: ReactNode; onClick?: () => void; kind?: "primary" | "secondary" | "ghost"; color?: string; onColor?: string;
  disabled?: boolean; loading?: boolean; block?: boolean; small?: boolean; icon?: ReactNode; style?: CSSProperties; type?: "button" | "submit" }) {
  return (
    <button type={p.type ?? "button"} className={`btn ${p.kind ?? "primary"} ${p.block ? "block" : ""} ${p.small ? "small" : ""}`}
      style={{ ...(p.color ? { ["--c" as string]: p.color } : {}), ...(p.onColor ? { ["--on" as string]: p.onColor } : {}), ...p.style }}
      disabled={p.disabled || p.loading} onClick={p.onClick}>
      {p.loading ? <span className="spinner">⏳</span> : p.icon}{p.children}
    </button>
  );
}

export function Card(p: { children: ReactNode; tint?: string; style?: CSSProperties; onClick?: () => void }) {
  return <div className={`card ${p.tint ? "tint" : ""}`} style={{ ...(p.tint ? { ["--c" as string]: p.tint } : {}), cursor: p.onClick ? "pointer" : undefined, ...p.style }} onClick={p.onClick}>{p.children}</div>;
}

export function Pill(p: { children: ReactNode; color?: string; filled?: boolean; title?: string }) {
  return <span className={`pill ${p.filled ? "filled" : ""}`} title={p.title} style={p.color ? { ["--c" as string]: p.color } : undefined}>{p.children}</span>;
}

export function Avatar(p: { emoji: string; color: number; size?: number; ring?: string; dim?: boolean; title?: string }) {
  return <span className={`avatar ${p.dim ? "dim" : ""}`} title={p.title}
    style={{ ["--s" as string]: `${p.size ?? 44}px`, ["--ring" as string]: p.ring ?? "transparent", background: playerColor(p.color) }}>{p.emoji}</span>;
}

export function Notice(p: { title: string; body?: ReactNode; icon?: string; color?: string; action?: ReactNode }) {
  return (
    <div className="notice" style={{ ["--c" as string]: p.color ?? "var(--zone)" }}>
      <span className="ic">{p.icon ?? "ℹ️"}</span>
      <div className="grow col" style={{ gap: 2 }}>
        <strong style={{ fontSize: 14 }}>{p.title}</strong>
        {p.body && <span className="muted small">{p.body}</span>}
        {p.action}
      </div>
    </div>
  );
}

export function Switch(p: { checked: boolean; onChange: (v: boolean) => void; disabled?: boolean; label?: string }) {
  return <label className="switch" aria-label={p.label}><input type="checkbox" checked={p.checked} disabled={p.disabled} onChange={(e) => p.onChange(e.target.checked)} /><span /></label>;
}

export function ToggleRow(p: { title: string; subtitle?: string; checked: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <label className="row" style={{ padding: "8px 0", cursor: p.disabled ? "default" : "pointer" }}>
      <div className="grow"><div>{p.title}</div>{p.subtitle && <div className="muted small">{p.subtitle}</div>}</div>
      <Switch checked={p.checked} onChange={p.onChange} disabled={p.disabled} label={p.title} />
    </label>
  );
}

export function Segmented<T extends string | number>(p: { options: [T, string][]; value: T; onChange: (v: T) => void }) {
  return <div className="seg">{p.options.map(([v, l]) => <button key={String(v)} className={v === p.value ? "on" : ""} onClick={() => p.onChange(v)}>{v === p.value ? "✓ " : ""}{l}</button>)}</div>;
}

export function Sheet(p: { onClose: () => void; children: ReactNode; title?: string }) {
  useEffect(() => { const k = (e: KeyboardEvent) => e.key === "Escape" && p.onClose(); window.addEventListener("keydown", k); return () => window.removeEventListener("keydown", k); }, []);
  return (
    <div className="scrim" onClick={p.onClose}>
      <div className="sheet" onClick={(e) => e.stopPropagation()} role="dialog">
        <div className="grip" />
        {p.title && <h2 style={{ marginBottom: 12 }}>{p.title}</h2>}
        {p.children}
      </div>
    </div>
  );
}

export function Dialog(p: { onClose?: () => void; title: ReactNode; children?: ReactNode; actions: ReactNode; icon?: ReactNode }) {
  return (
    <div className="scrim center" onClick={p.onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()} role="alertdialog">
        {p.icon && <div className="center" style={{ fontSize: 40 }}>{p.icon}</div>}
        <h3 className={p.icon ? "center" : ""} style={{ fontSize: 20 }}>{p.title}</h3>
        {p.children}
        <div className="dialog-actions">{p.actions}</div>
      </div>
    </div>
  );
}

export function SectionHeader(p: { title: string; action?: ReactNode }) {
  return <div className="row" style={{ marginTop: 6 }}><span className="label-caps grow" style={{ fontSize: 13 }}>{p.title}</span>{p.action}</div>;
}

export function CodeInput(p: { value: string; onChange: (v: string) => void; onSubmit: () => void }) {
  return (
    <div className="code-boxes">
      {Array.from({ length: 6 }, (_, i) => <div key={i} className={`box ${i === p.value.length ? "active" : ""}`}>{p.value[i] ?? ""}</div>)}
      <input aria-label="Lobby code" value={p.value} autoCapitalize="characters" autoComplete="off" spellCheck={false} inputMode="text"
        onChange={(e) => p.onChange(normalizeCode(e.target.value))} onKeyDown={(e) => e.key === "Enter" && p.onSubmit()} />
    </div>
  );
}

export function QrCode({ text, size = 200 }: { text: string; size?: number }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => { if (ref.current) QRCode.toCanvas(ref.current, text, { width: size, margin: 1, color: { dark: "#0B0F15", light: "#FFFFFF" } }); }, [text, size]);
  return <div className="qr"><canvas ref={ref} /></div>;
}

export function Logo({ size = 44 }: { size?: number }) {
  return (
    <svg viewBox="0 0 108 108" width={size} height={size} aria-hidden>
      <circle cx="54" cy="54" r="25" fill="none" stroke="#FF5A36" strokeWidth="5" />
      <path d="M54 22v8M54 78v8M22 54h8M78 54h8" stroke="#FF5A36" strokeWidth="4.5" strokeLinecap="round" />
      <circle cx="54" cy="54" r="15" fill="none" stroke="#A855F7" strokeWidth="3.5" strokeDasharray="6 4">
        <animateTransform attributeName="transform" type="rotate" from="0 54 54" to="360 54 54" dur="8s" repeatCount="indefinite" />
      </circle>
      <circle cx="54" cy="54" r="6.5" fill="#fff" /><circle cx="54" cy="54" r="3.5" fill="#FF5A36" />
    </svg>
  );
}

export function ProfileEditor(p: { initial: { name: string; avatar: string; color: number }; confirm: string; onConfirm: (v: { name: string; avatar: string; color: number }) => void }) {
  const [name, setName] = useState(p.initial.name);
  const [avatar, setAvatar] = useState(p.initial.avatar);
  const [color, setColor] = useState(p.initial.color);
  return (
    <div className="col" style={{ gap: 14 }}>
      <div className="row">
        <Avatar emoji={avatar} color={color} size={72} />
        <div className="grow">
          <input className="text" placeholder="Your name" value={name} maxLength={20} onChange={(e) => setName(e.target.value)} autoFocus={!name} />
          <div className="tiny muted" style={{ marginTop: 4 }}>{name.length}/20</div>
        </div>
      </div>
      <strong>Avatar</strong>
      <div className="chips">{AVATARS.map((e) => (
        <button key={e} className="avatar" onClick={() => setAvatar(e)}
          style={{ ["--s" as string]: "46px", background: e === avatar ? "color-mix(in srgb, var(--signal) 25%, transparent)" : "var(--n3)", ["--ring" as string]: e === avatar ? "var(--signal)" : "transparent", cursor: "pointer" }}>{e}</button>
      ))}</div>
      <strong>Colour</strong>
      <div className="chips">{PLAYER_COLORS.map((c, i) => (
        <button key={c} aria-label={`Colour ${i + 1}`} onClick={() => setColor(i)} className="avatar"
          style={{ ["--s" as string]: "38px", background: c, ["--ring" as string]: i === color ? "#fff" : "transparent", cursor: "pointer", color: "#fff" }}>{i === color ? "✓" : ""}</button>
      ))}</div>
      <Button block disabled={!name.trim()} onClick={() => p.onConfirm({ name: name.trim(), avatar, color })}>{p.confirm}</Button>
    </div>
  );
}

export const roleColor = (role?: string) => engine.role(role ?? "")?.color ?? "#94A3B8";
export const roleIcon = (role?: string) => ({ hunter: "🎯", runner: "🏃", spectator: "👁️" } as Record<string, string>)[role ?? ""] ?? "👤";
export const modeIcon = (icon: string) => ({ classic: "🏃", virus: "🦠", helicopter: "🚁", target: "🎯" } as Record<string, string>)[icon] ?? "🎮";
export const abilityIcon = (icon: string) => ({ radar: "📡", cloak: "👻", sonar: "🔊" } as Record<string, string>)[icon] ?? "⚡";
export const categoryIcon = (icon: string) => ({ timer: "⏱️", groups: "👥", visibility: "👁️", back_hand: "✋", radar: "🌀", tune: "🎛️", bolt: "⚡", shield: "🛡️" } as Record<string, string>)[icon] ?? "⚙️";
export function eventIcon(type: string) {
  const m: Record<string, string> = {
    caught: "✋", catch_claimed: "✋", catch_disputed: "🤚", infected: "🦠", eliminated: "☠️", outside_zone: "⚠️",
    zone_warning: "🌀", zone_shrinking: "🌀", zone_final: "🌀", back_in_zone: "✅", released: "🔥", escaped: "🚁", objective: "🚁",
    ability: "⚡", revealed: "📍", disconnected: "📵", reconnected: "📶", left: "🚪", joined: "👋", time_warning: "⏱️",
    game_over: "🏁", game_started: "🚩", target: "🎯", wrong_code: "❌",
  };
  return m[type] ?? "📣";
}
