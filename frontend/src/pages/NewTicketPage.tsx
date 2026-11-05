import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ticketApi } from '../api/endpoints';
import { ApiError } from '../api/client';
import type { Ticket } from '../api/types';
import { Notice, Panel } from '../components/ui';
import { DEPT_LABEL, DEPT_VAR, PRIORITY_LABEL } from '../theme';

export default function NewTicketPage() {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [created, setCreated] = useState<Ticket | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setCreated(await ticketApi.create(title, description));
      setTitle('');
      setDescription('');
    } catch (err) {
      const fields = err instanceof ApiError ? Object.values(err.fieldErrors ?? {}).join(', ') : '';
      setError(fields || (err instanceof Error ? err.message : 'The request was not sent. Try again.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div className="hero">
        <h1>What do you need help with?</h1>
        <p className="lede">Write it the way you’d explain it to a colleague. The assistant picks the team and priority, and you can see where it went straight away.</p>
      </div>

      <div className="split">
        <form onSubmit={submit}>
          <div className="field"><label htmlFor="t">Short title</label>
            <input id="t" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} required placeholder="For example: VPN drops every few minutes" /></div>
          <div className="field"><label htmlFor="d">What’s happening?</label>
            <textarea id="d" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={5000} required
              placeholder="Include what you tried, and anything that makes it urgent." /></div>
          {error && <p className="notice notice-warn" role="alert">{error}</p>}
          <button className="btn" disabled={busy}>{busy ? 'Sending…' : 'Send request'}</button>
        </form>

        <aside className="rail" aria-live="polite">
          {created ? (
            <Panel title="Request sent" tight>
              <Notice tone="good">
                Sent to <strong style={{ color: DEPT_VAR[created.category] }}>{DEPT_LABEL[created.category]}</strong> as {PRIORITY_LABEL[created.priority].toLowerCase()} priority.
                {created.assigneeName ? ` ${created.assigneeName} has it now.` : ' It will be assigned shortly.'}
              </Notice>
              <p className="muted" style={{ marginBottom: 14 }}>
                The assistant is {created.classificationConfidence != null ? Math.round(created.classificationConfidence * 100) : '—'}% sure
                about this routing. If it looks wrong, the team can correct it.
              </p>
              <Link className="btn btn-line" to={`/tickets/${created.id}`}>View request</Link>
            </Panel>
          ) : (
            <p className="muted">Urgent outages and blocked work are flagged to managers automatically.</p>
          )}
        </aside>
      </div>
    </>
  );
}
