import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listGiftCards } from "../../api/giftCards";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { GiftCardDto, GiftCardStatus, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<GiftCardStatus, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-700",
  REDEEMED: "bg-slate-100 text-slate-500",
  EXPIRED: "bg-amber-100 text-amber-700",
  CANCELLED: "bg-rose-100 text-rose-700",
};

export function GiftCardStatusBadge({ status }: { status: GiftCardStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

export default function GiftCardListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<GiftCardDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listGiftCards({ page, size: PAGE_SIZE, sort: "issuedAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load gift cards.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Gift Cards</h1>
          <p className="mt-1 text-sm text-slate-500">Prepaid balances issued to clients.</p>
        </div>
        <Link to="/gift-cards/new">
          <Button>Issue gift card</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Code</th>
              <th className="px-4 py-3 font-medium">Balance</th>
              <th className="px-4 py-3 font-medium">Issued</th>
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
                  No gift cards yet.
                </td>
              </tr>
            )}
            {result?.content.map((giftCard) => (
              <tr key={giftCard.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/gift-cards/${giftCard.id}`} className="font-medium text-slate-900 hover:underline">
                    {giftCard.code}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">
                  ${giftCard.currentBalance.toFixed(2)} / ${giftCard.initialBalance.toFixed(2)}
                </td>
                <td className="px-4 py-3 text-slate-600">{new Date(giftCard.issuedAt).toLocaleDateString()}</td>
                <td className="px-4 py-3">
                  <GiftCardStatusBadge status={giftCard.status} />
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
