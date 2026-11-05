import { useState, type FormEvent } from 'react';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';
import { CATEGORIES } from '../api/types';
import { DEPT_BLURB, DEPT_LABEL, DEPT_VAR } from '../theme';

const DEMO = [
  { label: 'an employee', email: 'employee@ops.local' },
  { label: 'an IT agent', email: 'agent.it@ops.local' },
  { label: 'a manager', email: 'manager@ops.local' },
];

export default function LoginPage() {
  const { login, register } = useAuth();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    await go(() => (mode === 'login' ? login(email, password) : register(fullName, email, password)));
  }

  async function go(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The server did not respond. Check that the backend is running on port 8080.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="signin">
      <section className="signin-art">
        <div>
          <h1>Tell us what you need. We’ll get it to the right people.</h1>
        </div>
        <ul className="teams">
          {CATEGORIES.map((c) => (
            <li key={c}>
              <i className="dot" style={{ background: DEPT_VAR[c] }} />
              <div><strong>{DEPT_LABEL[c]}</strong><span>{DEPT_BLURB[c]}</span></div>
            </li>
          ))}
        </ul>
      </section>

      <section className="signin-form">
        <h2>{mode === 'login' ? 'Sign in' : 'Create your account'}</h2>
        <form onSubmit={submit}>
          {mode === 'register' && (
            <div className="field"><label htmlFor="n">Full name</label><input id="n" value={fullName} onChange={(e) => setFullName(e.target.value)} required autoComplete="name" /></div>
          )}
          <div className="field"><label htmlFor="e">Work email</label><input id="e" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" /></div>
          <div className="field"><label htmlFor="p">Password {mode === 'register' && <span className="hint">(at least 8 characters)</span>}</label>
            <input id="p" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></div>
          {error && <p className="notice notice-warn" role="alert">{error}</p>}
          <button className="btn" disabled={busy}>{mode === 'login' ? 'Sign in' : 'Create account'}</button>
        </form>
        <p className="switch">
          {mode === 'login' ? 'New here? ' : 'Already have an account? '}
          <button onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(null); }}>
            {mode === 'login' ? 'Create an account' : 'Sign in'}
          </button>
        </p>
        <div className="try">
          <p>Demo build: sign in as</p>
          <div className="pills">
            {DEMO.map((d) => (
              <button key={d.email} className="pill-btn" aria-pressed="false" disabled={busy}
                onClick={() => go(() => login(d.email, 'Passw0rd!'))}>{d.label}</button>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
