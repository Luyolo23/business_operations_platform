import { useState, type CSSProperties } from 'react';
import { useParams } from 'react-router-dom';
import { ticketApi, userApi } from '../api/endpoints';
import { ApiError } from '../api/client';
import { CATEGORIES, PRIORITIES, TONES, type Category, type Priority, type Tone, type User } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useAsync } from '../components/useAsync';
import { Dept, Notice, Panel, PriorityMark, RiskMark, Status, StatusMark, fmtDate } from '../components/ui';
import { DEPT_LABEL, PRIORITY_LABEL, RISK_COLOR, flagText } from '../theme';

const TONE_LABEL: Record<Tone, string> = { FORMAL: 'Formal', FRIENDLY: 'Friendly', URGENT: 'Urgent' };

export default function TicketDetailPage() {
  const id = Number(useParams().id);
  const { user, isStaff, isManager } = useAuth();
  const ticket = useAsync(() => ticketApi.get(id), [id]);
  const responses = useAsync(() => ticketApi.responses(id), [id]);
  const staff = useAsync<User[]>(() => (isManager ? userApi.staff() : Promise.resolve([])), [isManager]);
  const [tone, setTone] = useState<Tone>('FORMAL');
  const [replyTone, setReplyTone] = useState<Tone>('FORMAL');
  const [replyBody, setReplyBody] = useState('');
  const [resolutionNote, setResolutionNote] = useState('');
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
  const isRequester = !!user && !!t && t.requesterId === user.id;
  // Replies are refused on resolved/closed tickets and while another reply waits for a manager.
  const canReply = !!t && t.status !== 'RESOLVED' && t.status !== 'CLOSED'
    && t.status !== 'AWAITING_APPROVAL';
  const steps = t ? [
    { name: 'Received', when: t.createdAt, done: true },
    { name: t.assigneeName ? `Assigned to ${t.assigneeName}` : 'Assigned', when: null as string | null, done: t.assigneeId != null },
    { name: 'First reply sent', when: t.firstResponseAt, done: t.firstResponseAt != null },
    { name: 'Resolved', when: t.resolvedAt, done: t.resolvedAt != null },
    { name: 'Requester confirmed it’s solved', when: t.requesterConfirmedAt, done: t.requesterConfirmedAt != null },
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

          {isRequester && !t.requesterConfirmedAt
            && (t.status === 'RESOLVED' || t.status === 'CLOSED') && (
            <Panel title="Did this solve your problem?">
              <p className="muted" style={{ marginBottom: 16 }}>
                The team marked this request as resolved. You have the final say — confirm that the
                problem is fixed, or send it back to the team if it is not.
              </p>
              <div className="field">
                <label htmlFor="resolution-note">Anything to add? <span className="hint">(optional)</span></label>
                <textarea id="resolution-note" value={resolutionNote} maxLength={2000}
                  placeholder="For example: working now, thanks. Or: it broke again this morning."
                  onChange={(e) => setResolutionNote(e.target.value)} />
              </div>
              <div className="row">
                <button className="btn" disabled={busy}
                  onClick={() => run(() => ticketApi.confirmResolution(id, true, resolutionNote),
                    'Thanks — your confirmation closed the request.')}>
                  Yes, it’s solved
                </button>
                <button className="btn btn-line btn-danger" disabled={busy}
                  style={{ color: '#fff' }}
                  onClick={() => run(() => ticketApi.confirmResolution(id, false, resolutionNote),
                    'Reopened — the team has been notified.')}>
                  No, still a problem
                </button>
              </div>
            </Panel>
          )}

          {t.requesterConfirmedAt && (
            <Notice tone="good">
              The requester confirmed this problem was solved.
              {t.requesterFeedback ? ` They said: “${t.requesterFeedback}”` : ''}
            </Notice>
          )}

          <div className="split">
            <div>
              <p className="prose">{t.description}</p>

              {isStaff && canReply && (
                <div style={{ marginTop: 40 }}>
                  <Panel title="Write a reply yourself">
                    <p className="muted" style={{ marginBottom: 16 }}>
                      Prefer your own words? Write the reply here. It still goes through the same risk
                      check: a clean reply is sent straight away, while a risky one is held for a manager.
                    </p>
                    <div className="field">
                      <label htmlFor="reply-tone">Tone</label>
                      <select id="reply-tone" value={replyTone}
                        onChange={(e) => setReplyTone(e.target.value as Tone)}>
                        {TONES.map((x) => <option key={x} value={x}>{TONE_LABEL[x]}</option>)}
                      </select>
                    </div>
                    <div className="field">
                      <label htmlFor="reply-body">Your reply</label>
                      <textarea id="reply-body" value={replyBody} maxLength={10000}
                        placeholder="Write what the requester needs to hear…"
                        onChange={(e) => setReplyBody(e.target.value)} />
                    </div>
                    <button className="btn" disabled={busy || replyBody.trim().length === 0}
                      onClick={() => run(async () => {
                        await ticketApi.reply(id, replyTone, replyBody);
                        setReplyBody('');
                      }, 'Reply submitted — it is sent unless the risk check holds it for approval.')}>
                      Send written reply
                    </button>
                  </Panel>
                </div>
              )}

              <div style={{ marginTop: 40 }}>
                <Panel title="Replies" aside={isStaff && canReply && (
                  <div className="row">
                    <div><label htmlFor="tone">Tone</label>
                      <select id="tone" value={tone} onChange={(e) => setTone(e.target.value as Tone)}>
                        {TONES.map((x) => <option key={x} value={x}>{TONE_LABEL[x]}</option>)}</select></div>
                    <button className="btn" disabled={busy} onClick={() => run(() => ticketApi.generate(id, tone), 'Reply drafted.')}>Draft reply</button>
                  </div>
                )}>
                  <Status loading={responses.loading} error={responses.error} />
                  {isStaff && !canReply && (
                    <p className="muted" style={{ marginBottom: 12 }}>
                      {t.status === 'AWAITING_APPROVAL'
                        ? 'A reply is waiting for a manager’s decision. You can reply again once it has been approved or rejected.'
                        : t.status === 'CLOSED'
                          ? 'This request was closed by the requester; only they can reopen it.'
                          : 'Reopen this request before sending another reply.'}
                    </p>
                  )}
                  {responses.data?.length === 0 && (
                    <p className="muted">{isStaff ? 'No replies yet. Draft one and the assistant will check it before it is sent.' : 'No replies yet. You’ll get an update as soon as the team responds.'}</p>
                  )}
                  {(responses.data ?? []).map((r) => (
                    <div className="reply" key={r.id}>
                      <div className="reply-meta">
                        {isStaff && r.riskLevel && <RiskMark level={r.riskLevel} />}
                        <span className="tag">{!isStaff ? 'Sent' : r.status === 'APPROVED' ? 'Sent' : r.status === 'REJECTED' ? 'Rejected' : 'Waiting for approval'}</span>
                        <span className="tag">{TONE_LABEL[r.tone]} tone</span>
                        {r.generatedBy.startsWith('manual') && <span className="tag">Written by staff</span>}
                        <span className="tag muted">{fmtDate(r.createdAt)}</span>
                      </div>
                      <div className="letter" style={{ '--edge': r.riskLevel ? RISK_COLOR[r.riskLevel] : undefined } as CSSProperties}>{r.body}</div>
                      {isStaff && r.riskFlags.length > 0 && <p className="reply-note">Held for review because it {r.riskFlags.map(flagText).join(', and ')}.</p>}
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
                  <div><dt>Requester confirmed</dt><dd>{t.requesterConfirmedAt ? `Yes, ${fmtDate(t.requesterConfirmedAt)}` : 'Not yet'}</dd></div>
                  {t.requesterFeedback && <div><dt>Requester note</dt><dd>{t.requesterFeedback}</dd></div>}
                </dl>
              </section>

              {isStaff && (
                <section>
                  <h2>Update this ticket</h2>
                  {t.status === 'CLOSED' ? (
                    <p className="muted">The requester has closed this request. Only they can reopen it.</p>
                  ) : (
                    <>
                      {t.status === 'RESOLVED' ? (
                        <button className="btn btn-line" disabled={busy}
                          onClick={() => run(() => ticketApi.setStatus(id, 'IN_PROGRESS'), 'Ticket reopened.')}>
                          Reopen request
                        </button>
                      ) : t.status === 'IN_PROGRESS' && t.firstResponseAt ? (
                        <button className="btn btn-line" disabled={busy}
                          onClick={() => run(() => ticketApi.setStatus(id, 'RESOLVED'), 'Ticket marked resolved.')}>
                          Mark resolved
                        </button>
                      ) : (
                        <p className="muted">
                          {t.status === 'AWAITING_APPROVAL'
                            ? 'A manager is reviewing the held reply. The request moves forward after their decision.'
                            : 'Send a reply to start work. A request can be resolved once the requester has received a reply.'}
                        </p>
                      )}
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
                    </>
                  )}
                </section>
              )}
            </aside>
          </div>
        </>
      )}
    </>
  );
}
