import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listNutritionLogs } from "../../api/nutritionLogs";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { NutritionLogDto, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

export default function NutritionLogListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<NutritionLogDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listNutritionLogs({ page, size: PAGE_SIZE, sort: "loggedAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load nutrition logs.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Nutrition Logs</h1>
          <p className="mt-1 text-sm text-slate-500">Meals clients have logged - calories and macros, self-reported.</p>
        </div>
        <Link to="/nutrition-logs/new">
          <Button>Log a meal</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Logged</th>
              <th className="px-4 py-3 font-medium">Meal</th>
              <th className="px-4 py-3 font-medium">Calories</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={3}>
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={3}>
                  No nutrition logs yet.
                </td>
              </tr>
            )}
            {result?.content.map((log) => (
              <tr key={log.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/nutrition-logs/${log.id}`} className="font-medium text-slate-900 hover:underline">
                    {new Date(log.loggedAt).toLocaleString()}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{log.mealType}</td>
                <td className="px-4 py-3 text-slate-600">{log.calories != null ? `${log.calories} kcal` : "—"}</td>
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
