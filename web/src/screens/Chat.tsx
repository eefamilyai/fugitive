import { useEffect, useRef, useState } from "react";
import { Avatar } from "../ui/components";
import type { ChatMessage, Member } from "../types";
import { playerColor } from "../util/format";

const QUICK = ["👋", "Ready!", "On my way", "Where are you?", "Nice catch!", "GG"];

export function ChatPanel(p: { messages: ChatMessage[]; members: Record<string, Member>; selfId: string; onSend: (t: string, team: boolean) => void; allowTeam?: boolean }) {
  const [text, setText] = useState("");
  const [team, setTeam] = useState(!!p.allowTeam);
  const end = useRef<HTMLDivElement>(null);
  useEffect(() => end.current?.scrollIntoView({ behavior: "smooth" }), [p.messages.length]);
  const send = (t: string) => { if (t.trim()) { p.onSend(t, team && !!p.allowTeam); setText(""); } };
  return (
    <div className="col">
      <div className="chat-list">
        {p.messages.length === 0 && <span className="muted">No messages yet. Say hi!</span>}
        {p.messages.map((m) => {
          const who = p.members[m.by]; const mine = m.by === p.selfId;
          return (
            <div key={m.id} className="row" style={{ alignItems: "flex-end", justifyContent: mine ? "flex-end" : "flex-start" }}>
              {!mine && <Avatar emoji={who?.avatar ?? "❔"} color={who?.color ?? 11} size={28} />}
              <div className={`bubble ${mine ? "mine" : ""}`}>
                {!mine && <div className="tiny" style={{ color: playerColor(who?.color ?? 11), fontWeight: 700 }}>{who?.name ?? "Player"}</div>}
                {m.audience?.length ? <div className="tiny" style={{ color: "var(--zone)" }}>TEAM</div> : null}
                <div>{m.text}</div>
              </div>
            </div>
          );
        })}
        <div ref={end} />
      </div>
      <div className="chips">
        {p.allowTeam && <button className={`chip ${team ? "on" : ""}`} onClick={() => setTeam(!team)}>Team only</button>}
        {QUICK.map((q) => <button key={q} className="chip" onClick={() => send(q)}>{q}</button>)}
      </div>
      <form className="row" onSubmit={(e) => { e.preventDefault(); send(text); }}>
        <input className="text" placeholder="Message" value={text} maxLength={280} onChange={(e) => setText(e.target.value)} />
        <button className="icon-btn" type="submit" disabled={!text.trim()} aria-label="Send">➤</button>
      </form>
    </div>
  );
}
