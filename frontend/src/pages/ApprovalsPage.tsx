import { useRef, useState, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { approvalApi } from '../api/endpoints';
import { ApiError } from '../api/client';
import { useAsync } from '../components/useAsync';
import { Empty, Notice, Panel, RiskMark, Status, fmtDate } from '../components/ui';
import { RISK_COLOR, flagText } from '../theme';
import type { ApprovalStatus } from '../api/types';

const TABS: { value: ApprovalStatus; label: string }[] = [
  { value: 'PENDING', label: 'Waiting for you' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
];

const LEDE: Record<ApprovalStatus, string> = {
  PENDING: 'The assistant holds back any drafted reply that might promise too much, blame the requester or include personal data.',
  APPROVED: 'Replies a manager approved. Approved replies have been delivered to the requester.',
  REJECTED: 'Replies a manager sent back to the agent for a rewrite. They were not delivered.',
};

const EMPTY: Record<ApprovalStatus, { title: string; body: string }> = {
  PENDING: { title: 'All clear.', body: 'New replies that need a second pair of eyes will appear here.' },
  APPROVED: { title: 'Nothing approved yet.', body: 'Replies a manager approves will be listed here.' },
  REJECTED: { title: 'Nothing rejected yet.', body: 'Replies a manager sends back to an agent will be listed here.' },
};

export default function ApprovalsPage() {
  const [tab, setTab] = useState<ApprovalStatus>('PENDING');
  const { data, loading, error, reload } = useAsync(() => approvalApi.list(tab), [tab]);
  const [comments, setComments] = useState<Record<number, string>>({});
  const [result, setResult] = useState<{ tone: 'good' | 'warn'; text: string } | null>(null);
  const [deciding, setDeciding] = useState<number | null>(null);
  const inFlight = useRef(false);

  async function decide(id: number, approve: boolean) {
    // A fast double click (or approving and rejecting the same card) would
    // otherwise send two decisions and race the version check.
    if (inFlight.current) return;
    inFlight.current = true;
    setDeciding(id);
    setResult(null);
    try {
      await approvalApi.decide(id, approve, comments[id] ?? '');
      setResult({ tone: 'good', text: approve ? 'Reply sent to the requester.' : 'Reply rejected. The agent has been told.' });
      reload();
    } catch (e) {
      setResult({ tone: 'warn', text: e instanceof ApiError ? e.message : 'That did not go through. Try again.' });
    } finally {
      inFlight.current = false;
      setDeciding(null);
    }
  }

  const n = data?.length ?? 0;
  const pending = tab === 'PENDING';
  const heading = !data
    ? 'Approvals'
    : pending
      ? (n === 0 ? 'Nothing is waiting for you.' : `${n} ${n === 1 ? 'reply needs' : 'replies need'} your decision.`)
      : `${n} ${n === 1 ? 'reply' : 'replies'} ${tab === 'APPROVED' ? 'approved' : 'rejected'}.`;

  return (
    <>
      <div className="hero">
        <h1>{heading}</h1>
        <p className="lede">{LEDE[tab]}</p>
      </div>

      <div className="tabs" aria-label="Approval status">
        {TABS.map((t) => (
          <button
            key={t.value}
            className={tab === t.value ? 'btn' : 'btn btn-line'}
            aria-pressed={tab === t.value}
            onClick={() => { setTab(t.value); setResult(null); }}
          >
            {t.label}
          </button>
        ))}
      </div>

      <Status loading={loading} error={error} />
      {result && <Notice tone={result.tone}>{result.text}</Notice>}
      {data?.length === 0 && <Empty title={EMPTY[tab].title}>{EMPTY[tab].body}</Empty>}

      {(data ?? []).map((a) => (
        <Panel key={a.id} title={a.ticketTitle} aside={<RiskMark level={a.riskLevel} />}>
          <p className="muted" style={{ marginBottom: 12 }}>
            Ticket #{a.ticketId}, drafted {fmtDate(a.createdAt)}. <Link to={`/tickets/${a.ticketId}`}>Open the ticket</Link>
          </p>
          <div className="letter" style={{ '--edge': RISK_COLOR[a.riskLevel] } as CSSProperties}>{a.responseBody}</div>
          {a.riskFlags.length > 0 && <p className="reply-note">Held because it {a.riskFlags.map(flagText).join(', and ')}.</p>}

          {pending ? (
            <div className="row" style={{ marginTop: 18 }}>
              <div className="grow">
                <label htmlFor={`c${a.id}`}>Note for the agent <span className="hint">(optional)</span></label>
                <input id={`c${a.id}`} value={comments[a.id] ?? ''} onChange={(e) => setComments({ ...comments, [a.id]: e.target.value })} />
              </div>
              <button className="btn" disabled={deciding !== null} onClick={() => decide(a.id, true)}>
                {deciding === a.id ? 'Sending…' : 'Approve and send'}
              </button>
              <button className="btn btn-danger" disabled={deciding !== null} onClick={() => decide(a.id, false)}>
                Reject reply
              </button>
            </div>
          ) : (
            <p className="reply-note">
              {a.status === 'APPROVED' ? 'Approved' : 'Rejected'} by {a.decidedBy ?? 'a manager'} on {fmtDate(a.decidedAt)}.
              {a.decisionComment ? ` Note: ${a.decisionComment}` : ''}
            </p>
          )}
        </Panel>
      ))}
    </>
  );
}
