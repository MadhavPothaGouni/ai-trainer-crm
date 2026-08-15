import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listReferrals } from "../../api/referrals";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { PageResponse, ReferralDto, ReferralStatus } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<ReferralStatus, string> = {
  PENDING: "bg-slate-100 text-slate-500",
  CONTACTED: "bg-blue-100 text-blue-700",
  CONVERTED: "bg-emerald-100 text-emerald-700",
  DECLINED: "bg-rose-100 text-rose-700",
};

export function ReferralStatusBadge({ status }: { status: ReferralStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

export default function ReferralListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<ReferralDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listReferrals({ page, size: PAGE_SIZE, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load referrals.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Referrals</h1>
          <p className="mt-1 text-sm text-slate-500">Clients referring people they know - work the lead, then optionally issue a reward.</p>
        </div>
        <Link to="/referrals/new">
          <Button>New referral</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Referred</th>
              <th className="px-4 py-3 font-medium">Email</th>
              <th className="px-4 py-3 font-medium">Reward</th>
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
                  No referrals yet.
                </td>
              </tr>
            )}
            {result?.content.map((referral) => (
              <tr key={referral.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/referrals/${referral.id}`} className="font-medium text-slate-900 hover:underline">
                    {referral.referredName}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{referral.referredEmail ?? "—"}</td>
                <td className="px-4 py-3 text-slate-600">
                  {referral.rewardAmount != null ? `$${referral.rewardAmount.toFixed(2)}${referral.rewardIssuedAt ? " (issued)" : ""}` : "—"}
                </td>
                <td className="px-4 py-3">
                  <ReferralStatusBadge status={referral.status} />
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
