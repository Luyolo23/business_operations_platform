import type { Category, Priority, RiskLevel, Role, TicketStatus } from './api/types';

// One colour per department: colour tells you which team owns a request.
export const DEPT_HEX: Record<Category, string> = { HR: '#8E3B7A', IT: '#2D52D6', FINANCE: '#2F7D4F', OPERATIONS: '#C9860A' };
export const DEPT_VAR: Record<Category, string> = { HR: 'var(--hr)', IT: 'var(--it)', FINANCE: 'var(--fin)', OPERATIONS: 'var(--ops)' };
export const DEPT_LABEL: Record<Category, string> = { HR: 'HR', IT: 'IT', FINANCE: 'Finance', OPERATIONS: 'Operations' };
export const DEPT_BLURB: Record<Category, string> = {
  HR: 'Leave, payslips, contracts, onboarding',
  IT: 'Logins, laptops, VPN, software',
  FINANCE: 'Invoices, expenses, budgets, refunds',
  OPERATIONS: 'Suppliers, stock, facilities, bookings',
};

export const STATUS_LABEL: Record<TicketStatus, string> = {
  NEW: 'Received', ASSIGNED: 'Assigned', IN_PROGRESS: 'In progress',
  AWAITING_APPROVAL: 'Waiting for approval', RESOLVED: 'Resolved', CLOSED: 'Closed',
};
export const STATUS_COLOR: Record<TicketStatus, string> = {
  NEW: 'var(--ink-3)', ASSIGNED: 'var(--it)', IN_PROGRESS: 'var(--pine)',
  AWAITING_APPROVAL: 'var(--ops)', RESOLVED: 'var(--fin)', CLOSED: 'var(--ink-3)',
};
export const PRIORITY_LABEL: Record<Priority, string> = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', URGENT: 'Urgent' };
export const ROLE_LABEL: Record<Role, string> = {
  EMPLOYEE: 'Employee', AGENT: 'Agent', MANAGER: 'Manager', ADMIN: 'Admin',
};
export const ROLES: Role[] = ['EMPLOYEE', 'AGENT', 'MANAGER', 'ADMIN'];
export const RISK_LABEL: Record<RiskLevel, string> = { NONE: 'No concerns', LOW: 'Minor concerns', MEDIUM: 'Needs a look', HIGH: 'Held back' };
export const RISK_COLOR: Record<RiskLevel, string> = {
  NONE: 'var(--fin)', LOW: 'var(--fin)', MEDIUM: 'var(--ops)', HIGH: 'var(--urgent)',
};

// Plain-language versions of the backend's risk flag codes.
export const FLAG_LABEL: Record<string, string> = {
  PII_LONG_NUMBER: 'contains a long number that could be personal data',
  DISCRIMINATORY_LANGUAGE: 'generalises about a group of people',
  ABSOLUTE_PROMISE: 'makes a promise the team may not be able to keep',
  LEGAL_CLAIM: 'uses legal wording',
  BLAMING_TONE: 'may sound like it blames the requester',
  GENDERED_LANGUAGE: 'uses gendered wording',
  CONTACT_DETAIL: 'includes an email address',
};
export const flagText = (f: string) => FLAG_LABEL[f] ?? f.toLowerCase().replace(/_/g, ' ');

export const OPEN_STATUSES: TicketStatus[] = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'AWAITING_APPROVAL'];

export function timeAgo(iso: string): string {
  const mins = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins} min ago`;
  const hrs = Math.round(mins / 60);
  if (hrs < 24) return `${hrs} h ago`;
  const days = Math.round(hrs / 24);
  return days === 1 ? 'yesterday' : `${days} days ago`;
}

export function greeting(): string {
  const h = new Date().getHours();
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
}

export const firstName = (full?: string) => (full ?? '').split(' ')[0];
