import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listClientCheckIns } from "../../api/clientCheckIns";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { ClientCheckInDto, ClientCheckInStatus, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<ClientCheckInStatus, string> = {
  CHECKED_IN: "bg-emerald-100 text-emerald-700",
  CHECKED_OUT: "bg-slate-100 text-slate-500",
};

export function ClientCheckInStatusBadge({ status }: { status: ClientCheckInStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status.replace("_", " ")}</span>;
}

export default function ClientCheckInListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<ClientCheckInDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listClientCheckIns({ page, size: PAGE_SIZE, sort: "checkedInAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load check-ins.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Client Check-Ins</h1>
          <p className="mt-1 text-sm text-slate-500">Who's in the building - front-desk, kiosk, and app check-ins.</p>
        </div>
        <Link to="/client-check-ins/new">
          <Button>Check in a client</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Checked in</th>
              <th className="px-4 py-3 font-medium">Method</th>
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
                  No check-ins yet.
                </td>
              </tr>
            )}
            {result?.content.map((checkIn) => (
              <tr key={checkIn.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/client-check-ins/${checkIn.id}`} className="font-medium text-slate-900 hover:underline">
                    {new Date(checkIn.checkedInAt).toLocaleString()}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{checkIn.method.replace("_", " ")}</td>
                <td className="px-4 py-3">
                  <ClientCheckInStatusBadge status={checkIn.status} />
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
