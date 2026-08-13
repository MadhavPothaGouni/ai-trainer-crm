import { useEffect, useState } from "react";
import { listCommissionRecords, updateCommissionRecordStatus } from "../../api/commission";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { ApiError } from "../../lib/apiClient";
import type { CommissionRecordDto, CommissionRecordStatus, PageResponse, UserDto } from "../../types/api";

const PAGE_SIZE = 20;

const STATUS_STYLES: Record<CommissionRecordStatus, string> = {
  PENDING: "bg-amber-50 text-amber-700",
  APPROVED: "bg-sky-50 text-sky-700",
  PAID: "bg-emerald-50 text-emerald-700",
};

/** No CREATE/UPDATE/DELETE anywhere on this page - CommissionEngine is the only writer of a record's core fields. The only action available here is walking status forward: PENDING -> APPROVED -> PAID, never backward, never skipping. */
export default function CommissionRecordListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<CommissionRecordDto> | null>(null);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [actioningId, setActioningId] = useState<string | null>(null);

  function reload() {
    setIsLoading(true);
    listCommissionRecords({ page, size: PAGE_SIZE, sort: "earnedAt,desc" })
      .then((res) => setResult(res))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load commission records."))
      .finally(() => setIsLoading(false));
  }

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  useEffect(() => {
    listUsers({ size: 200 })
      .then((res) => setUsers(res.content))
      .catch(() => undefined);
  }, []);

  function ownerLabel(record: CommissionRecordDto): string {
    return users.find((u) => u.id === record.ownerUserId)?.fullName ?? "Unknown teammate";
  }

  async function advance(record: CommissionRecordDto, status: CommissionRecordStatus) {
    setActioningId(record.id);
    try {
      const updated = await updateCommissionRecordStatus(record.id, { status });
      setResult((prev) =>
        prev ? { ...prev, content: prev.content.map((r) => (r.id === updated.id ? updated : r)) } : prev,
      );
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this commission record.");
    } finally {
      setActioningId(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Commission Records</h1>
        <p className="mt-1 text-sm text-slate-500">
          Created automatically the moment a rep's Opportunity is marked Closed Won - dealAmount, rate, and commissionAmount
          are frozen at that moment and never recomputed, even if the underlying plan changes later. Walk each record from
          Pending to Approved to Paid as it works through payroll.
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3">Rep</th>
              <th className="px-4 py-3">Deal amount</th>
              <th className="px-4 py-3">Rate</th>
              <th className="px-4 py-3">Commission</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Earned</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td colSpan={7} className="px-4 py-6 text-center text-sm text-slate-400">
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-6 text-center text-sm text-slate-400">
                  No commission records yet.
                </td>
              </tr>
            )}
            {result?.content.map((record) => (
              <tr key={record.id}>
                <td className="px-4 py-3 text-slate-900">{ownerLabel(record)}</td>
                <td className="px-4 py-3">{formatCurrency(record.dealAmount)}</td>
                <td className="px-4 py-3">{record.rateType === "PERCENTAGE" ? `${record.rate}%` : formatCurrency(record.rate)}</td>
                <td className="px-4 py-3 font-medium text-slate-900">{formatCurrency(record.commissionAmount)}</td>
                <td className="px-4 py-3">
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[record.status]}`}>
                    {record.status}
                  </span>
                </td>
                <td className="px-4 py-3 text-slate-500">{new Date(record.earnedAt).toLocaleDateString()}</td>
                <td className="px-4 py-3 text-right">
                  {record.status === "PENDING" && (
                    <Button
                      variant="secondary"
                      isLoading={actioningId === record.id}
                      onClick={() => void advance(record, "APPROVED")}
                    >
                      Approve
                    </Button>
                  )}
                  {record.status === "APPROVED" && (
                    <Button
                      variant="secondary"
                      isLoading={actioningId === record.id}
                      onClick={() => void advance(record, "PAID")}
                    >
                      Mark paid
                    </Button>
                  )}
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

function formatCurrency(value: number): string {
  return value.toLocaleString(undefined, { style: "currency", currency: "USD" });
}
