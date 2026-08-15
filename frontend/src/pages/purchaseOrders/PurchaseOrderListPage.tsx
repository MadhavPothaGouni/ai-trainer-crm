import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listPurchaseOrders } from "../../api/purchaseOrders";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { PageResponse, PurchaseOrderDto, PurchaseOrderStatus } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<PurchaseOrderStatus, string> = {
  DRAFT: "bg-slate-100 text-slate-500",
  ORDERED: "bg-blue-100 text-blue-700",
  RECEIVED: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-rose-100 text-rose-700",
};

export function PurchaseOrderStatusBadge({ status }: { status: PurchaseOrderStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

export default function PurchaseOrderListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<PurchaseOrderDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listPurchaseOrders({ page, size: PAGE_SIZE, sort: "orderDate,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load purchase orders.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Purchase Orders</h1>
          <p className="mt-1 text-sm text-slate-500">Orders placed with vendors, tracked from draft through delivery.</p>
        </div>
        <Link to="/purchase-orders/new">
          <Button>New order</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Order date</th>
              <th className="px-4 py-3 font-medium">Total</th>
              <th className="px-4 py-3 font-medium">Expected delivery</th>
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
                  No purchase orders yet.
                </td>
              </tr>
            )}
            {result?.content.map((order) => (
              <tr key={order.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/purchase-orders/${order.id}`} className="font-medium text-slate-900 hover:underline">
                    {order.orderDate}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">{order.totalAmount != null ? `$${order.totalAmount.toFixed(2)}` : "—"}</td>
                <td className="px-4 py-3 text-slate-600">{order.expectedDeliveryDate ?? "—"}</td>
                <td className="px-4 py-3">
                  <PurchaseOrderStatusBadge status={order.status} />
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
