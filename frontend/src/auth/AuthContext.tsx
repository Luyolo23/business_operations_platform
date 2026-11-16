import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi } from '../api/endpoints';
import { setUnauthorizedHandler, tokenStore } from '../api/client';
import type { AuthResponse, Role, User } from '../api/types';

interface AuthState {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  isStaff: boolean;
  isManager: boolean;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  const logout = useCallback(() => { tokenStore.clear(); setUser(null); }, []);

  useEffect(() => {
    setUnauthorizedHandler(logout);
    if (!tokenStore.get()) { setLoading(false); return; }
    authApi.me().then(setUser).catch(() => tokenStore.clear()).finally(() => setLoading(false));
  }, [logout]);

  // The token lives in localStorage, which every tab shares. If another tab
  // signs in as (or out of) a different account, or this page is restored from
  // the back/forward cache, this tab keeps the old `user` while its requests
  // carry the new token. That made the UI offer actions the current token is
  // not allowed to perform, such as the requester's resolution buttons for an
  // agent. Re-read the identity whenever the token or page state can change.
  useEffect(() => {
    let cancelled = false;

    const sync = () => {
      if (!tokenStore.get()) {
        if (!cancelled) setUser(null);
        return;
      }
      authApi.me()
        .then((u) => { if (!cancelled) setUser(u); })
        .catch(() => {
          tokenStore.clear();
          if (!cancelled) setUser(null);
        });
    };

    const onStorage = (e: StorageEvent) => {
      if (e.key === null || e.key === tokenStore.key) sync();
    };
    const onVisible = () => {
      if (document.visibilityState === 'visible') sync();
    };
    const onPageShow = (e: PageTransitionEvent) => {
      if (e.persisted) sync();
    };

    window.addEventListener('storage', onStorage);
    window.addEventListener('pageshow', onPageShow);
    document.addEventListener('visibilitychange', onVisible);

    return () => {
      cancelled = true;
      window.removeEventListener('storage', onStorage);
      window.removeEventListener('pageshow', onPageShow);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, []);

  const accept = (r: AuthResponse) => { tokenStore.set(r.token); setUser(r.user); };

  const value = useMemo<AuthState>(() => ({
    user, loading, logout,
    login: async (email, password) => accept(await authApi.login(email, password)),
    register: async (n, e, p) => accept(await authApi.register(n, e, p)),
    isStaff: !!user && user.role !== 'EMPLOYEE',
    isManager: !!user && (user.role === 'MANAGER' || user.role === 'ADMIN'),
    isAdmin: !!user && user.role === 'ADMIN',
  }), [user, loading, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}

export type { Role };
