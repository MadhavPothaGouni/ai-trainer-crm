import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getTicketSlaStatus } from "../../api/sla";
import type { TicketSlaStatusDto } from "../../types/api";

/**
 * Self-contained - fetches its own data rather than taking it as a prop, so dropping it into
 * TicketDetailPage is a one-line addition rather than a restructuring of that page's existing
 * data-loading effect. Renders nothing (not even a loading state) until the first response comes
 * back, and renders nothing at all if no policy covers this ticket (the common case for orgs that
 * haven't configured SLA policies yet) - there's no reason to show an empty "SLA" card on every
 * ticket page for a feature most tickets will never use.
 */
export function TicketSlaWidget({ ticketId }: { ticketId: string }) {
  const [status, setStatus] = useState<TicketSlaStatusDto | null>(null);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getTicketSlaStatus(ticketId)
      .then((data) => {
        if (!cancelled) {
          setStatus(data);
          setLoaded(true);
        }
      })
      .catch(() => {
        if (!cancelled) setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, [ticketId]);

  if (!loaded || !status) {
    return null;
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-medium text-slate-500">SLA</h2>
        {status.escalated && (
          <span className="inline-block rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-medium text-red-700">Escalated</span>
        )}
      </div>
      <dl className="mt-3 flex flex-col gap-2 text-sm">
        <DeadlineRow label="First response" dueAt={status.responseDueAt} breached={status.responseBreached} />
        <DeadlineRow label="Resolution" dueAt={status.resolutionDueAt} breached={status.resolutionBreached} />
      </dl>
      <Link to="/sla-policies" className="mt-3 inline-block text-xs text-slate-400 hover:text-slate-700 hover:underline">
        Manage SLA policies
      </Link>
    </div>
  );
}

function DeadlineRow({ label, dueAt, breached }: { label: string; dueAt: string; breached: boolean }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className={breached ? "font-medium text-red-600" : "text-slate-900"}>
        {new Date(dueAt).toLocaleString()}
        {breached && " (breached)"}
      </dd>
    </div>
  );
}
