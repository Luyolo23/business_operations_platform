import { useState, type ChangeEvent, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import { ticketApi } from '../api/endpoints';
import { CATEGORIES, PRIORITIES, STATUSES } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useAsync } from '../components/useAsync';
import { Dept, Empty, PriorityMark, Status, StatusMark } from '../components/ui';
import { DEPT_LABEL, DEPT_VAR, OPEN_STATUSES, STATUS_LABEL, PRIORITY_LABEL, firstName, greeting, timeAgo } from '../theme';

export default function TicketsPage() {
  const { user, isStaff } = useAuth();
  const [category, setCategory] = useState('');
  const [status, setStatus] = useState('');
  const [priority, setPriority] = useState('');
  const [page, setPage] = useState(0);

  // Latest 100 tickets, used only for the sentence at the top of the page.
  const overview = useAsync(() => ticketApi.list({ size: 100 }), []);
  const list = useAsync(() => ticketApi.list({ category, status, priority, page, size: 12 }),
    [category, status, priority, page]);

  const pick = (set: (v: string) => void) => (e: ChangeEvent<HTMLSelectElement>) => { set(e.target.value); setPage(0); };

  const all = overview.data?.content ?? [];
  const open = all.filter((t) => OPEN_STATUSES.includes(t.status));
  const urgent = open.filter((t) => t.priority === 'URGENT').length;
  const waiting = open.filter((t) => t.status === 'AWAITING_APPROVAL').length;

  let headline = `${greeting()}, ${firstName(user?.fullName)}.`;
  let lede = '';
  if (overview.data) {
    if (open.length === 0) {
      lede = isStaff ? 'Your queue is clear.' : 'Nothing is waiting on you right now.';
    } else {
      headline = isStaff
        ? `${open.length} ${open.length === 1 ? 'request is' : 'requests are'} open in your queue.`
        : `You have ${open.length} open ${open.length === 1 ? 'request' : 'requests'}.`;
      const parts: string[] = [];
      if (urgent > 0) parts.push(`${urgent} ${urgent === 1 ? 'is' : 'are'} urgent`);
      if (waiting > 0) parts.push(`${waiting} ${waiting === 1 ? 'is' : 'are'} waiting for a manager’s approval`);
      lede = parts.length ? `${parts.join(' and ')}.` : 'Everything is moving.';
    }
  }

  return (
    <>
      <div className="hero">
        <h1>{headline}</h1>
        {lede && <p className="lede">{lede}</p>}
      </div>

      <div className="filters">
        <select aria-label="Team" value={category} onChange={pick(setCategory)}>
          <option value="">All teams</option>{CATEGORIES.map((c) => <option key={c} value={c}>{DEPT_LABEL[c]}</option>)}</select>
        <select aria-label="Status" value={status} onChange={pick(setStatus)}>
          <option value="">Any status</option>{STATUSES.map((c) => <option key={c} value={c}>{STATUS_LABEL[c]}</option>)}</select>
        <select aria-label="Priority" value={priority} onChange={pick(setPriority)}>
          <option value="">Any priority</option>{PRIORITIES.map((c) => <option key={c} value={c}>{PRIORITY_LABEL[c]}</option>)}</select>
      </div>

      <Status loading={list.loading} error={list.error} />
      {list.data && list.data.content.length === 0 && (
        <Empty title="No requests match.">
          Try clearing a filter, or <Link to="/tickets/new">raise a new request</Link> and the assistant will route it to the right team.
        </Empty>
      )}
      {list.data && list.data.content.length > 0 && (
        <>
          <ul className="queue">
            {list.data.content.map((t) => (
              <li key={t.id}>
                <Link to={`/tickets/${t.id}`} style={{ '--dept': DEPT_VAR[t.category] } as CSSProperties}>
                  <h3>{t.title}{t.escalated && <span className="tag tag-hot" style={{ marginLeft: 10 }}>Past its deadline</span>}</h3>
                  <p className="snippet">{t.description}</p>
                  <div className="side"><StatusMark status={t.status} /><span className="muted">{timeAgo(t.createdAt)}</span></div>
                  <div className="meta">
                    <Dept value={t.category} />
                    <PriorityMark priority={t.priority} />
                    <span className="tag">{t.assigneeName ? `With ${t.assigneeName}` : 'Not assigned yet'}</span>
                    {isStaff && <span className="tag">From {t.requesterName}</span>}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
          <div className="pager">
            <span className="count-chip">{list.data.totalElements} {list.data.totalElements === 1 ? 'request' : 'requests'}, page {list.data.page + 1} of {Math.max(list.data.totalPages, 1)}</span>
            <button className="btn btn-line" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
            <button className="btn btn-line" disabled={page + 1 >= list.data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
          </div>
        </>
      )}
    </>
  );
}
