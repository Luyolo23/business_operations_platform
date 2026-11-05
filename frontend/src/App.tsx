import type { ReactElement } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import TicketsPage from './pages/TicketsPage';
import NewTicketPage from './pages/NewTicketPage';
import TicketDetailPage from './pages/TicketDetailPage';
import NotificationsPage from './pages/NotificationsPage';
import DashboardPage from './pages/DashboardPage';
import ForecastPage from './pages/ForecastPage';
import ReportsPage from './pages/ReportsPage';
import ApprovalsPage from './pages/ApprovalsPage';
import CompliancePage from './pages/CompliancePage';

function RequireAuth({ children, managerOnly }: { children: ReactElement; managerOnly?: boolean }) {
  const { user, loading, isManager } = useAuth();
  if (loading) return <p className="muted pad">Loading…</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (managerOnly && !isManager) return <Navigate to="/tickets" replace />;
  return children;
}

export default function App() {
  const { user } = useAuth();
  return (
    <Routes>
      <Route path="/login" element={user ? <Navigate to="/tickets" replace /> : <LoginPage />} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route path="/tickets" element={<TicketsPage />} />
        <Route path="/tickets/new" element={<NewTicketPage />} />
        <Route path="/tickets/:id" element={<TicketDetailPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/dashboard" element={<RequireAuth managerOnly><DashboardPage /></RequireAuth>} />
        <Route path="/forecast" element={<RequireAuth managerOnly><ForecastPage /></RequireAuth>} />
        <Route path="/reports" element={<RequireAuth managerOnly><ReportsPage /></RequireAuth>} />
        <Route path="/approvals" element={<RequireAuth managerOnly><ApprovalsPage /></RequireAuth>} />
        <Route path="/compliance" element={<RequireAuth managerOnly><CompliancePage /></RequireAuth>} />
      </Route>
      <Route path="*" element={<Navigate to="/tickets" replace />} />
    </Routes>
  );
}
