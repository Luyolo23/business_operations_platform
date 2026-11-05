import { api, qs } from './client';
import type * as T from './types';

export const authApi = {
  login: (email: string, password: string) => api<T.AuthResponse>('/api/auth/login', 'POST', { email, password }),
  register: (fullName: string, email: string, password: string) =>
    api<T.AuthResponse>('/api/auth/register', 'POST', { fullName, email, password }),
  me: () => api<T.User>('/api/auth/me'),
};

export const ticketApi = {
  list: (p: { category?: string; status?: string; priority?: string; page?: number; size?: number }) =>
    api<T.PageResponse<T.Ticket>>(`/api/tickets${qs(p)}`),
  get: (id: number) => api<T.Ticket>(`/api/tickets/${id}`),
  create: (title: string, description: string) => api<T.Ticket>('/api/tickets', 'POST', { title, description }),
  setStatus: (id: number, status: T.TicketStatus) => api<T.Ticket>(`/api/tickets/${id}/status`, 'PATCH', { status }),
  reclassify: (id: number, category: T.Category, priority: T.Priority) =>
    api<T.Ticket>(`/api/tickets/${id}/classification`, 'PATCH', { category, priority }),
  assign: (id: number, userId: number) => api<T.Ticket>(`/api/tickets/${id}/assignee`, 'PATCH', { userId }),
  responses: (id: number) => api<T.TicketResponse[]>(`/api/tickets/${id}/responses`),
  generate: (id: number, tone: T.Tone) => api<T.TicketResponse>(`/api/tickets/${id}/responses`, 'POST', { tone }),
};

export const approvalApi = {
  list: (status = 'PENDING') => api<T.Approval[]>(`/api/approvals${qs({ status })}`),
  decide: (id: number, approve: boolean, comment: string) =>
    api<T.Approval>(`/api/approvals/${id}/decision`, 'POST', { approve, comment }),
};

export const notificationApi = {
  list: () => api<T.AppNotification[]>('/api/notifications'),
  read: (id: number) => api<T.AppNotification>(`/api/notifications/${id}/read`, 'POST'),
};

export const analyticsApi = {
  summary: (days: number, category?: string) => api<T.Summary>(`/api/analytics/summary${qs({ days, category })}`),
  insights: (days: number, category?: string) => api<T.Insights>(`/api/analytics/insights${qs({ days, category })}`),
};

export const forecastApi = { get: (horizonDays = 7) => api<T.Forecast>(`/api/forecast${qs({ horizonDays })}`) };

export const reportApi = {
  weekly: (department?: string) => api<T.WeeklyReport>(`/api/reports/weekly${qs({ department })}`),
  pdfPath: (department?: string) => `/api/reports/weekly/pdf${qs({ department })}`,
};

export const complianceApi = {
  report: (days = 30) => api<T.RiskReport>(`/api/compliance/report${qs({ days })}`),
  audit: (page = 0, size = 20) => api<T.PageResponse<T.AuditLog>>(`/api/compliance/audit${qs({ page, size })}`),
};

export const userApi = { staff: () => api<T.User[]>('/api/users/staff') };
