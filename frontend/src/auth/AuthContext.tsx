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

  const accept = (r: AuthResponse) => { tokenStore.set(r.token); setUser(r.user); };

  const value = useMemo<AuthState>(() => ({
    user, loading, logout,
    login: async (email, password) => accept(await authApi.login(email, password)),
    register: async (n, e, p) => accept(await authApi.register(n, e, p)),
    isStaff: !!user && user.role !== 'EMPLOYEE',
    isManager: !!user && (user.role === 'MANAGER' || user.role === 'ADMIN'),
  }), [user, loading, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}

export type { Role };
