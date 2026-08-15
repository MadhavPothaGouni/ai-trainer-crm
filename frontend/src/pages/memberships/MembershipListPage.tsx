import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listMemberships } from "../../api/memberships";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { MembershipDto, MembershipStatus, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<MembershipStatus, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-700",
  PAUSED: "bg-amber-100 text-amber-700",
  CANCELLED: "bg-slate-100 text-slate-500",
  EXPIRED: "bg-rose-100 text-rose-700",
};

export function MembershipStatusBadge({ status }: { status: MembershipStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

export default function MembershipListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<MembershipDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listMemberships({ page, size: PAGE_SIZE, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load memberships.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Memberships</h1>
          <p className="mt-1 text-sm text-slate-500">Active recurring billing relationships between clients and a membership plan.</p>
        </div>
        <Link to="/memberships/new">
          <Button>New membership</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Start date</th>
              <th className="px-4 py-3 font-medium">Next billing</th>
              <th className="px-4 py-3 font-medium">Price</th>
              <th className="px-4 py-3 font-medium">Credits left</th>
              <th className="px-4 py-3 font-medium">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={5}>
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={5}>
                  No memberships yet.
                </td>
              </tr>
            )}
            {result?.content.map((membership) => (
              <tr key={membership.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/memberships/${membership.id}`} className="font-medium text-slate-900 hover:underline">
                    {membership.startDate}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{membership.nextBillingDate ?? "—"}</td>
                <td className="px-4 py-3 text-slate-600">{membership.billingCyclePrice.toLocaleString()}</td>
                <td className="px-4 py-3 text-slate-600">{membership.remainingCredits ?? "Unlimited"}</td>
                <td className="px-4 py-3">
                  <MembershipStatusBadge status={membership.status} />
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
