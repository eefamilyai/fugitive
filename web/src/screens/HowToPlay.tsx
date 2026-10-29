import { useNavigate } from "react-router-dom";
import { Card, SectionHeader, abilityIcon, modeIcon } from "../ui/components";
import { engine } from "../engine";

const basics: [string, string][] = [
  ["🎯", "One person hosts and shares the 6-character code. Everyone joins from their own phone — web or Android."],
  ["🏃", "The host picks the hunters (or lets the app choose). Everyone else runs."],
  ["⏱️", "Runners get a head start. Hunters can't see anyone until it ends."],
  ["📍", "Hunters see runners on the map — live, or only at pings, depending on the rules."],
  ["🌀", "The zone shrinks. Runners caught outside it are out — the dashed circle shows where it's going."],
  ["✋", "Tag someone in real life, then tap Catch. The runner confirms on their phone."],
  ["⚡", "Abilities like Cloak, Sonar and Radar Scan can turn the game around."],
  ["🏁", "When time runs out (or everyone is caught), see the results and replay everyone's routes."],
];

export function HowToPlay() {
  const nav = useNavigate();
  return (
    <div className="page"><div className="container">
      <div className="topbar"><button className="icon-btn" onClick={() => nav(-1)} aria-label="Back">←</button><h1>How to play</h1></div>
      <SectionHeader title="The basics" />
      {basics.map(([e, t]) => <div key={t} className="row" style={{ alignItems: "flex-start" }}><span style={{ fontSize: 22 }}>{e}</span><span>{t}</span></div>)}
      <SectionHeader title="Game modes" />
      {engine.catalog.modes.map((m) => (
        <Card key={m.id}><div className="row"><span className="role-icon" style={{ background: `${m.accent}2e`, borderRadius: 12 }}>{modeIcon(m.icon)}</span>
          <div><h3>{m.name}</h3><div className="muted small">{m.tagline}</div></div></div>
          <ul style={{ margin: "10px 0 0", paddingLeft: 20 }}>{m.rules.map((r) => <li key={r} style={{ marginBottom: 4 }}>{r}</li>)}</ul></Card>
      ))}
      <SectionHeader title="Abilities" />
      {engine.catalog.abilities.map((a) => <div key={a.id} className="row"><span style={{ fontSize: 22 }}>{abilityIcon(a.icon)}</span><div><strong>{a.name}</strong><div className="muted small">{a.description}</div></div></div>)}
      <SectionHeader title="Play safe" />
      <p>⚠️ Watch for traffic — never run across roads while looking at your phone. Agree on boundaries; private property is off-limits.</p>
    </div></div>
  );
}
