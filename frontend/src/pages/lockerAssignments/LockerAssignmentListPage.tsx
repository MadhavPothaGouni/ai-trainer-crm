import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listLockerAssignments } from "../../api/lockerAssignments";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { LockerAssignmentDto, LockerAssignmentStatus, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<LockerAssignmentStatus, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-700",
  RETURNED: "bg-slate-100 text-slate-500",
  EXPIRED: "bg-rose-100 text-rose-700",
};

export function LockerAssignmentStatusBadge({ status }: { status: LockerAssignmentStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

export default function LockerAssignmentListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<LockerAssignmentDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listLockerAssignments({ page, size: PAGE_SIZE, sort: "assignedAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load locker assignments.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Locker Assignments</h1>
          <p className="mt-1 text-sm text-slate-500">Which client has which locker, and for how long.</p>
        </div>
        <Link to="/locker-assignments/new">
          <Button>New assignment</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Assigned</th>
              <th className="px-4 py-3 font-medium">Expires</th>
              <th className="px-4 py-3 font-medium">Status</th>
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
                  No locker assignments yet.
                </td>
              </tr>
            )}
            {result?.content.map((assignment) => (
              <tr key={assignment.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/locker-assignments/${assignment.id}`} className="font-medium text-slate-900 hover:underline">
                    {new Date(assignment.assignedAt).toLocaleDateString()}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{assignment.expiresAt ?? "—"}</td>
                <td className="px-4 py-3">
                  <LockerAssignmentStatusBadge status={assignment.status} />
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
