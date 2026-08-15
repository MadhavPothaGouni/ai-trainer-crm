import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { getContact } from "../../api/contacts";
import { deleteLoyaltyTransaction, getLoyaltyBalance, getLoyaltyTransaction, updateLoyaltyTransaction } from "../../api/loyaltyTransactions";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateLoyaltyTransactionSchema, type UpdateLoyaltyTransactionFormValues } from "../../lib/validation";
import { LOYALTY_TRANSACTION_REASONS, type ContactDto, type LoyaltyTransactionDto, type LoyaltyTransactionReason } from "../../types/api";
import { LoyaltyPointsBadge } from "./LoyaltyTransactionListPage";

export default function LoyaltyTransactionDetailPage() {
  const { loyaltyTransactionId } = useParams<{ loyaltyTransactionId: string }>();
  const navigate = useNavigate();
  const [transaction, setTransaction] = useState<LoyaltyTransactionDto | null>(null);
  const [contact, setContact] = useState<ContactDto | null>(null);
  const [balance, setBalance] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateLoyaltyTransactionFormValues>({ resolver: zodResolver(updateLoyaltyTransactionSchema) });

  useEffect(() => {
    if (!loyaltyTransactionId) return;
    let cancelled = false;
    getLoyaltyTransaction(loyaltyTransactionId)
      .then((data) => {
        if (cancelled) return;
        setTransaction(data);
        reset({ points: String(data.points), reason: data.reason, notes: data.notes ?? "" });
        getContact(data.contactId).then((c) => {
          if (cancelled) return;
          setContact(c);
          getLoyaltyBalance(c.id)
            .then((result) => {
              if (!cancelled) setBalance(result.balance);
            })
            .catch(() => undefined);
        }).catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this loyalty transaction.");
      });
    return () => {
      cancelled = true;
    };
  }, [loyaltyTransactionId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!loyaltyTransactionId) return;
    setFormError(null);
    try {
      const updated = await updateLoyaltyTransaction(loyaltyTransactionId, {
        points: Number(values.points),
        reason: values.reason as LoyaltyTransactionReason,
        notes: blankToUndefined(values.notes),
      });
      setTransaction(updated);
      if (contact) {
        getLoyaltyBalance(contact.id)
          .then((result) => setBalance(result.balance))
          .catch(() => undefined);
      }
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleDelete() {
    if (!loyaltyTransactionId || !window.confirm("Delete this loyalty transaction? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteLoyaltyTransaction(loyaltyTransactionId);
      navigate("/loyalty-transactions");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this loyalty transaction.");
      setIsDeleting(false);
    }
  }

  if (error && !transaction) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!transaction) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/loyalty-transactions" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Loyalty Transactions
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{transaction.reason}</h1>
            <LoyaltyPointsBadge points={transaction.points} />
          </div>
          {contact && (
            <p className="mt-1 text-sm text-slate-500">
              For{" "}
              <Link to={`/contacts/${contact.id}`} className="text-slate-700 hover:underline">
                {contact.fullName}
              </Link>
              {balance != null && <span> - current balance: {balance} points</span>}
            </p>
          )}
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit transaction</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Points" type="number" step="1" error={errors.points?.message} {...register("points")} />
          <Select
            label="Reason"
            options={LOYALTY_TRANSACTION_REASONS.map((reason) => ({ value: reason, label: reason }))}
            error={errors.reason?.message}
            {...register("reason")}
          />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
