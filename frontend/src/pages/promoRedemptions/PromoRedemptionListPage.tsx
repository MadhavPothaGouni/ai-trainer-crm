import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listPromoRedemptions } from "../../api/promoRedemptions";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { PageResponse, PromoRedemptionDto } from "../../types/api";

const PAGE_SIZE = 20;

export default function PromoRedemptionListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<PromoRedemptionDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listPromoRedemptions({ page, size: PAGE_SIZE, sort: "redeemedAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load promo redemptions.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Promo Redemptions</h1>
          <p className="mt-1 text-sm text-slate-500">Every time a client has used a promo code.</p>
        </div>
        <Link to="/promo-redemptions/new">
          <Button>Record redemption</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Redeemed</th>
              <th className="px-4 py-3 font-medium">Amount discounted</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={2}>
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={2}>
                  No promo redemptions yet.
                </td>
              </tr>
            )}
            {result?.content.map((redemption) => (
              <tr key={redemption.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/promo-redemptions/${redemption.id}`} className="font-medium text-slate-900 hover:underline">
                    {new Date(redemption.redeemedAt).toLocaleString()}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">
                  {redemption.amountDiscounted != null ? `$${redemption.amountDiscounted.toFixed(2)}` : "—"}
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
