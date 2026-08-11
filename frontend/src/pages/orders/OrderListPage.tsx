import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listOrders } from "../../api/orders";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { OrderDto, OrderStatus, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<OrderStatus, string> = {
  DRAFT: "bg-slate-100 text-slate-700",
  CONFIRMED: "bg-blue-100 text-blue-700",
  FULFILLED: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-red-100 text-red-700",
};

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

export default function OrderListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<OrderDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listOrders({ page, size: PAGE_SIZE, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load orders.");
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
          <h1 className="text-2xl font-semibold text-slate-900">Orders</h1>
          <p className="mt-1 text-sm text-slate-500">Confirmed sales, optionally converted from a quote.</p>
        </div>
        <Link to="/orders/new">
          <Button>New order</Button>
        </Link>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Order #</th>
              <th className="px-4 py-3 font-medium">Total</th>
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
                  No orders yet.
                </td>
              </tr>
            )}
            {result?.content.map((order) => (
              <tr key={order.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/orders/${order.id}`} className="font-medium text-slate-900 hover:underline">
                    {order.orderNumber}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">
                  {order.totalAmount.toLocaleString()} {order.currency ?? ""}
                </td>
                <td className="px-4 py-3">
                  <OrderStatusBadge status={order.status} />
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
