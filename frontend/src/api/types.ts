// Mirrors docs/API.md. Keep in sync with the backend DTOs.
export type Category = 'HR' | 'IT' | 'FINANCE' | 'OPERATIONS';
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type TicketStatus = 'NEW' | 'ASSIGNED' | 'IN_PROGRESS' | 'AWAITING_APPROVAL' | 'RESOLVED' | 'CLOSED';
export type Tone = 'FORMAL' | 'FRIENDLY' | 'URGENT';
export type RiskLevel = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH';
export type Role = 'EMPLOYEE' | 'AGENT' | 'MANAGER' | 'ADMIN';
export type ResponseStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export const CATEGORIES: Category[] = ['HR', 'IT', 'FINANCE', 'OPERATIONS'];
export const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
export const STATUSES: TicketStatus[] = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'AWAITING_APPROVAL', 'RESOLVED', 'CLOSED'];
export const TONES: Tone[] = ['FORMAL', 'FRIENDLY', 'URGENT'];

export interface User {
  id: number; email: string; fullName: string; role: Role; department: Category | null; active: boolean;
}
export interface AuthResponse { token: string; expiresInSeconds: number; user: User }

export interface PageResponse<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export interface Ticket {
  id: number; title: string; description: string; category: Category; priority: Priority; status: TicketStatus;
  requesterId: number; requesterName: string; requesterEmail: string; assigneeId: number | null;
  assigneeName: string | null; classificationConfidence: number | null; classificationSource: string | null;
  escalated: boolean; createdAt: string; updatedAt: string; firstResponseAt: string | null; resolvedAt: string | null;
}

export interface TicketResponse {
  id: number; ticketId: number; tone: Tone; body: string; generatedBy: string; transparencyNote: string | null;
  status: ResponseStatus; riskLevel: RiskLevel; riskFlags: string[]; riskNotes: string | null;
  approvalId: number | null; createdAt: string;
}

export interface Approval {
  id: number; responseId: number; ticketId: number; ticketTitle: string; responseBody: string; riskLevel: RiskLevel;
  riskFlags: string[]; status: ApprovalStatus; decidedBy: string | null; decisionComment: string | null;
  createdAt: string; decidedAt: string | null;
}

export interface AppNotification {
  id: number; ticketId: number | null; subject: string; message: string; read: boolean; createdAt: string;
}

export interface DailyCount { date: string; count: number }
export interface Summary {
  days: number; totalTickets: number; openTickets: number; resolvedTickets: number;
  avgFirstResponseHours: number; avgResolutionHours: number; slaBreaches: number; slaBreachRate: number;
  byCategory: Record<Category, number>; byPriority: Record<Priority, number>; byStatus: Record<TicketStatus, number>;
  avgFirstResponseHoursByCategory: Record<Category, number>; dailyVolume: DailyCount[];
}
export interface Insights { summary: Summary; insights: string[] }

export interface Forecast {
  method: string; historyDays: number; horizonDays: number; historicalDailyAverage: number;
  projectedDailyAverage: number; projectedTotal: number; surgeExpected: boolean; message: string;
  history: DailyCount[]; projection: { date: string; predictedTickets: number }[];
  projectedByCategory: Record<Category, number>;
}

export interface WeeklyReport {
  generatedAt: string; department: Category | null; title: string; narrative: string; insights: string[];
  summary: Summary; forecast: Forecast;
}

export interface ComplianceOverview {
  responsesByRiskLevel: Record<RiskLevel, number>; pendingApprovals: number; approved: number; rejected: number;
  avgClassificationConfidence: Record<Category, number>; manualOverrideRate: number;
  avgFirstResponseHours: Record<Category, number>; ticketsConsidered: number;
}
export interface FlaggedItem {
  assessmentId: number; responseId: number; ticketId: number; level: RiskLevel; flags: string[];
  notes: string; createdAt: string;
}
export interface RiskReport { generatedAt: string; overview: ComplianceOverview; recentFlagged: FlaggedItem[]; observations: string[] }
export interface AuditLog { id: number; action: string; entityType: string; entityId: number | null; actorEmail: string | null; details: string | null; createdAt: string }
