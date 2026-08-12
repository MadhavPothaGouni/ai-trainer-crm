import { useEffect, useState } from "react";
import { getPipelineTrend } from "../../api/forecast";
import { Alert } from "../../components/ui/Alert";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import type { OpportunityStage, PipelineTrendPointDto } from "../../types/api";

const STAGE_LABELS: Record<OpportunityStage, string> = {
  PROSPECTING: "Prospecting",
  QUALIFICATION: "Qualification",
  PROPOSAL: "Proposal",
  NEGOTIATION: "Negotiation",
  CLOSED_WON: "Closed won",
  CLOSED_LOST: "Closed lost",
};

const STAGE_BAR_CLASSES: Record<OpportunityStage, string> = {
  PROSPECTING: "bg-slate-400",
  QUALIFICATION: "bg-blue-400",
  PROPOSAL: "bg-amber-400",
  NEGOTIATION: "bg-orange-400",
  CLOSED_WON: "bg-emerald-500",
  CLOSED_LOST: "bg-red-400",
};

function isoDaysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().slice(0, 10);
}

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * A daily pipeline history, captured once a day by the backend's PipelineSnapshotService -
 * genuinely different from the Reports page's pipeline-by-stage view, which only ever shows
 * right now. No charting library, in keeping with ReportsPage's own "plain Tailwind, no extra
 * dependency" bar-chart style - here that's a bar per captured day, height scaled to that day's
 * total value, since forecast/trend already returns one folded point per day rather than raw rows.
 */
export default function PipelineTrendPage() {
  const [from, setFrom] = useState(isoDaysAgo(30));
  const [to, setTo] = useState(todayIso());
  const [trend, setTrend] = useState<PipelineTrendPointDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  function reload() {
    if (from > to) {
      setError("'From' must not be after 'To'.");
      return;
    }
    setError(null);
    setIsLoading(true);
    getPipelineTrend({ from, to })
      .then((data) => setTrend(data))
      .catch((err: unknown) => {
        setError(
          err instanceof ApiError && err.status === 403
            ? "You don't have access to forecasting. Ask an organization admin for the Report permission."
            : err instanceof ApiError
              ? err.message
              : "Could not load pipeline trend.",
        );
      })
      .finally(() => setIsLoading(false));
  }

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const maxValue = Math.max(1, ...(trend ?? []).map((point) => point.totalValue));

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Pipeline Forecast</h1>
        <p className="mt-1 text-sm text-slate-500">
          Daily pipeline value over time, captured automatically once a day. Unlike Reports, which only ever shows the
          pipeline as it looks right now, this reflects what it looked like on each day it was captured.
        </p>
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          reload();
        }}
        className="flex flex-wrap items-end gap-4 rounded-lg border border-slate-200 bg-white p-4"
      >
        <TextField label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        <TextField label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        <button
          type="submit"
          className="h-fit rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
        >
          Apply
        </button>
      </form>

      {error && <Alert variant="error">{error}</Alert>}
      {isLoading && !error && <p className="text-sm text-slate-400">Loading...</p>}

      {!isLoading && trend && trend.length === 0 && !error && (
        <p className="text-sm text-slate-400">
          No snapshots captured in this range yet - the first one appears after the daily capture job next runs.
        </p>
      )}

      {trend && trend.length > 0 && (
        <>
          <div className="rounded-lg border border-slate-200 bg-white p-5">
            <h2 className="text-sm font-medium text-slate-500">Total pipeline value by day</h2>
            <div className="mt-4 flex h-48 items-end gap-1">
              {trend.map((point) => (
                <div
                  key={point.date}
                  className="flex-1 rounded-t bg-indigo-400"
                  style={{ height: `${Math.max(2, (point.totalValue / maxValue) * 100)}%` }}
                  title={`${point.date}: ${point.totalValue.toLocaleString()} across ${point.dealCount} deal${point.dealCount === 1 ? "" : "s"}`}
                />
              ))}
            </div>
            <div className="mt-2 flex justify-between text-xs text-slate-400">
              <span>{trend[0].date}</span>
              <span>{trend[trend.length - 1].date}</span>
            </div>
          </div>

          <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3 font-medium">Date</th>
                  <th className="px-4 py-3 font-medium">Deals</th>
                  <th className="px-4 py-3 font-medium">Total value</th>
                  <th className="px-4 py-3 font-medium">By stage</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {[...trend].reverse().map((point) => (
                  <tr key={point.date} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-900">{point.date}</td>
                    <td className="px-4 py-3 text-slate-500">{point.dealCount}</td>
                    <td className="px-4 py-3 text-slate-500">{point.totalValue.toLocaleString()}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-2">
                        {(Object.entries(point.valueByStage) as [OpportunityStage, number][])
                          .filter(([, value]) => value > 0)
                          .map(([stage, value]) => (
                            <span key={stage} className="inline-flex items-center gap-1 text-xs text-slate-500">
                              <span className={`h-2 w-2 rounded-full ${STAGE_BAR_CLASSES[stage]}`} />
                              {STAGE_LABELS[stage]}: {value.toLocaleString()}
                            </span>
                          ))}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
