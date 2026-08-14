import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listNutritionPlans } from "../../api/nutritionPlans";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { NutritionPlanDto, NutritionPlanStatus, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<NutritionPlanStatus, string> = {
  DRAFT: "bg-slate-100 text-slate-500",
  ACTIVE: "bg-emerald-100 text-emerald-700",
  COMPLETED: "bg-blue-100 text-blue-700",
  ARCHIVED: "bg-amber-100 text-amber-700",
};

export function NutritionPlanStatusBadge({ status }: { status: NutritionPlanStatus }) {
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>
  );
}

function formatCalories(dailyCalorieTarget: number | null): string {
  return dailyCalorieTarget != null ? `${dailyCalorieTarget} kcal` : "—";
}

function formatDateRange(startDate: string | null, endDate: string | null): string {
  if (!startDate && !endDate) return "—";
  return `${startDate ?? "?"} → ${endDate ?? "?"}`;
}

export default function NutritionPlanListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<NutritionPlanDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listNutritionPlans({ page, size: PAGE_SIZE, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load nutrition plans.");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Nutrition Plans</h1>
          <p className="mt-1 text-sm text-slate-500">Dietary and macro guidance you're tracking for a client.</p>
        </div>
        <Link to="/nutrition-plans/new">
          <Button>New plan</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Title</th>
              <th className="px-4 py-3 font-medium">Calorie target</th>
              <th className="px-4 py-3 font-medium">Dates</th>
              <th className="px-4 py-3 font-medium">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={4}>
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={4}>
                  No nutrition plans yet.
                </td>
              </tr>
            )}
            {result?.content.map((plan) => (
              <tr key={plan.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/nutrition-plans/${plan.id}`} className="font-medium text-slate-900 hover:underline">
                    {plan.title}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{formatCalories(plan.dailyCalorieTarget)}</td>
                <td className="px-4 py-3 text-slate-600">{formatDateRange(plan.startDate, plan.endDate)}</td>
                <td className="px-4 py-3">
                  <NutritionPlanStatusBadge status={plan.status} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {result && (
        <Pagination
          pageNumber={result.pageNumber}
          totalPages={result.totalPages}
          first={result.first}
          last={result.last}
          totalElements={result.totalElements}
          onPageChange={setPage}
        />
      )}
    </div>
  );
}
