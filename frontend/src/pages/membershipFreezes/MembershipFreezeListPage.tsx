import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listMembershipFreezes } from "../../api/membershipFreezes";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { MembershipFreezeDto, MembershipFreezeStatus, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<MembershipFreezeStatus, string> = {
  REQUESTED: "bg-slate-100 text-slate-500",
  ACTIVE: "bg-amber-100 text-amber-700",
  ENDED: "bg-emerald-100 text-emerald-700",
};

export function MembershipFreezeStatusBadge({ status }: { status: MembershipFreezeStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

export default function MembershipFreezeListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<MembershipFreezeDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listMembershipFreezes({ page, size: PAGE_SIZE, sort: "freezeStart,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load membership freezes.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Membership Freezes</h1>
          <p className="mt-1 text-sm text-slate-500">Clients pausing an active membership for a date range.</p>
        </div>
        <Link to="/membership-freezes/new">
          <Button>Request freeze</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Date range</th>
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
                  No membership freezes yet.
                </td>
              </tr>
            )}
            {result?.content.map((freeze) => (
              <tr key={freeze.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/membership-freezes/${freeze.id}`} className="font-medium text-slate-900 hover:underline">
                    {freeze.freezeStart} &ndash; {freeze.freezeEnd}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{freeze.reason ?? "—"}</td>
                <td className="px-4 py-3">
                  <MembershipFreezeStatusBadge status={freeze.status} />
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
