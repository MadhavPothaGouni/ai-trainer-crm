import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteAccount, getAccount } from "../../api/accounts";
import { ActivityTimeline } from "../../components/activities/ActivityTimeline";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { ApiError } from "../../lib/apiClient";
import type { AccountDto } from "../../types/api";

export default function AccountDetailPage() {
  const { accountId } = useParams<{ accountId: string }>();
  const navigate = useNavigate();
  const [account, setAccount] = useState<AccountDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    if (!accountId) return;
    let cancelled = false;
    getAccount(accountId)
      .then((data) => {
        if (!cancelled) setAccount(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this account.");
      });
    return () => {
      cancelled = true;
    };
  }, [accountId]);

  async function handleDelete() {
    if (!accountId || !window.confirm("Delete this account? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteAccount(accountId);
      navigate("/accounts");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this account.");
      setIsDeleting(false);
    }
  }

  if (error && !account) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!account) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const billingAddress = [account.billingStreet, account.billingCity, account.billingState, account.billingPostalCode, account.billingCountry]
    .filter(Boolean)
    .join(", ");

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/accounts" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Accounts
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{account.name}</h1>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Overview</h2>
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <Row label="Industry" value={account.industry} />
            <Row label="Website" value={account.website} />
            <Row label="Phone" value={account.phone} />
            <Row label="Annual revenue" value={account.annualRevenue?.toLocaleString()} />
            <Row label="Employees" value={account.employeeCount} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Billing address</h2>
          <p className="mt-3 text-sm text-slate-900">{billingAddress || "—"}</p>
        </div>
      </div>

      {account.description && (
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Description</h2>
          <p className="mt-3 whitespace-pre-wrap text-sm text-slate-900">{account.description}</p>
        </div>
      )}

      <ActivityTimeline relatedToType="ACCOUNT" relatedToId={account.id} />
    </div>
  );
}

function Row({ label, value }: { label: string; value: string | number | null | undefined }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right text-slate-900">{value ?? "—"}</dd>
    </div>
  );
}
