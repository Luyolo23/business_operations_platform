import { Suspense, lazy, type ReactElement } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import Layout from './components/Layout';

// Each page is its own chunk. The login page also pulls in the 3D workflow scene,
// so loading it lazily keeps that cost off every other route.
const LoginPage = lazy(() => import('./pages/LoginPage'));
const TicketsPage = lazy(() => import('./pages/TicketsPage'));
const NewTicketPage = lazy(() => import('./pages/NewTicketPage'));
const TicketDetailPage = lazy(() => import('./pages/TicketDetailPage'));
const NotificationsPage = lazy(() => import('./pages/NotificationsPage'));
const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const ForecastPage = lazy(() => import('./pages/ForecastPage'));
const ReportsPage = lazy(() => import('./pages/ReportsPage'));
const ApprovalsPage = lazy(() => import('./pages/ApprovalsPage'));
const CompliancePage = lazy(() => import('./pages/CompliancePage'));
const EmployeesPage = lazy(() => import('./pages/EmployeesPage'));

function RequireAuth({ children, managerOnly, adminOnly }: {
  children: ReactElement; managerOnly?: boolean; adminOnly?: boolean;
}) {
  const { user, loading, isManager, isAdmin } = useAuth();
  if (loading) return <p className="muted pad">Loading…</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (managerOnly && !isManager) return <Navigate to="/tickets" replace />;
  if (adminOnly && !isAdmin) return <Navigate to="/tickets" replace />;
  return children;
}

export default function App() {
  const { user } = useAuth();
  return (
    <Suspense fallback={<p className="muted pad">Loading…</p>}>
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
          <Route path="/employees" element={<RequireAuth adminOnly><EmployeesPage /></RequireAuth>} />
        </Route>
        <Route path="*" element={<Navigate to="/tickets" replace />} />
      </Routes>
    </Suspense>
  );
}
