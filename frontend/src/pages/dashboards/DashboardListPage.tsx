import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listDashboards } from "../../api/dashboards";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { DashboardDto, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

export default function DashboardListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<DashboardDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listDashboards({ page, size: PAGE_SIZE, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load dashboards.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Dashboards</h1>
          <p className="mt-1 text-sm text-slate-500">Saved widget layouts over the pipeline/funnel/leaderboard reports.</p>
        </div>
        <Link to="/dashboards/new">
          <Button>New dashboard</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Description</th>
              <th className="px-4 py-3 font-medium"></th>
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
                  No dashboards yet.
                </td>
              </tr>
            )}
            {result?.content.map((dashboard) => (
              <tr key={dashboard.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/dashboards/${dashboard.id}`} className="font-medium text-slate-900 hover:underline">
                    {dashboard.name}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{dashboard.description ?? "—"}</td>
                <td className="px-4 py-3 text-right">
                  {dashboard.isDefault && (
                    <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">
                      Default
                    </span>
                  )}
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
