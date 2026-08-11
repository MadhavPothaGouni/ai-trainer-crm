import { useEffect, useState } from "react";
import { getLeadFunnel, getLeaderboard, getPipelineByStage } from "../../api/reports";
import { Alert } from "../../components/ui/Alert";
import { ApiError } from "../../lib/apiClient";
import type { LeadFunnelStageDto, PipelineStageSummaryDto, RepLeaderboardEntryDto } from "../../types/api";

/**
 * Three read-only views over the same aggregation endpoints the backend's
 * ReportController exposes: pipeline value by stage, the lead conversion
 * funnel, and a per-rep leaderboard. No charting library - these are
 * simple CSS-width bar charts, in keeping with this frontend's existing
 * "plain Tailwind, no extra dependency" style (see the rest of pages/ for
 * precedent). Every stage/status always has a row (the backend zero-fills
 * them), so a brand-new organization with no data yet still renders a
 * sensible, if empty-looking, chart rather than a blank space.
 *
 * <p>PipelineByStageCard/LeadFunnelCard/LeaderboardCard are exported (not
 * page-local) so DashboardDetailPage can render the exact same widgets
 * inside a saved dashboard, rather than reimplementing three near-identical
 * charts.
 */
export default function ReportsPage() {
  const [pipeline, setPipeline] = useState<PipelineStageSummaryDto[] | null>(null);
  const [funnel, setFunnel] = useState<LeadFunnelStageDto[] | null>(null);
  const [leaderboard, setLeaderboard] = useState<RepLeaderboardEntryDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    Promise.all([getPipelineByStage(), getLeadFunnel(), getLeaderboard()])
      .then(([pipelineData, funnelData, leaderboardData]) => {
        if (cancelled) return;
        setPipeline(pipelineData);
        setFunnel(funnelData);
        setLeaderboard(leaderboardData);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(
            err instanceof ApiError && err.status === 403
              ? "You don't have access to reports. Ask an organization admin for the Report permission."
              : err instanceof ApiError
                ? err.message
                : "Could not load reports.",
          );
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Reports</h1>
        <p className="mt-1 text-sm text-slate-500">Pipeline value, lead conversion, and rep performance.</p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}
      {isLoading && !error && <p className="text-sm text-slate-400">Loading...</p>}

      {pipeline && <PipelineByStageCard rows={pipeline} />}
      {funnel && <LeadFunnelCard rows={funnel} />}
      {leaderboard && <LeaderboardCard rows={leaderboard} />}
    </div>
  );
}

const STAGE_LABELS: Record<PipelineStageSummaryDto["stage"], string> = {
  PROSPECTING: "Prospecting",
  QUALIFICATION: "Qualification",
  PROPOSAL: "Proposal",
  NEGOTIATION: "Negotiation",
  CLOSED_WON: "Closed won",
  CLOSED_LOST: "Closed lost",
};

const STAGE_BAR_CLASSES: Record<PipelineStageSummaryDto["stage"], string> = {
  PROSPECTING: "bg-slate-400",
  QUALIFICATION: "bg-blue-400",
  PROPOSAL: "bg-amber-400",
  NEGOTIATION: "bg-orange-400",
  CLOSED_WON: "bg-emerald-500",
  CLOSED_LOST: "bg-red-400",
};

export function PipelineByStageCard({ rows }: { rows: PipelineStageSummaryDto[] }) {
  const maxAmount = Math.max(1, ...rows.map((row) => row.totalAmount));
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-medium text-slate-500">Pipeline by stage</h2>
      <div className="mt-4 flex flex-col gap-3">
        {rows.map((row) => (
          <div key={row.stage} className="flex items-center gap-3 text-sm">
            <span className="w-28 shrink-0 text-slate-600">{STAGE_LABELS[row.stage]}</span>
            <div className="h-4 flex-1 overflow-hidden rounded bg-slate-100">
              <div
                className={`h-full rounded ${STAGE_BAR_CLASSES[row.stage]}`}
                style={{ width: `${(row.totalAmount / maxAmount) * 100}%` }}
              />
            </div>
            <span className="w-32 shrink-0 text-right text-slate-900">
              {row.totalAmount.toLocaleString()} <span className="text-slate-400">&middot; {row.opportunityCount}</span>
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

const FUNNEL_LABELS: Record<LeadFunnelStageDto["status"], string> = {
  NEW: "New",
  CONTACTED: "Contacted",
  QUALIFIED: "Qualified",
  UNQUALIFIED: "Unqualified",
  CONVERTED: "Converted",
};

export function LeadFunnelCard({ rows }: { rows: LeadFunnelStageDto[] }) {
  const maxCount = Math.max(1, ...rows.map((row) => row.leadCount));
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-medium text-slate-500">Lead conversion funnel</h2>
      <div className="mt-4 flex flex-col gap-3">
        {rows.map((row) => (
          <div key={row.status} className="flex items-center gap-3 text-sm">
            <span className="w-28 shrink-0 text-slate-600">{FUNNEL_LABELS[row.status]}</span>
            <div className="h-4 flex-1 overflow-hidden rounded bg-slate-100">
              <div className="h-full rounded bg-indigo-400" style={{ width: `${(row.leadCount / maxCount) * 100}%` }} />
            </div>
            <span className="w-12 shrink-0 text-right text-slate-900">{row.leadCount}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

export function LeaderboardCard({ rows }: { rows: RepLeaderboardEntryDto[] }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <h2 className="text-sm font-medium text-slate-500">Rep leaderboard</h2>
      {rows.length === 0 ? (
        <p className="mt-3 text-sm text-slate-400">No opportunities owned by anyone yet.</p>
      ) : (
        <table className="mt-4 w-full text-left text-sm">
          <thead>
            <tr className="border-b border-slate-100 text-xs font-medium text-slate-400">
              <th className="pb-2 font-medium">Rep</th>
              <th className="pb-2 font-medium">Open</th>
              <th className="pb-2 font-medium">Won</th>
              <th className="pb-2 font-medium">Lost</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.ownerId} className="border-b border-slate-50 last:border-0">
                <td className="py-2 font-medium text-slate-900">{row.ownerName}</td>
                <td className="py-2 text-slate-600">
                  {row.openAmount.toLocaleString()} <span className="text-slate-400">&middot; {row.openCount}</span>
                </td>
                <td className="py-2 text-emerald-700">
                  {row.wonAmount.toLocaleString()} <span className="text-slate-400">&middot; {row.wonCount}</span>
                </td>
                <td className="py-2 text-slate-500">{row.lostCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
