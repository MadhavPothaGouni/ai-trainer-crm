import { useEffect, useState } from "react";
import { myGoals } from "../../api/salesGoals";
import { Alert } from "../../components/ui/Alert";
import { ProgressBar } from "../../components/ui/ProgressBar";
import { ApiError } from "../../lib/apiClient";
import type { SalesGoalDto, SalesGoalMetric } from "../../types/api";

const METRIC_LABELS: Record<SalesGoalMetric, string> = { REVENUE: "Revenue", DEAL_COUNT: "Deal count" };

/** No SALES_GOAL:*:ORGANIZATION needed to view this page - GET /sales-goals/mine is self-scoped on the backend, the same shape the notification inbox uses, so every teammate can see their own quota regardless of role. */
export default function MyGoalsPage() {
  const [goals, setGoals] = useState<SalesGoalDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    myGoals()
      .then(setGoals)
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load your goals."));
  }, []);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">My Goals</h1>
        <p className="mt-1 text-sm text-slate-500">
          Quotas assigned to you individually or through your team, with progress computed live from your won deals.
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      {goals === null && !error && <p className="text-sm text-slate-400">Loading...</p>}

      {goals !== null && goals.length === 0 && (
        <p className="rounded-lg border border-dashed border-slate-200 px-4 py-6 text-center text-sm text-slate-400">
          No goals assigned to you right now.
        </p>
      )}

      <div className="flex flex-col gap-3">
        {goals?.map((goal) => (
          <div key={goal.id} className="flex flex-col gap-2 rounded-lg border border-slate-200 bg-white p-4">
            <div className="flex items-center justify-between">
              <div>
                <span className="font-medium text-slate-900">{goal.name}</span>
                <span className="ml-2 text-xs text-slate-400">
                  {METRIC_LABELS[goal.metric]} · {goal.periodStart} to {goal.periodEnd}
                  {goal.teamId ? " · team goal" : ""}
                </span>
              </div>
              <span className="text-sm font-medium text-slate-600">
                {goal.actualValue} / {goal.targetValue}
              </span>
            </div>
            <ProgressBar percent={goal.percentComplete} />
          </div>
        ))}
      </div>
    </div>
  );
}
