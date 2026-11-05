import { useState } from 'react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { analyticsApi } from '../api/endpoints';
import { CATEGORIES, PRIORITIES } from '../api/types';
import { useAsync } from '../components/useAsync';
import { Bars, Panel, Stats, Status } from '../components/ui';
import { DEPT_LABEL, DEPT_VAR, PRIORITY_LABEL } from '../theme';

const PRIORITY_COLOR = { LOW: '#7B8D90', MEDIUM: '#2D52D6', HIGH: '#C9860A', URGENT: '#D6342A' } as const;

export default function DashboardPage() {
  const [days, setDays] = useState(30);
  const [category, setCategory] = useState('');
  const { data, loading, error } = useAsync(() => analyticsApi.insights(days, category || undefined), [days, category]);
  const s = data?.summary;

  return (
    <>
      <div className="hero">
        <h1>How the desk is doing.</h1>
        {data && <p className="lede">{data.insights.join(' ')}</p>}
      </div>

      <div className="filters">
        <select aria-label="Period" value={days} onChange={(e) => setDays(Number(e.target.value))}>
          <option value={7}>Last 7 days</option><option value={14}>Last 14 days</option>
          <option value={30}>Last 30 days</option><option value={90}>Last 90 days</option>
        </select>
        <select aria-label="Team" value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="">All teams</option>{CATEGORIES.map((c) => <option key={c} value={c}>{DEPT_LABEL[c]}</option>)}
        </select>
      </div>

      <Status loading={loading} error={error} />
      {s && (
        <>
          <Stats items={[
            { value: s.totalTickets, label: 'requests received' },
            { value: s.openTickets, label: `still open, ${s.resolvedTickets} resolved` },
            { value: `${s.avgFirstResponseHours} h`, label: 'to the first reply, on average' },
            { value: `${s.avgResolutionHours} h`, label: 'to resolve, on average' },
            { value: s.slaBreaches, label: `past their deadline (${Math.round(s.slaBreachRate * 100)}%)` },
          ]} />

          <Panel title="Requests per day" tight>
            <div className="chart" role="img" aria-label="Line chart of requests received per day">
              <ResponsiveContainer>
                <LineChart data={s.dailyVolume} margin={{ left: -16, right: 8, top: 8 }}>
                  <CartesianGrid stroke="#D3DCD9" vertical={false} />
                  <XAxis dataKey="date" tickFormatter={(d: string) => d.slice(5)} tick={{ fontSize: 12 }} stroke="#7B8D90" />
                  <YAxis allowDecimals={false} tick={{ fontSize: 12 }} stroke="#7B8D90" />
                  <Tooltip />
                  <Line type="monotone" dataKey="count" stroke="#0E4D4A" strokeWidth={2.5} dot={false} name="Requests" />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </Panel>

          <div className="grid-2">
            <Panel title="Where requests go">
              <Bars rows={CATEGORIES.map((c) => ({ label: DEPT_LABEL[c], value: s.byCategory[c] ?? 0, color: DEPT_VAR[c] }))} />
            </Panel>
            <Panel title="How urgent they are">
              <Bars rows={PRIORITIES.map((p) => ({ label: PRIORITY_LABEL[p], value: s.byPriority[p] ?? 0, color: PRIORITY_COLOR[p] }))} />
            </Panel>
          </div>
        </>
      )}
    </>
  );
}
