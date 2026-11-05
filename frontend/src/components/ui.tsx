import type { CSSProperties, ReactNode } from 'react';
import type { Category, Priority, RiskLevel, TicketStatus } from '../api/types';
import { DEPT_LABEL, DEPT_VAR, PRIORITY_LABEL, RISK_COLOR, RISK_LABEL, STATUS_COLOR, STATUS_LABEL } from '../theme';

export function Dept({ value }: { value: Category }) {
  return <span className="tag"><i className="dot" style={{ background: DEPT_VAR[value] }} />{DEPT_LABEL[value]}</span>;
}

export function StatusMark({ status }: { status: TicketStatus }) {
  return <span className="tag"><i className="dot" style={{ background: STATUS_COLOR[status] }} />{STATUS_LABEL[status]}</span>;
}

export function PriorityMark({ priority }: { priority: Priority }) {
  const hot = priority === 'URGENT' || priority === 'HIGH';
  return <span className={hot ? 'tag tag-hot' : 'tag'}>{PRIORITY_LABEL[priority]} priority</span>;
}

export function RiskMark({ level }: { level: RiskLevel }) {
  return <span className="tag"><i className="dot" style={{ background: RISK_COLOR[level] }} />{RISK_LABEL[level]}</span>;
}

export function Panel({ title, aside, children, tight }: { title?: string; aside?: ReactNode; children: ReactNode; tight?: boolean }) {
  return (
    <section className={tight ? 'panel panel-tight' : 'panel'}>
      {(title || aside) && <header className="panel-head">{title && <h2>{title}</h2>}{aside && <div>{aside}</div>}</header>}
      {children}
    </section>
  );
}

export function Stats({ items }: { items: { value: ReactNode; label: string }[] }) {
  return (
    <dl className="stats">
      {items.map((s) => (
        <div key={s.label}><dd>{s.value}</dd><dt>{s.label}</dt></div>
      ))}
    </dl>
  );
}

export function Bars({ rows }: { rows: { label: string; value: number; color: string; text?: string }[] }) {
  const max = Math.max(1, ...rows.map((r) => r.value));
  return (
    <ul className="bars">
      {rows.map((r) => (
        <li key={r.label}>
          <span className="bars-label">{r.label}</span>
          <span className="bars-track"><span style={{ width: `${(r.value / max) * 100}%`, background: r.color } as CSSProperties} /></span>
          <span className="bars-value">{r.text ?? r.value}</span>
        </li>
      ))}
    </ul>
  );
}

export function Notice({ tone = 'info', children }: { tone?: 'info' | 'warn' | 'good'; children: ReactNode }) {
  return <p className={`notice notice-${tone}`} role="status">{children}</p>;
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return <div className="empty"><h3>{title}</h3>{children && <p>{children}</p>}</div>;
}

export function Status({ loading, error }: { loading: boolean; error: string | null }) {
  if (loading) return <p className="muted">Loading…</p>;
  if (error) return <p className="notice notice-warn" role="alert">{error}</p>;
  return null;
}

export const fmtDate = (iso: string | null): string =>
  iso ? new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—';
