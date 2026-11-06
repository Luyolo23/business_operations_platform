import { lazy, Suspense, useState, type FormEvent } from 'react';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api/client';

// The 3D scene is loaded on demand so the form is interactive immediately.
const WorkflowScene = lazy(() => import('../components/workflow/WorkflowScene'));

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
      <section className="signin-form">
        <h1>{mode === 'login' ? 'Welcome back' : 'Create your account'}</h1>
        <p className="signin-sub">
          {mode === 'login'
            ? 'Sign in to raise a request, follow it from first reply to resolution, and see what your team is working on.'
            : 'Raise requests to HR, IT, Finance or Operations and follow each one until it is resolved.'}
        </p>
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

      <section className="signin-viz" aria-label="How the platform works">
        <div className="viz-copy">
          <h2>Smarter requests. Faster resolutions.</h2>
          <p>Every request is read, sorted and sent to the right team in seconds. AI drafts the reply, a safety check reviews it, and a person approves anything risky.</p>
        </div>
        <Suspense fallback={<div className="viz-fallback" aria-hidden="true" />}>
          <WorkflowScene />
        </Suspense>
      </section>
    </div>
  );
}
