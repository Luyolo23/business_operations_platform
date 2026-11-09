import { useState, type FormEvent } from 'react';
import { userApi } from '../api/endpoints';
import { ApiError } from '../api/client';
import { CATEGORIES, type Category, type Role, type User } from '../api/types';
import { useAsync } from '../components/useAsync';
import { Empty, Notice, Panel, Status } from '../components/ui';
import { DEPT_LABEL, ROLE_LABEL, ROLES } from '../theme';

/** ADMIN-only: create staff accounts and switch access on or off. */
export default function EmployeesPage() {
  const users = useAsync<User[]>(() => userApi.list(), []);
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>('AGENT');
  const [department, setDepartment] = useState<Category | ''>('IT');
  const [busy, setBusy] = useState(false);
  const [actingOn, setActingOn] = useState<number | null>(null);
  const [result, setResult] = useState<{ tone: 'good' | 'warn'; text: string } | null>(null);

  async function create(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setResult(null);
    try {
      await userApi.create({
        fullName,
        email,
        password,
        role,
        department: role === 'AGENT' ? (department || null) : null,
      });
      setResult({ tone: 'good', text: `${fullName} can now sign in as ${ROLE_LABEL[role].toLowerCase()}.` });
      setFullName('');
      setEmail('');
      setPassword('');
      users.reload();
    } catch (err) {
      const fields = err instanceof ApiError ? Object.values(err.fieldErrors ?? {}).join(', ') : '';
      setResult({ tone: 'warn', text: fields || (err instanceof Error ? err.message : 'The account was not created.') });
    } finally {
      setBusy(false);
    }
  }

  async function toggle(u: User) {
    setActingOn(u.id);
    setResult(null);
    try {
      await (u.active ? userApi.deactivate(u.id) : userApi.activate(u.id));
      setResult({
        tone: 'good',
        text: u.active ? `${u.fullName} can no longer sign in.` : `${u.fullName} can sign in again.`,
      });
      users.reload();
    } catch (err) {
      setResult({ tone: 'warn', text: err instanceof Error ? err.message : 'That change did not go through.' });
    } finally {
      setActingOn(null);
    }
  }

  return (
    <>
      <div className="hero">
        <h1>People</h1>
        <p className="lede">
          Add the agents, managers and admins who handle requests, and switch access off when someone leaves.
        </p>
      </div>

      {result && <Notice tone={result.tone}>{result.text}</Notice>}

      <div className="grid-2">
        <Panel title="Add a person">
          <form onSubmit={create}>
            <div className="field">
              <label htmlFor="emp-name">Full name</label>
              <input id="emp-name" value={fullName} maxLength={120} required
                onChange={(e) => setFullName(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="emp-email">Email</label>
              <input id="emp-email" type="email" value={email} required
                onChange={(e) => setEmail(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="emp-password">Temporary password</label>
              <input id="emp-password" type="password" value={password} minLength={8} required
                onChange={(e) => setPassword(e.target.value)} />
              <small className="muted">At least 8 characters. Share it with them securely.</small>
            </div>
            <div className="row">
              <div className="field">
                <label htmlFor="emp-role">Role</label>
                <select id="emp-role" value={role} onChange={(e) => setRole(e.target.value as Role)}>
                  {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}
                </select>
              </div>
              <div className="field grow">
                <label htmlFor="emp-dept">Team</label>
                <select id="emp-dept" value={role === 'AGENT' ? department : ''} disabled={role !== 'AGENT'}
                  onChange={(e) => setDepartment(e.target.value as Category)}>
                  {role !== 'AGENT' && <option value="">Not assigned to a team</option>}
                  {CATEGORIES.map((c) => <option key={c} value={c}>{DEPT_LABEL[c]}</option>)}
                </select>
                {role !== 'AGENT' && <small className="muted">Only agents are tied to a team.</small>}
              </div>
            </div>
            <button className="btn" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
          </form>
        </Panel>

        <Panel title="Who has access">
          <Status loading={users.loading} error={users.error} />
          {users.data?.length === 0 && <Empty title="No accounts yet">Create the first one on the left.</Empty>}
          {users.data && users.data.length > 0 && (
            <table className="plain">
              <thead>
                <tr><th>Name</th><th>Role</th><th>Status</th><th /></tr>
              </thead>
              <tbody>
                {users.data.map((u) => (
                  <tr key={u.id}>
                    <td>
                      <strong>{u.fullName}</strong>
                      <div className="muted">{u.email}</div>
                      {u.department && <div className="muted">{DEPT_LABEL[u.department]} team</div>}
                    </td>
                    <td>{ROLE_LABEL[u.role]}</td>
                    <td>
                      <span className="tag">
                        <i className="dot" style={{ background: u.active ? 'var(--fin)' : 'var(--ink-3)' }} />
                        {u.active ? 'Active' : 'Switched off'}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <button className="btn btn-line" disabled={actingOn === u.id}
                        onClick={() => toggle(u)}>
                        {actingOn === u.id ? 'Working…' : u.active ? 'Switch off' : 'Switch on'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <p className="muted" style={{ marginTop: 16 }}>
            Switching someone off blocks sign-in immediately but keeps their ticket history.
            The last active admin cannot be switched off.
          </p>
        </Panel>
      </div>
    </>
  );
}
