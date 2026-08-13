import { useEffect, useState } from "react";
import { myCommissionRecords } from "../../api/commission";
import { Alert } from "../../components/ui/Alert";
import { ApiError } from "../../lib/apiClient";
import type { CommissionRecordDto, CommissionRecordStatus } from "../../types/api";

const STATUS_STYLES: Record<CommissionRecordStatus, string> = {
  PENDING: "bg-amber-50 text-amber-700",
  APPROVED: "bg-sky-50 text-sky-700",
  PAID: "bg-emerald-50 text-emerald-700",
};

/** No COMMISSION_RECORD:*:ORGANIZATION needed to view this page - GET /commission-records/mine is self-scoped on the backend, the same shape My Goals and the notification inbox already use, so every rep can see what they've earned regardless of role. */
export default function MyCommissionsPage() {
  const [records, setRecords] = useState<CommissionRecordDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    myCommissionRecords()
      .then(setRecords)
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load your commissions."));
  }, []);

  const totalEarned = records?.reduce((sum, r) => sum + r.commissionAmount, 0) ?? 0;
  const totalPaid = records?.filter((r) => r.status === "PAID").reduce((sum, r) => sum + r.commissionAmount, 0) ?? 0;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">My Commissions</h1>
        <p className="mt-1 text-sm text-slate-500">
          One record per Closed Won deal, created automatically - the amount here is locked in at the moment your deal
          closed and never changes even if your plan's rate changes later.
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      {records && records.length > 0 && (
        <div className="grid grid-cols-2 gap-4 rounded-lg border border-slate-200 bg-white p-5 sm:grid-cols-3">
          <Stat label="Deals" value={String(records.length)} />
          <Stat label="Total earned" value={formatCurrency(totalEarned)} />
          <Stat label="Paid out" value={formatCurrency(totalPaid)} accent="text-emerald-700" />
        </div>
      )}

      {records === null && !error && <p className="text-sm text-slate-400">Loading...</p>}

      {records !== null && records.length === 0 && (
        <p className="rounded-lg border border-dashed border-slate-200 px-4 py-6 text-center text-sm text-slate-400">
          No commissions yet - close a deal to earn your first one.
        </p>
      )}

      <div className="flex flex-col gap-3">
        {records?.map((record) => (
          <div key={record.id} className="flex items-center justify-between rounded-lg border border-slate-200 bg-white p-4">
            <div>
              <span className="font-medium text-slate-900">{formatCurrency(record.commissionAmount)}</span>
              <span className="ml-2 text-xs text-slate-400">
                on a {formatCurrency(record.dealAmount)} deal ·{" "}
                {record.rateType === "PERCENTAGE" ? `${record.rate}%` : `${formatCurrency(record.rate)} flat`} ·{" "}
                {new Date(record.earnedAt).toLocaleDateString()}
              </span>
            </div>
            <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[record.status]}`}>
              {record.status}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function Stat({ label, value, accent }: { label: string; value: string; accent?: string }) {
  return (
    <div>
      <p className="text-xs font-medium uppercase tracking-wide text-slate-400">{label}</p>
      <p className={`mt-0.5 text-lg font-semibold ${accent ?? "text-slate-900"}`}>{value}</p>
    </div>
  );
}

function formatCurrency(value: number): string {
  return value.toLocaleString(undefined, { style: "currency", currency: "USD" });
}
