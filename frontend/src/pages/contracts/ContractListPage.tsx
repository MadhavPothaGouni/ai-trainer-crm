import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listContracts } from "../../api/contracts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { ContractDto, ContractStatus, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<ContractStatus, string> = {
  DRAFT: "bg-slate-100 text-slate-700",
  ACTIVE: "bg-emerald-100 text-emerald-700",
  EXPIRED: "bg-amber-100 text-amber-700",
  TERMINATED: "bg-red-100 text-red-700",
  RENEWED: "bg-blue-100 text-blue-700",
};

export function ContractStatusBadge({ status }: { status: ContractStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

export default function ContractListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<ContractDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listContracts({ page, size: PAGE_SIZE, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load contracts.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Contracts</h1>
          <p className="mt-1 text-sm text-slate-500">The ongoing agreement with a customer, tracked after a deal closes.</p>
        </div>
        <Link to="/contracts/new">
          <Button>New contract</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Number</th>
              <th className="px-4 py-3 font-medium">Title</th>
              <th className="px-4 py-3 font-medium">End date</th>
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
                  No contracts yet.
                </td>
              </tr>
            )}
            {result?.content.map((contract) => (
              <tr key={contract.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/contracts/${contract.id}`} className="font-medium text-slate-900 hover:underline">
                    {contract.contractNumber}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{contract.title}</td>
                <td className="px-4 py-3 text-slate-600">{contract.endDate}</td>
                <td className="px-4 py-3">
                  <ContractStatusBadge status={contract.status} />
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
