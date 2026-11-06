export type Dept = 'HR' | 'IT' | 'FINANCE' | 'OPERATIONS';

/** Department nodes along the routing row. `x` is the node centre in stage coordinates. */
export const DEPTS: { key: Dept; label: string; x: number; open: number; load: number; color: string }[] = [
  { key: 'HR', label: 'HR', x: 78, open: 9, load: 46, color: '#C77BB4' },
  { key: 'IT', label: 'IT', x: 226, open: 14, load: 64, color: '#7C95FF' },
  { key: 'FINANCE', label: 'Finance', x: 374, open: 7, load: 38, color: '#6FC48F' },
  { key: 'OPERATIONS', label: 'Operations', x: 522, open: 11, load: 52, color: '#E7B64A' },
];

export interface Scenario {
  id: string;
  requester: string;
  initials: string;
  title: string;
  snippet: string;
  dept: Dept;
  priority: string;
  /** Classifier confidence per department, in percent. The winner is `dept`. */
  probs: Record<Dept, number>;
  signals: string[];
  assignee: string;
  reply: string;
  /** Third check flags a risky promise and holds the reply for a manager. */
  held: boolean;
  notified: string;
}

// Story order: one request per department. The finance one is deliberately flagged
// so the risk layer and the approval step are visible.
export const SCENARIOS: Scenario[] = [
  {
    id: 'it', requester: 'Emma Employee', initials: 'EE', title: 'Password reset required',
    snippet: 'I’m locked out of my laptop since this morning’s update.',
    dept: 'IT', priority: 'High', probs: { HR: 1, IT: 96, FINANCE: 1, OPERATIONS: 2 },
    signals: ['password', 'locked out', 'laptop'], assignee: 'Ian in IT',
    reply: 'Hi Emma, thanks for flagging this. We’ve started on your password reset and will update you here shortly.',
    held: false, notified: 'Emma was notified',
  },
  {
    id: 'hr', requester: 'Thabo Nkosi', initials: 'TN', title: 'Leave application',
    snippet: 'I’d like to apply for five days of annual leave in December.',
    dept: 'HR', priority: 'Medium', probs: { HR: 94, IT: 1, FINANCE: 1, OPERATIONS: 4 },
    signals: ['annual leave', 'December', 'apply'], assignee: 'Hana in HR',
    reply: 'Hi Thabo, thanks for your leave request. Hana will check your balance and confirm the next steps.',
    held: false, notified: 'Thabo was notified',
  },
  {
    id: 'fin', requester: 'Lerato Dlamini', initials: 'LD', title: 'Invoice discrepancy',
    snippet: 'Supplier invoice 4471 doesn’t match the purchase order.',
    dept: 'FINANCE', priority: 'High', probs: { HR: 1, IT: 1, FINANCE: 92, OPERATIONS: 6 },
    signals: ['invoice', 'purchase order', 'mismatch'], assignee: 'Fiona in Finance',
    reply: 'Hi Lerato, we guarantee a corrected invoice within two days.',
    held: true, notified: 'Lerato was notified',
  },
  {
    id: 'ops', requester: 'Sipho Maseko', initials: 'SM', title: 'Supplier delivery delayed',
    snippet: 'Our stock shipment hasn’t arrived and the warehouse is waiting.',
    dept: 'OPERATIONS', priority: 'Medium', probs: { HR: 1, IT: 1, FINANCE: 8, OPERATIONS: 90 },
    signals: ['shipment', 'warehouse', 'stock'], assignee: 'Olu in Operations',
    reply: 'Hi Sipho, thanks for the heads-up. Olu is contacting the supplier and will share what we find.',
    held: false, notified: 'Sipho was notified',
  },
];

export const CHECKS = ['No personal data', 'Neutral tone', 'No risky promises'] as const;
