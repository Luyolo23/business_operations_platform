import { useState } from 'react';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { forecastApi } from '../api/endpoints';
import { CATEGORIES } from '../api/types';
import { useAsync } from '../components/useAsync';
import { Bars, Notice, Panel, Stats, Status } from '../components/ui';
import { DEPT_LABEL, DEPT_VAR } from '../theme';

export default function ForecastPage() {
  const [horizon, setHorizon] = useState(7);
  const { data, loading, error } = useAsync(() => forecastApi.get(horizon), [horizon]);

  const chart = data ? [
    ...data.history.map((h) => ({ date: h.date, actual: h.count as number | null, projected: null as number | null })),
    ...data.projection.map((p) => ({ date: p.date, actual: null as number | null, projected: p.predictedTickets as number | null })),
  ] : [];

  return (
    <>
      <div className="hero">
        <h1>What the next {horizon} days look like.</h1>
        {data && <p className="lede">{data.message}</p>}
      </div>
      <div className="filters">
        <select aria-label="Forecast length" value={horizon} onChange={(e) => setHorizon(Number(e.target.value))}>
          <option value={7}>Next 7 days</option><option value={14}>Next 14 days</option><option value={30}>Next 30 days</option>
        </select>
      </div>
      <Status loading={loading} error={error} />
      {data && (
        <>
          {data.surgeExpected && <Notice tone="warn">A surge is likely. Check that each team has the capacity to cover it.</Notice>}
          <Stats items={[
            { value: data.historicalDailyAverage, label: `requests a day, last ${data.historyDays} days` },
            { value: data.projectedDailyAverage, label: 'requests a day expected' },
            { value: data.projectedTotal, label: `requests expected in ${data.horizonDays} days` },
          ]} />
          <Panel title="Past and projected" tight>
            <div className="chart" role="img" aria-label="Line chart of actual and projected requests per day">
              <ResponsiveContainer>
                <LineChart data={chart} margin={{ left: -16, right: 8, top: 8 }}>
                  <CartesianGrid stroke="#D3DCD9" vertical={false} />
                  <XAxis dataKey="date" tickFormatter={(d: string) => d.slice(5)} tick={{ fontSize: 12 }} stroke="#7B8D90" />
                  <YAxis allowDecimals={false} tick={{ fontSize: 12 }} stroke="#7B8D90" />
                  <Tooltip /><Legend />
                  <Line type="monotone" dataKey="actual" stroke="#0E4D4A" strokeWidth={2.5} dot={false} name="Actual" connectNulls={false} />
                  <Line type="monotone" dataKey="projected" stroke="#2D52D6" strokeWidth={2.5} strokeDasharray="6 5" dot={false} name="Projected" connectNulls={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
            <p className="muted" style={{ marginTop: 10 }}>This is a straight-line trend over the last {data.historyDays} days. It shows direction, not certainty.</p>
          </Panel>
          <Panel title="Who will feel it">
            <Bars rows={CATEGORIES.map((c) => ({ label: DEPT_LABEL[c], value: data.projectedByCategory[c] ?? 0, color: DEPT_VAR[c] }))} />
          </Panel>
        </>
      )}
    </>
  );
}
