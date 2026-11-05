import { useState, type CSSProperties } from 'react';
import { useParams } from 'react-router-dom';
import { ticketApi, userApi } from '../api/endpoints';
import { ApiError } from '../api/client';
import { CATEGORIES, PRIORITIES, STATUSES, TONES, type Category, type Priority, type Tone, type TicketStatus, type User } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useAsync } from '../components/useAsync';
import { Dept, Notice, Panel, PriorityMark, RiskMark, Status, StatusMark, fmtDate } from '../components/ui';
import { DEPT_LABEL, PRIORITY_LABEL, RISK_COLOR, STATUS_LABEL, flagText } from '../theme';

const TONE_LABEL: Record<Tone, string> = { FORMAL: 'Formal', FRIENDLY: 'Friendly', URGENT: 'Urgent' };

export default function TicketDetailPage() {
  const id = Number(useParams().id);
  const { isStaff, isManager } = useAuth();
  const ticket = useAsync(() => ticketApi.get(id), [id]);
  const responses = useAsync(() => ticketApi.responses(id), [id]);
  const staff = useAsync<User[]>(() => (isManager ? userApi.staff() : Promise.resolve([])), [isManager]);
  const [tone, setTone] = useState<Tone>('FORMAL');
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [cat, setCat] = useState<Category | ''>('');
  const [pri, setPri] = useState<Priority | ''>('');

  async function run(action: () => Promise<unknown>, done: string) {
    setBusy(true);
    setMsg(null);
    try {
      await action();
      setMsg(done);
      ticket.reload();
      responses.reload();
    } catch (e) {
      setMsg(e instanceof ApiError ? e.message : 'That did not go through. Try again.');
    } finally {
      setBusy(false);
    }
  }

  const t = ticket.data;
  const steps = t ? [
    { name: 'Received', when: t.createdAt, done: true },
    { name: t.assigneeName ? `Assigned to ${t.assigneeName}` : 'Assigned', when: null as string | null, done: t.assigneeId != null },
    { name: 'First reply sent', when: t.firstResponseAt, done: t.firstResponseAt != null },
    { name: 'Resolved', when: t.resolvedAt, done: t.resolvedAt != null },
  ] : [];

  return (
    <>
      <Status loading={ticket.loading} error={ticket.error} />
      {t && (
        <>
          <div className="hero">
            <h1>{t.title}</h1>
            <p className="lede" style={{ fontFamily: 'var(--display)', fontSize: 15 }}>
              <Dept value={t.category} /><PriorityMark priority={t.priority} /><StatusMark status={t.status} />
              {t.escalated && <span className="tag tag-hot">Past its deadline</span>}
            </p>
          </div>
          {msg && <Notice tone="info">{msg}</Notice>}

          <div className="split">
            <div>
              <p className="prose">{t.description}</p>

              <div style={{ marginTop: 40 }}>
                <Panel title="Replies" aside={isStaff && (
                  <div className="row">
                    <div><label htmlFor="tone">Tone</label>
                      <select id="tone" value={tone} onChange={(e) => setTone(e.target.value as Tone)}>
                        {TONES.map((x) => <option key={x} value={x}>{TONE_LABEL[x]}</option>)}</select></div>
                    <button className="btn" disabled={busy} onClick={() => run(() => ticketApi.generate(id, tone), 'Reply drafted.')}>Draft reply</button>
                  </div>
                )}>
                  <Status loading={responses.loading} error={responses.error} />
                  {responses.data?.length === 0 && (
                    <p className="muted">{isStaff ? 'No replies yet. Draft one and the assistant will check it before it is sent.' : 'No replies yet. You’ll get an update as soon as the team responds.'}</p>
                  )}
                  {(responses.data ?? []).map((r) => (
                    <div className="reply" key={r.id}>
                      <div className="reply-meta">
                        <RiskMark level={r.riskLevel} />
                        <span className="tag">{r.status === 'APPROVED' ? 'Sent' : r.status === 'REJECTED' ? 'Rejected' : 'Waiting for approval'}</span>
                        <span className="tag">{TONE_LABEL[r.tone]} tone</span>
                        <span className="tag muted">{fmtDate(r.createdAt)}</span>
                      </div>
                      <div className="letter" style={{ '--edge': RISK_COLOR[r.riskLevel] } as CSSProperties}>{r.body}</div>
                      {r.riskFlags.length > 0 && <p className="reply-note">Held for review because it {r.riskFlags.map(flagText).join(', and ')}.</p>}
                      {isStaff && r.transparencyNote && <p className="reply-note">How this was written: {r.transparencyNote}</p>}
                    </div>
                  ))}
                </Panel>
              </div>
            </div>

            <aside className="rail">
              <section>
                <h2>Journey</h2>
                <ol className="timeline">
                  {steps.map((s) => (
                    <li key={s.name} className={s.done ? 'done' : undefined}>
                      <i className="pip" />
                      <strong>{s.name}</strong>
                      {s.when && <small>{fmtDate(s.when)}</small>}
                    </li>
                  ))}
                </ol>
              </section>

              <section>
                <h2>Details</h2>
                <dl className="facts">
                  <div><dt>Raised by</dt><dd>{t.requesterName}</dd></div>
                  <div><dt>Handled by</dt><dd>{t.assigneeName ?? 'Not assigned yet'}</dd></div>
                  <div><dt>Routing</dt><dd>{t.classificationSource === 'MANUAL' ? 'Set by a person' : `Chosen by the assistant (${t.classificationConfidence != null ? Math.round(t.classificationConfidence * 100) : '—'}% sure)`}</dd></div>
                </dl>
              </section>

              {isStaff && (
                <section>
                  <h2>Update this ticket</h2>
                  <div className="field"><label htmlFor="st">Status</label>
                    <select id="st" value={t.status} disabled={busy} onChange={(e) => run(() => ticketApi.setStatus(id, e.target.value as TicketStatus), 'Status updated.')}>
                      {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}</select></div>
                  {isManager && (
                    <div className="field"><label htmlFor="as">Assign to</label>
                      <select id="as" value={t.assigneeId ?? ''} disabled={busy}
                        onChange={(e) => e.target.value && run(() => ticketApi.assign(id, Number(e.target.value)), 'Ticket assigned.')}>
                        <option value="">Choose a person</option>
                        {(staff.data ?? []).map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}</select></div>
                  )}
                  <div className="field"><label htmlFor="ct">Team</label>
                    <select id="ct" value={cat || t.category} onChange={(e) => setCat(e.target.value as Category)}>
                      {CATEGORIES.map((c) => <option key={c} value={c}>{DEPT_LABEL[c]}</option>)}</select></div>
                  <div className="field"><label htmlFor="pr">Priority</label>
                    <select id="pr" value={pri || t.priority} onChange={(e) => setPri(e.target.value as Priority)}>
                      {PRIORITIES.map((c) => <option key={c} value={c}>{PRIORITY_LABEL[c]}</option>)}</select></div>
                  <button className="btn btn-line" disabled={busy}
                    onClick={() => run(() => ticketApi.reclassify(id, (cat || t.category) as Category, (pri || t.priority) as Priority), 'Routing corrected.')}>
                    Correct routing
                  </button>
                </section>
              )}
            </aside>
          </div>
        </>
      )}
    </>
  );
}
