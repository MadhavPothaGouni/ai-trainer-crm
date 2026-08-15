import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listClassWaitlists } from "../../api/classWaitlists";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { ClassWaitlistDto, ClassWaitlistStatus, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<ClassWaitlistStatus, string> = {
  WAITING: "bg-slate-100 text-slate-500",
  NOTIFIED: "bg-amber-100 text-amber-700",
  CONVERTED: "bg-emerald-100 text-emerald-700",
  EXPIRED: "bg-rose-100 text-rose-700",
};

export function ClassWaitlistStatusBadge({ status }: { status: ClassWaitlistStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

export default function ClassWaitlistListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<ClassWaitlistDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listClassWaitlists({ page, size: PAGE_SIZE, sort: "position,asc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load class waitlists.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Class Waitlists</h1>
          <p className="mt-1 text-sm text-slate-500">Clients queued for a spot when a class session is full.</p>
        </div>
        <Link to="/class-waitlists/new">
          <Button>Add to waitlist</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Position</th>
              <th className="px-4 py-3 font-medium">Class session</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Notified</th>
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
                  No waitlist entries yet.
                </td>
              </tr>
            )}
            {result?.content.map((entry) => (
              <tr key={entry.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/class-waitlists/${entry.id}`} className="font-medium text-slate-900 hover:underline">
                    #{entry.position}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{entry.classSessionId.slice(0, 8)}&hellip;</td>
                <td className="px-4 py-3">
                  <ClassWaitlistStatusBadge status={entry.status} />
                </td>
                <td className="px-4 py-3 text-slate-600">{entry.notifiedAt ? new Date(entry.notifiedAt).toLocaleString() : "—"}</td>
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
