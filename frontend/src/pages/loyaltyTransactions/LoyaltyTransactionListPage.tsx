import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listLoyaltyTransactions } from "../../api/loyaltyTransactions";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { LoyaltyTransactionDto, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

export function LoyaltyPointsBadge({ points }: { points: number }) {
  const classes = points >= 0 ? "bg-emerald-100 text-emerald-700" : "bg-rose-100 text-rose-700";
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${classes}`}>{points >= 0 ? `+${points}` : points}</span>;
}

export default function LoyaltyTransactionListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<LoyaltyTransactionDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listLoyaltyTransactions({ page, size: PAGE_SIZE, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load loyalty transactions.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Loyalty Transactions</h1>
          <p className="mt-1 text-sm text-slate-500">Points earned and redeemed - a client's balance is always the live sum of their entries.</p>
        </div>
        <Link to="/loyalty-transactions/new">
          <Button>Log transaction</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Date</th>
              <th className="px-4 py-3 font-medium">Reason</th>
              <th className="px-4 py-3 font-medium">Points</th>
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
                  No loyalty transactions yet.
                </td>
              </tr>
            )}
            {result?.content.map((transaction) => (
              <tr key={transaction.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/loyalty-transactions/${transaction.id}`} className="font-medium text-slate-900 hover:underline">
                    {new Date(transaction.createdAt).toLocaleString()}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{transaction.reason}</td>
                <td className="px-4 py-3">
                  <LoyaltyPointsBadge points={transaction.points} />
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
