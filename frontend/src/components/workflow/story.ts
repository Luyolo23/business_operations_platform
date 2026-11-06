import type { Scenario } from './scenarios';

/**
 * The story is a sequence of stages. Everything on screen is a function of the current stage,
 * so the animation always tells the same story in the same order:
 *
 *  0 reset            5 reply drafted
 *  1 request arrives  6 risk & bias check running
 *  2 AI reads it      7 check result (passed, or held for a manager)
 *  3 classified       8 manager approves (held replies only)
 *  4 routed           9 approved, requester notified, resolved
 */
export type Stage = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9;
export const LAST_STAGE: Stage = 9;

export interface Timeline { steps: { at: number; stage: Stage }[]; end: number }

export function timelineFor(s: Scenario): Timeline {
  const steps: { at: number; stage: Stage }[] = [
    { at: 500, stage: 1 }, { at: 1700, stage: 2 }, { at: 3700, stage: 3 }, { at: 4900, stage: 4 },
    { at: 6500, stage: 5 }, { at: 8000, stage: 6 }, { at: 9400, stage: 7 },
  ];
  if (s.held) {
    steps.push({ at: 11600, stage: 8 }, { at: 12800, stage: 9 });
    return { steps, end: 15600 };
  }
  steps.push({ at: 10600, stage: 9 });
  return { steps, end: 13400 };
}

export const CAPTIONS: Record<Stage, string> = {
  0: 'Ready', 1: 'Request in', 2: 'Reading', 3: 'Classified', 4: 'Routing',
  5: 'Drafting', 6: 'Checking', 7: 'Reviewed', 8: 'Approving', 9: 'Resolved',
};

export const STEPS = ['Request', 'Classify', 'Route', 'Respond', 'Check', 'Resolve'] as const;

/** Which of the six headline steps is active at each stage (-1 = none yet). */
export function stepFor(stage: Stage): number {
  if (stage < 1) return -1;
  if (stage === 1) return 0;
  if (stage <= 3) return 1;
  if (stage === 4) return 2;
  if (stage === 5) return 3;
  if (stage <= 7) return 4;
  return 5;
}

export type Tone = 'pending' | 'processing' | 'approved' | 'resolved';
export interface PillState { label: string; tone: Tone }

export function ticketPill(stage: Stage): PillState {
  if (stage <= 1) return { label: 'Pending', tone: 'pending' };
  if (stage < 9) return { label: 'Processing', tone: 'processing' };
  return { label: 'Resolved', tone: 'resolved' };
}

export function replyPill(stage: Stage, held: boolean): PillState | null {
  if (stage < 5) return null;
  if (stage === 5) return { label: 'Drafting', tone: 'processing' };
  if (stage === 6) return { label: 'Checking', tone: 'processing' };
  if (stage === 7 && held) return { label: 'Needs approval', tone: 'pending' };
  return { label: 'Approved', tone: 'approved' };
}
