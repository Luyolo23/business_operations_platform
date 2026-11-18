import { useRef, type CSSProperties, type PointerEvent } from 'react';
import './workflow.css';
import { CHECKS, DEPTS, SCENARIOS } from './scenarios';
import { CAPTIONS, STEPS, replyPill, stepFor, ticketPill, type PillState, type Stage } from './story';
import { useFit, useInView, usePageVisible, usePrefersReducedMotion, useStoryClock } from './hooks';

/** Logical size of the 3D stage. It is scaled to fit its container, so all coordinates below are stage pixels. */
const W = 600;
const H = 760;

const cx = (...c: (string | false | undefined)[]) => c.filter(Boolean).join(' ');
const vars = (v: Record<string, string | number>) => v as CSSProperties;

// Static decoration: slow drifting dust gives the scene depth without competing with the story.
const DUST = Array.from({ length: 9 }, (_, i) => ({
  x: `${(i * 37 + 11) % 100}%`, y: `${(i * 53 + 17) % 100}%`, t: `${9 + (i % 4) * 3}s`, d: `-${i * 1.7}s`,
}));
const ANALYSING = [34, 52, 28, 44];            // bar widths while the classifier is "thinking"
const SPARK = [38, 52, 44, 66, 58, 74, 62, 80, 70, 88, 76, 92]; // engine activity bars (decorative)

function Pill({ state }: { state: PillState }) {
  return <span className={`wf-pill wf-pill-${state.tone}`}><i />{state.label}</span>;
}

/** A short burst of glowing dots that travels along an SVG path (CSS offset-path, runs once on mount). */
function Particle({ d, color = '#9FFFE6', delay = 0 }: { d: string; color?: string; delay?: number }) {
  return (
    <>
      {[0, 1, 2, 3].map((i) => (
        <i key={i} className="wf-particle"
          style={vars({ '--path': `path("${d}")`, '--pc': color, '--delay': `${delay + i * 0.07}s`, '--k': 1 - i * 0.2 })} />
      ))}
    </>
  );
}

export default function WorkflowScene() {
  const vizRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const reduced = usePrefersReducedMotion();
  const inView = useInView(vizRef);
  const pageVisible = usePageVisible();
  const scale = useFit(vizRef, W, H);
  const { index, stage, completed } = useStoryClock(!inView || !pageVisible, reduced);

  const s = SCENARIOS[index];
  const dept = DEPTS.find((d) => d.key === s.dept) ?? DEPTS[0];
  const at = (n: number) => stage >= n;
  const st = stage as Stage;
  const tPill = ticketPill(st);
  const rPill = replyPill(st, s.held);
  const active = stepFor(st);

  const pathIn = 'M190 136 C190 196, 300 172, 300 234';
  const pathDept = (x: number) => `M300 374 C300 416, ${x} 410, ${x} 452`;
  const pathResp = `M${dept.x} 508 C${dept.x} 540, 186 534, 186 566`;
  const pathGate = 'M348 640 L384 640';

  // Gentle pointer parallax: tilts the whole stage a few degrees. Skipped for touch and reduced motion.
  const onMove = (e: PointerEvent<HTMLDivElement>) => {
    if (reduced || e.pointerType === 'touch' || !stageRef.current) return;
    const r = e.currentTarget.getBoundingClientRect();
    const nx = ((e.clientX - r.left) / r.width) * 2 - 1;
    const ny = ((e.clientY - r.top) / r.height) * 2 - 1;
    stageRef.current.style.setProperty('--rx', `${8 - ny * 4}deg`);
    stageRef.current.style.setProperty('--ry', `${-10 + nx * 5}deg`);
  };
  const onLeave = () => {
    stageRef.current?.style.removeProperty('--rx');
    stageRef.current?.style.removeProperty('--ry');
  };

  const processed = completed + (stage >= 9 ? 1 : 0);

  return (
    <div className="wf">
      <div
        ref={vizRef}
        className={cx('wf-viz', reduced && 'is-reduced')}
        role="img"
        aria-label="Animated illustration of the request journey: an employee request arrives, AI classifies it, the platform routes it to HR, IT, Finance or Operations, drafts a reply, checks it for risk and bias, then a manager approves risky replies and the requester is notified."
        onPointerMove={onMove}
        onPointerLeave={onLeave}
      >
        <div className="wf-bg" aria-hidden="true">
          {DUST.map((p, i) => <i key={i} className="wf-dust" style={vars({ '--x': p.x, '--y': p.y, '--t': p.t, '--d': p.d })} />)}
        </div>

        <div className="wf-fit" style={vars({ '--s': scale })} aria-hidden="true">
          <div className="wf-stage" ref={stageRef}>

            {/* ---------- connection lines ---------- */}
            <svg className="wf-wires" viewBox={`0 0 ${W} ${H}`}>
              <path d={pathIn} className="wf-wire" />
              <path d={pathIn} pathLength={1} className={cx('wf-live', at(2) && 'on')} style={vars({ stroke: '#9FFFE6', color: '#9FFFE6' })} />
              {DEPTS.map((d) => <path key={d.key} d={pathDept(d.x)} className="wf-wire" />)}
              <path d={pathDept(dept.x)} pathLength={1} className={cx('wf-live', at(4) && 'on')} style={vars({ stroke: dept.color, color: dept.color })} />
              <path d={pathResp} className="wf-wire" />
              <path d={pathResp} pathLength={1} className={cx('wf-live', at(5) && 'on')} style={vars({ stroke: dept.color, color: dept.color })} />
              <path d={pathGate} className="wf-wire wf-gate" />
              <path d={pathGate} pathLength={1} className={cx('wf-live wf-gate', at(6) && 'on')} style={vars({ stroke: '#9FFFE6', color: '#9FFFE6' })} />
            </svg>

            {/* ---------- travelling particles: one burst per step of the story ---------- */}
            {stage === 2 && <Particle key={`in-${index}`} d={pathIn} />}
            {stage === 4 && <Particle key={`route-${index}`} d={pathDept(dept.x)} color={dept.color} />}
            {stage === 5 && <Particle key={`resp-${index}`} d={pathResp} color={dept.color} />}
            {stage === 6 && <Particle key={`gate-${index}`} d={pathGate} />}

            {/* ---------- 1. incoming request ---------- */}
            <article className={cx('wf-card wf-ticket', at(1) && 'on', at(9) && 'done')}>
              <header>
                <span className="wf-avatar">{s.initials}</span>
                <div className="wf-who"><strong>{s.requester}</strong><small>Employee, just now</small></div>
                <Pill key={tPill.label} state={tPill} />
              </header>
              <h4>{s.title}</h4>
              <p>{s.snippet}</p>
              <footer className={cx(at(4) && 'on')}>
                <span className="wf-chip" style={vars({ '--c': dept.color })}><i />{dept.label}</span>
                <span className="wf-chip plain">{s.priority} priority</span>
              </footer>
            </article>

            {/* top-right slot: engine stats, then the assignee, then the notification */}
            <div className="wf-glass wf-slot on">
              <div className={cx('wf-face', stage <= 3 && 'on')}>
                <small>Requests processed</small>
                <strong>{String(processed).padStart(2, '0')}</strong>
                <div className="wf-spark">{SPARK.map((h, i) => <i key={i} style={vars({ '--h': `${h}%`, '--i': i })} />)}</div>
              </div>
              <div className={cx('wf-face', stage >= 4 && stage <= 8 && 'on')}>
                <small>Routed to {dept.label}</small>
                <div className="wf-assign"><span className="wf-avatar sm" style={vars({ '--c': dept.color })}>{s.assignee[0]}</span><strong>{s.assignee}</strong></div>
                <small>Assigned automatically</small>
              </div>
              <div className={cx('wf-face good', stage >= 9 && 'on')}>
                <small>Request resolved</small>
                <div className="wf-assign"><span className="wf-tick">✓</span><strong>{s.notified}</strong></div>
                <small>Update sent by the platform</small>
              </div>
            </div>

            {/* ---------- 2. AI classification ---------- */}
            <div className={cx('wf-glass wf-signals', at(2) && 'on')}>
              <h5>Reading the request</h5>
              <div>{s.signals.map((sig, i) => <span key={sig} className="wf-sig" style={vars({ '--d': `${0.25 + i * 0.35}s` })}>{sig}</span>)}</div>
            </div>

            <div className={cx('wf-core', at(2) && stage <= 6 && 'hot')}>
              <div className="wf-glow" />
              <div className="wf-ring wf-r2" /><div className="wf-ring wf-r1" /><div className="wf-ring wf-r3" />
              <div className="wf-sphere"><div className="wf-hue" /><div className="wf-shade" />
                <div className="wf-core-text"><b>AI</b><span>{CAPTIONS[st]}</span></div>
              </div>
            </div>

            <div className={cx('wf-glass wf-classify', at(2) && 'on', stage === 2 && 'scan')}>
              <h5>Classifying</h5>
              {DEPTS.map((d, i) => {
                const win = at(3) && d.key === s.dept;
                const w = at(3) ? s.probs[d.key] : ANALYSING[i];
                return (
                  <div key={d.key} className={cx('wf-row', win && 'win', at(3) && !win && 'dim')} style={vars({ '--c': d.color, '--i': i })}>
                    <span>{d.label}</span>
                    <div className="wf-bar"><b style={vars({ '--w': `${w}%` })} /></div>
                    <em>{at(3) ? `${s.probs[d.key]}%` : '…'}</em>
                  </div>
                );
              })}
            </div>

            {/* ---------- 3. intelligent routing: department nodes ---------- */}
            {DEPTS.map((d) => {
              const hit = at(4) && d.key === s.dept;
              const load = Math.min(96, d.load + (hit ? 8 : 0));
              return (
                <div key={d.key} className={cx('wf-glass wf-dept on', hit && 'hit', at(4) && !hit && 'dim')}
                  style={vars({ left: d.x - 62, '--c': d.color })}>
                  <div><i className="wf-dot" /><strong>{d.label}</strong><em>{d.open + (hit ? 1 : 0)} open</em></div>
                  <div className="wf-bar"><b style={vars({ '--w': `${load}%` })} /></div>
                </div>
              );
            })}

            {/* ---------- 4. AI response ---------- */}
            <article className={cx('wf-card wf-reply', at(5) && 'on', stage === 6 && 'scanning')}>
              <header>
                <span className="wf-ai">AI</span>
                <div className="wf-who"><strong>Suggested reply</strong><small>Formal tone, {dept.label}</small></div>
                {rPill && <Pill key={rPill.label} state={rPill} />}
              </header>
              <p>{s.reply}</p>
            </article>

            {/* ---------- 5. risk and bias layer ---------- */}
            <div className={cx('wf-glass wf-risk wf-gate', at(5) && 'on')}>
              <h5>
                <svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true"><path d="M8 1.2 2.4 3.4v4.2c0 3.1 2.3 5.6 5.6 7.2 3.3-1.6 5.6-4.1 5.6-7.2V3.4L8 1.2Z" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" /></svg>
                Risk and bias check
              </h5>
              {CHECKS.map((label, i) => {
                const flagged = s.held && i === 2;
                return (
                  <div key={label} className={cx('wf-check', at(6) && 'run', at(7) && (flagged ? 'flag' : 'pass'))} style={vars({ '--i': i })}>
                    <span className="wf-ic"><b className="sp" /><b className="ok" /><b className="no" /></span>
                    {flagged && at(7) ? 'Promise wording flagged' : label}
                  </div>
                );
              })}
              <div className={cx('wf-verdict', at(7) && 'on', s.held && stage < 8 && 'hold')}>
                {s.held ? (stage >= 8 ? 'Approved by a manager' : 'Held for a manager') : 'Passed, approved automatically'}
              </div>
            </div>

          </div>
        </div>
      </div>

      {/* The six headline steps, always readable (not tilted) */}
      <ol className="wf-steps" aria-hidden="true">
        {STEPS.map((label, i) => (
          <li key={label} className={cx(i < active && 'done', i === active && (stage === 9 && i === 5 ? 'done' : 'active'))}>
            <span>{i + 1}</span>{label}
          </li>
        ))}
      </ol>
    </div>
  );
}
