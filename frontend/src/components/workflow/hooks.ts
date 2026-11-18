import { useEffect, useState, type RefObject } from 'react';
import { SCENARIOS } from './scenarios';
import { LAST_STAGE, timelineFor, type Stage } from './story';

export function usePrefersReducedMotion(): boolean {
  const query = '(prefers-reduced-motion: reduce)';
  const [reduced, setReduced] = useState(() => typeof window !== 'undefined' && window.matchMedia(query).matches);
  useEffect(() => {
    const m = window.matchMedia(query);
    const on = () => setReduced(m.matches);
    m.addEventListener('change', on);
    return () => m.removeEventListener('change', on);
  }, []);
  return reduced;
}

/** True while the element is on screen. Used to pause the story when it is scrolled away. */
export function useInView(ref: RefObject<Element>): boolean {
  const [inView, setInView] = useState(true);
  useEffect(() => {
    const el = ref.current;
    if (!el || typeof IntersectionObserver === 'undefined') return;
    const io = new IntersectionObserver(([entry]) => setInView(entry.isIntersecting), { threshold: 0.15 });
    io.observe(el);
    return () => io.disconnect();
  }, [ref]);
  return inView;
}

/** False while the browser tab is in the background. */
export function usePageVisible(): boolean {
  const [visible, setVisible] = useState(() => typeof document === 'undefined' || !document.hidden);
  useEffect(() => {
    const on = () => setVisible(!document.hidden);
    document.addEventListener('visibilitychange', on);
    return () => document.removeEventListener('visibilitychange', on);
  }, []);
  return visible;
}

/** Scale factor that fits a fixed-size stage (w x h) into whatever space the container has. */
export function useFit(ref: RefObject<HTMLElement>, w: number, h: number): number {
  const [scale, setScale] = useState(1);
  useEffect(() => {
    const el = ref.current;
    if (!el || typeof ResizeObserver === 'undefined') return;
    const measure = () => {
      const r = el.getBoundingClientRect();
      if (r.width > 0 && r.height > 0) setScale(Math.min(1.3, Math.max(0.3, Math.min(r.width / w, r.height / h))));
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, [ref, w, h]);
  return scale;
}

/**
 * Drives the story: plays one scenario's timeline, holds the final frame, fades out, then moves to the next.
 * Pausing (off screen or background tab) stops all timers; resuming replays the current scenario from the start.
 * With reduced motion there are no timers: the final, fully resolved frame is shown.
 */
export function useStoryClock(paused: boolean, reduced: boolean) {
  const [index, setIndex] = useState(0);
  const [stage, setStage] = useState<Stage>(reduced ? LAST_STAGE : 0);
  const [completed, setCompleted] = useState(0);

  useEffect(() => {
    if (reduced) { setStage(LAST_STAGE); return; }
    if (paused) return;
    setStage(0);
    const tl = timelineFor(SCENARIOS[index]);
    const ids: number[] = tl.steps.map(({ at, stage: next }) => window.setTimeout(() => setStage(next), at));
    ids.push(window.setTimeout(() => setStage(0), tl.end));
    ids.push(window.setTimeout(() => {
      setCompleted((c) => c + 1);
      setIndex((i) => (i + 1) % SCENARIOS.length);
    }, tl.end + 700));
    return () => ids.forEach((id) => window.clearTimeout(id));
  }, [index, paused, reduced]);

  return { index, stage, completed };
}
