import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listRefundRecords } from "../../api/refundRecords";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { PageResponse, RefundRecordDto, RefundRecordStatus } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<RefundRecordStatus, string> = {
  REQUESTED: "bg-slate-100 text-slate-500",
  APPROVED: "bg-blue-100 text-blue-700",
  PROCESSED: "bg-emerald-100 text-emerald-700",
};

export function RefundRecordStatusBadge({ status }: { status: RefundRecordStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

export default function RefundRecordListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<RefundRecordDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listRefundRecords({ page, size: PAGE_SIZE, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load refund records.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Refund Records</h1>
          <p className="mt-1 text-sm text-slate-500">Refunds issued against payments - a payment's refunds can never total more than it did.</p>
        </div>
        <Link to="/refund-records/new">
          <Button>New refund</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Amount</th>
              <th className="px-4 py-3 font-medium">Reason</th>
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
                  No refund records yet.
                </td>
              </tr>
            )}
            {result?.content.map((refund) => (
              <tr key={refund.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/refund-records/${refund.id}`} className="font-medium text-slate-900 hover:underline">
                    ${refund.amount.toFixed(2)}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{refund.reason.replace("_", " ")}</td>
                <td className="px-4 py-3">
                  <RefundRecordStatusBadge status={refund.status} />
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
