import { useState, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { approvalApi } from '../api/endpoints';
import { ApiError } from '../api/client';
import { useAsync } from '../components/useAsync';
import { Empty, Notice, Panel, RiskMark, Status, fmtDate } from '../components/ui';
import { RISK_COLOR, flagText } from '../theme';

export default function ApprovalsPage() {
  const { data, loading, error, reload } = useAsync(() => approvalApi.list('PENDING'), []);
  const [comments, setComments] = useState<Record<number, string>>({});
  const [result, setResult] = useState<{ tone: 'good' | 'warn'; text: string } | null>(null);

  async function decide(id: number, approve: boolean) {
    setResult(null);
    try {
      await approvalApi.decide(id, approve, comments[id] ?? '');
      setResult({ tone: 'good', text: approve ? 'Reply sent to the requester.' : 'Reply rejected. The agent has been told.' });
      reload();
    } catch (e) {
      setResult({ tone: 'warn', text: e instanceof ApiError ? e.message : 'That did not go through. Try again.' });
    }
  }

  const n = data?.length ?? 0;
  return (
    <>
      <div className="hero">
        <h1>{data ? (n === 0 ? 'Nothing is waiting for you.' : `${n} ${n === 1 ? 'reply needs' : 'replies need'} your decision.`) : 'Approvals'}</h1>
        <p className="lede">The assistant holds back any drafted reply that might promise too much, blame the requester or include personal data.</p>
      </div>
      <Status loading={loading} error={error} />
      {result && <Notice tone={result.tone}>{result.text}</Notice>}
      {data?.length === 0 && <Empty title="All clear.">New replies that need a second pair of eyes will appear here.</Empty>}
      {(data ?? []).map((a) => (
        <Panel key={a.id} title={a.ticketTitle} aside={<RiskMark level={a.riskLevel} />}>
          <p className="muted" style={{ marginBottom: 12 }}>Ticket #{a.ticketId}, drafted {fmtDate(a.createdAt)}. <Link to={`/tickets/${a.ticketId}`}>Open the ticket</Link></p>
          <div className="letter" style={{ '--edge': RISK_COLOR[a.riskLevel] } as CSSProperties}>{a.responseBody}</div>
          {a.riskFlags.length > 0 && <p className="reply-note">Held because it {a.riskFlags.map(flagText).join(', and ')}.</p>}
          <div className="row" style={{ marginTop: 18 }}>
            <div className="grow"><label htmlFor={`c${a.id}`}>Note for the agent <span className="hint">(optional)</span></label>
              <input id={`c${a.id}`} value={comments[a.id] ?? ''} onChange={(e) => setComments({ ...comments, [a.id]: e.target.value })} /></div>
            <button className="btn" onClick={() => decide(a.id, true)}>Approve and send</button>
            <button className="btn btn-danger" onClick={() => decide(a.id, false)}>Reject reply</button>
          </div>
        </Panel>
      ))}
    </>
  );
}
