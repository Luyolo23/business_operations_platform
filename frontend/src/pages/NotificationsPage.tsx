import { useState } from 'react';
import { Link } from 'react-router-dom';
import { notificationApi } from '../api/endpoints';
import { useAsync } from '../components/useAsync';
import { notifyNotificationsChanged } from '../components/events';
import { Empty, Status } from '../components/ui';
import { timeAgo } from '../theme';

export default function NotificationsPage() {
  const { data, loading, error, reload } = useAsync(() => notificationApi.list(), []);
  const [marking, setMarking] = useState<number | null>(null);
  const unread = (data ?? []).filter((n) => !n.read).length;

  function markRead(id: number) {
    setMarking(id);
    notificationApi.read(id)
      .then(() => {
        reload();
        notifyNotificationsChanged();   // keep the header badge in step
      })
      .catch(() => { /* the list still shows it as unread, so the user can retry */ })
      .finally(() => setMarking(null));
  }

  return (
    <>
      <div className="hero">
        <h1>{unread > 0 ? `${unread} new ${unread === 1 ? 'update' : 'updates'}.` : 'You’re all caught up.'}</h1>
      </div>
      <Status loading={loading} error={error} />
      {data?.length === 0 && <Empty title="No updates yet.">When a request moves, you’ll see it here.</Empty>}
      <ul className="list">
        {(data ?? []).map((n) => (
          <li key={n.id}>
            <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div className="grow" style={{ maxWidth: '70ch' }}>
                <strong>{!n.read && <span className="unread-dot" aria-label="Unread" />}{n.ticketId ? <Link to={`/tickets/${n.ticketId}`}>{n.subject}</Link> : n.subject}</strong>
                <p className="prose" style={{ fontSize: 16, marginTop: 4 }}>{n.message.length > 220 ? `${n.message.slice(0, 220)}…` : n.message}</p>
              </div>
              <div className="row" style={{ alignItems: 'center' }}>
                <span className="muted">{timeAgo(n.createdAt)}</span>
                {!n.read && (
                  <button className="btn btn-line" disabled={marking === n.id} onClick={() => markRead(n.id)}>
                    {marking === n.id ? 'Marking…' : 'Mark as read'}
                  </button>
                )}
              </div>
            </div>
          </li>
        ))}
      </ul>
    </>
  );
}
