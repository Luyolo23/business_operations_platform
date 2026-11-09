import { useState } from 'react';
import { Link } from 'react-router-dom';
import { complianceApi } from '../api/endpoints';
import { CATEGORIES } from '../api/types';
import { useAsync } from '../components/useAsync';
import { Bars, Panel, RiskMark, Stats, Status, fmtDate } from '../components/ui';
import { DEPT_LABEL, DEPT_VAR, flagText } from '../theme';

export default function CompliancePage() {
  const report = useAsync(() => complianceApi.report(30), []);
  const [page, setPage] = useState(0);
  const audit = useAsync(() => complianceApi.audit(page, 15), [page]);
  const o = report.data?.overview;

  return (
    <>
      <div className="hero">
        <h1>Is the assistant behaving?</h1>
        {report.data && <p className="lede">{report.data.observations.join(' ')}</p>}
      </div>
      <Status loading={report.loading} error={report.error} />

      <Panel title="What this page measures">
        <ul className="list">
          <li><strong>Reply risk.</strong> Every reply — whether written by the assistant or typed by a person — is screened. Medium or high risk replies are held for a manager before the requester sees them.</li>
          <li><strong>Approvals.</strong> How many replies are waiting for a manager, and how many were approved or rejected.</li>
          <li><strong>Routing quality.</strong> How often staff had to correct the assistant’s choice of team or priority.</li>
          <li><strong>First-response consistency.</strong> Average hours until the first reply, broken down by team.</li>
          <li><strong>Flagged replies and audit trail.</strong> The exact replies that triggered a rule, and a record of who changed what.</li>
        </ul>
      </Panel>

      {report.data && o && (
        <>
          <Stats items={[
            { value: o.responsesByRiskLevel.HIGH ?? 0, label: 'replies held back as high risk' },
            { value: o.responsesByRiskLevel.MEDIUM ?? 0, label: 'replies that needed a look' },
            { value: o.pendingApprovals, label: `waiting for approval (${o.approved} approved, ${o.rejected} rejected)` },
            { value: `${Math.round(o.manualOverrideRate * 100)}%`, label: 'of routing corrected by staff' },
          ]} />

          <div className="grid-2">
            <Panel title="How sure the assistant is, by team">
              <Bars rows={CATEGORIES.map((c) => ({ label: DEPT_LABEL[c], value: o.avgClassificationConfidence[c] ?? 0, color: DEPT_VAR[c], text: `${Math.round((o.avgClassificationConfidence[c] ?? 0) * 100)}%` }))} />
            </Panel>
            <Panel title="Hours to the first reply, by team">
              <Bars rows={CATEGORIES.map((c) => ({ label: DEPT_LABEL[c], value: o.avgFirstResponseHours[c] ?? 0, color: DEPT_VAR[c], text: `${o.avgFirstResponseHours[c] ?? 0} h` }))} />
            </Panel>
          </div>

          <Panel title="Recently flagged replies">
            {report.data.recentFlagged.length === 0 ? <p className="muted">No replies have been flagged.</p> : (
              <table className="plain">
                <thead><tr><th>Ticket</th><th>Risk</th><th>Why</th><th>When</th></tr></thead>
                <tbody>
                  {report.data.recentFlagged.map((f) => (
                    <tr key={f.assessmentId}>
                      <td><Link to={`/tickets/${f.ticketId}`}>#{f.ticketId}</Link></td>
                      <td><RiskMark level={f.level} /></td>
                      <td>It {f.flags.map(flagText).join(', and ')}.</td>
                      <td>{fmtDate(f.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Panel>
        </>
      )}

      <Panel title="Audit trail">
        <Status loading={audit.loading} error={audit.error} />
        {audit.data && (
          <>
            <table className="plain">
              <thead><tr><th>When</th><th>What happened</th><th>Who</th><th>Details</th></tr></thead>
              <tbody>
                {audit.data.content.map((a) => (
                  <tr key={a.id}>
                    <td>{fmtDate(a.createdAt)}</td>
                    <td>{a.action.toLowerCase().replace(/_/g, ' ')} ({a.entityType} {a.entityId ?? ''})</td>
                    <td>{a.actorEmail ?? 'system'}</td>
                    <td>{a.details}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="pager">
              <span className="count-chip">Page {audit.data.page + 1} of {Math.max(audit.data.totalPages, 1)}</span>
              <button className="btn btn-line" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
              <button className="btn btn-line" disabled={page + 1 >= audit.data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
            </div>
          </>
        )}
      </Panel>
    </>
  );
}
