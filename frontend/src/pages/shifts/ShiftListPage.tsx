import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listShifts } from "../../api/shifts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { PageResponse, ShiftDto, ShiftStatus } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<ShiftStatus, string> = {
  SCHEDULED: "bg-emerald-100 text-emerald-700",
  IN_PROGRESS: "bg-blue-100 text-blue-700",
  COMPLETED: "bg-slate-100 text-slate-500",
  MISSED: "bg-amber-100 text-amber-700",
  CANCELLED: "bg-rose-100 text-rose-700",
};

export function ShiftStatusBadge({ status }: { status: ShiftStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status.replace("_", " ")}</span>;
}

export default function ShiftListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<ShiftDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listShifts({ page, size: PAGE_SIZE, sort: "startsAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load shifts.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Shifts</h1>
          <p className="mt-1 text-sm text-slate-500">Scheduled staff shifts, with clock-in/out tracked as status changes.</p>
        </div>
        <Link to="/shifts/new">
          <Button>Schedule shift</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Date</th>
              <th className="px-4 py-3 font-medium">Starts</th>
              <th className="px-4 py-3 font-medium">Ends</th>
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
                  No shifts scheduled yet.
                </td>
              </tr>
            )}
            {result?.content.map((shift) => (
              <tr key={shift.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/shifts/${shift.id}`} className="font-medium text-slate-900 hover:underline">
                    {shift.shiftDate}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{new Date(shift.startsAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}</td>
                <td className="px-4 py-3 text-slate-600">{new Date(shift.endsAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}</td>
                <td className="px-4 py-3">
                  <ShiftStatusBadge status={shift.status} />
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
