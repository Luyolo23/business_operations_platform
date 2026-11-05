import { useState } from 'react';
import { reportApi } from '../api/endpoints';
import { downloadFile } from '../api/client';
import { CATEGORIES } from '../api/types';
import { useAsync } from '../components/useAsync';
import { Notice, Stats, Status, fmtDate } from '../components/ui';
import { DEPT_LABEL } from '../theme';

export default function ReportsPage() {
  const [department, setDepartment] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const { data, loading, error } = useAsync(() => reportApi.weekly(department || undefined), [department]);

  async function pdf() {
    setErr(null);
    try {
      await downloadFile(reportApi.pdfPath(department || undefined), `weekly-report-${department.toLowerCase() || 'all'}.pdf`);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'The PDF could not be downloaded.');
    }
  }

  return (
    <>
      <div className="hero">
        <h1>The week in review.</h1>
        <p className="lede">A summary you can read in a minute and share with a PDF.</p>
      </div>

      <div className="row" style={{ marginBottom: 28 }}>
        <div className="pills" role="group" aria-label="Team">
          <button className="pill-btn" aria-pressed={department === ''} onClick={() => setDepartment('')}>All teams</button>
          {CATEGORIES.map((c) => (
            <button key={c} className="pill-btn" aria-pressed={department === c} onClick={() => setDepartment(c)}>{DEPT_LABEL[c]}</button>
          ))}
        </div>
        <button className="btn" onClick={pdf}>Download PDF</button>
      </div>
      {err && <Notice tone="warn">{err}</Notice>}
      <Status loading={loading} error={error} />
      {data && (
        <article className="doc">
          <p className="muted">{data.title}, generated {fmtDate(data.generatedAt)}</p>
          <p className="prose" style={{ marginTop: 14 }}>{data.narrative}</p>
          <div style={{ margin: '28px 0' }}>
            <Stats items={[
              { value: data.summary.totalTickets, label: 'requests this week' },
              { value: `${data.summary.openTickets} / ${data.summary.resolvedTickets}`, label: 'open / resolved' },
              { value: `${data.summary.avgFirstResponseHours} h`, label: 'to the first reply' },
              { value: data.summary.slaBreaches, label: 'past their deadline' },
            ]} />
          </div>
          <h2>Highlights</h2>
          <ul>{data.insights.map((i, n) => <li key={n}>{i}</li>)}</ul>
          <h2>Looking ahead</h2>
          <p className="prose">{data.forecast.message}</p>
        </article>
      )}
    </>
  );
}
