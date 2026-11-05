const TOKEN_KEY = 'ops_token';

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

export class ApiError extends Error {
  status: number;
  fieldErrors?: Record<string, string>;
  constructor(status: number, message: string, fieldErrors?: Record<string, string>) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(fn: () => void) { onUnauthorized = fn; }

async function send(path: string, method: string, body?: unknown): Promise<Response> {
  const headers: Record<string, string> = {};
  const token = tokenStore.get();
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  if (!res.ok) {
    let message = res.statusText;
    let fieldErrors: Record<string, string> | undefined;
    try {
      const err = await res.json();
      message = err.message ?? message;
      fieldErrors = err.fieldErrors ?? undefined;
    } catch { /* empty body */ }
    if (res.status === 401 && token) onUnauthorized?.();
    throw new ApiError(res.status, message || `HTTP ${res.status}`, fieldErrors);
  }
  return res;
}

export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const res = await send(path, method, body);
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** For file downloads (PDF): a plain <a href> cannot send the JWT header, so fetch + blob. */
export async function downloadFile(path: string, filename: string): Promise<void> {
  const res = await send(path, 'GET');
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export function qs(params: Record<string, string | number | undefined | null>): string {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') p.set(k, String(v)); });
  const s = p.toString();
  return s ? `?${s}` : '';
}
