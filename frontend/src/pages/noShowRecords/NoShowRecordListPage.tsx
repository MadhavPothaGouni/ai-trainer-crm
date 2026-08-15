import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listNoShowRecords } from "../../api/noShowRecords";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { NoShowRecordDto, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

export function NoShowRecordWaivedBadge({ waived }: { waived: boolean }) {
  const classes = waived ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-500";
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${classes}`}>{waived ? "Waived" : "Not waived"}</span>;
}

export default function NoShowRecordListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<NoShowRecordDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listNoShowRecords({ page, size: PAGE_SIZE, sort: "occurredAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load no-show records.");
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
          <h1 className="text-2xl font-semibold text-slate-900">No-Show Records</h1>
          <p className="mt-1 text-sm text-slate-500">Missed bookings, with an optional fee that can be waived.</p>
        </div>
        <Link to="/no-show-records/new">
          <Button>Log no-show</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Occurred</th>
              <th className="px-4 py-3 font-medium">Type</th>
              <th className="px-4 py-3 font-medium">Fee</th>
              <th className="px-4 py-3 font-medium">Waived</th>
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
                  No no-show records yet.
                </td>
              </tr>
            )}
            {result?.content.map((record) => (
              <tr key={record.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/no-show-records/${record.id}`} className="font-medium text-slate-900 hover:underline">
                    {new Date(record.occurredAt).toLocaleString()}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{record.relatedType}</td>
                <td className="px-4 py-3 text-slate-600">{record.feeAmount != null ? `$${record.feeAmount.toFixed(2)}` : "—"}</td>
                <td className="px-4 py-3">
                  <NoShowRecordWaivedBadge waived={record.waived} />
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
