import { useEffect, useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { notificationApi } from '../api/endpoints';

export default function Layout() {
  const { user, logout, isManager } = useAuth();
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    const load = () => notificationApi.list().then((l) => setUnread(l.filter((n) => !n.read).length)).catch(() => {});
    load();
    const id = setInterval(load, 30000);
    return () => clearInterval(id);
  }, []);

  const initials = (user?.fullName ?? '?').split(' ').map((p) => p[0]).slice(0, 2).join('');

  return (
    <>
      <a className="skip" href="#main">Skip to content</a>
      <header className="topbar">
        <NavLink to="/tickets" className="brand" aria-label="Ops Assistant home">
          <span className="mark" aria-hidden="true"><i /><i /><i /><i /></span>
          Ops Assistant
        </NavLink>
        <nav className="nav" aria-label="Main">
          <NavLink to="/tickets" end>Requests</NavLink>
          <NavLink to="/tickets/new">New request</NavLink>
          <NavLink to="/notifications">Updates{unread > 0 && <b className="count">{unread}</b>}</NavLink>
          {isManager && (
            <>
              <span className="nav-gap" aria-hidden="true" />
              <NavLink to="/dashboard">Overview</NavLink>
              <NavLink to="/forecast">Forecast</NavLink>
              <NavLink to="/reports">Weekly report</NavLink>
              <NavLink to="/approvals">Approvals</NavLink>
              <NavLink to="/compliance">Compliance</NavLink>
            </>
          )}
        </nav>
        <div className="who">
          <span className="avatar" aria-hidden="true">{initials}</span>
          <span className="who-text"><strong>{user?.fullName}</strong><small>{user?.role.toLowerCase()}{user?.department ? ` in ${user.department}` : ''}</small></span>
          <button className="btn-quiet" onClick={logout}>Sign out</button>
        </div>
      </header>
      <main id="main" className="page"><Outlet /></main>
    </>
  );
}
