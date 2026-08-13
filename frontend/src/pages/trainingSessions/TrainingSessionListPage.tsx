import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listTrainingSessions } from "../../api/trainingSessions";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { PageResponse, TrainingSessionDto, TrainingSessionStatus } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<TrainingSessionStatus, string> = {
  SCHEDULED: "bg-blue-100 text-blue-700",
  COMPLETED: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-slate-100 text-slate-500",
  NO_SHOW: "bg-red-100 text-red-700",
};

export function TrainingSessionStatusBadge({ status }: { status: TrainingSessionStatus }) {
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>
      {status.replace("_", " ")}
    </span>
  );
}

export default function TrainingSessionListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<TrainingSessionDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listTrainingSessions({ page, size: PAGE_SIZE, sort: "startedAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load training sessions.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Training Sessions</h1>
          <p className="mt-1 text-sm text-slate-500">The post-session record of what actually happened with a client.</p>
        </div>
        <Link to="/training-sessions/new">
          <Button>Log session</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Started</th>
              <th className="px-4 py-3 font-medium">Type</th>
              <th className="px-4 py-3 font-medium">Focus area</th>
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
                  No training sessions logged yet.
                </td>
              </tr>
            )}
            {result?.content.map((session) => (
              <tr key={session.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/training-sessions/${session.id}`} className="font-medium text-slate-900 hover:underline">
                    {new Date(session.startedAt).toLocaleString()}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{session.sessionType.replace("_", " ")}</td>
                <td className="px-4 py-3 text-slate-600">{session.focusArea ?? "—"}</td>
                <td className="px-4 py-3">
                  <TrainingSessionStatusBadge status={session.status} />
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
