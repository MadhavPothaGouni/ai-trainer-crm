import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listInvoices } from "../../api/invoices";
import { Alert } from "../../components/ui/Alert";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { InvoiceDto, InvoiceStatus, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_CLASSES: Record<InvoiceStatus, string> = {
  DRAFT: "bg-slate-100 text-slate-700",
  SENT: "bg-blue-100 text-blue-700",
  PAID: "bg-emerald-100 text-emerald-700",
  OVERDUE: "bg-amber-100 text-amber-700",
  VOID: "bg-red-100 text-red-700",
};

export function InvoiceStatusBadge({ status }: { status: InvoiceStatus }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CLASSES[status]}`}>{status}</span>;
}

/** No "New invoice" button here, deliberately - every invoice is generated from a confirmed order (see the "Generate an invoice" card on OrderDetailPage), not created standalone. */
export default function InvoiceListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<InvoiceDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    listInvoices({ page, size: PAGE_SIZE, sort: "createdAt,desc" })
      .then((res) => {
        if (!cancelled) setResult(res);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load invoices.");
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
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Invoices</h1>
        <p className="mt-1 text-sm text-slate-500">Bills generated from confirmed orders. Start one from an order's detail page.</p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Invoice #</th>
              <th className="px-4 py-3 font-medium">Total</th>
              <th className="px-4 py-3 font-medium">Balance due</th>
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
                  No invoices yet.
                </td>
              </tr>
            )}
            {result?.content.map((invoice) => (
              <tr key={invoice.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/invoices/${invoice.id}`} className="font-medium text-slate-900 hover:underline">
                    {invoice.invoiceNumber}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600">
                  {invoice.totalAmount.toLocaleString()} {invoice.currency ?? ""}
                </td>
                <td className="px-4 py-3 text-slate-600">
                  {invoice.balanceDue.toLocaleString()} {invoice.currency ?? ""}
                </td>
                <td className="px-4 py-3">
                  <InvoiceStatusBadge status={invoice.status} />
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
