import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { createLoyaltyTransaction, getLoyaltyBalance } from "../../api/loyaltyTransactions";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createLoyaltyTransactionSchema, type CreateLoyaltyTransactionFormValues } from "../../lib/validation";
import { LOYALTY_TRANSACTION_REASONS, type ContactDto, type LoyaltyTransactionReason } from "../../types/api";

export default function LoyaltyTransactionCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [balance, setBalance] = useState<number | null>(null);

  useEffect(() => {
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<CreateLoyaltyTransactionFormValues>({
    resolver: zodResolver(createLoyaltyTransactionSchema),
    defaultValues: { reason: "EARNED_CHECKIN" },
  });

  const selectedContactId = watch("contactId");

  useEffect(() => {
    if (!selectedContactId) {
      setBalance(null);
      return;
    }
    let cancelled = false;
    getLoyaltyBalance(selectedContactId)
      .then((result) => {
        if (!cancelled) setBalance(result.balance);
      })
      .catch(() => {
        if (!cancelled) setBalance(null);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedContactId]);

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const transaction = await createLoyaltyTransaction({
        contactId: values.contactId,
        points: Number(values.points),
        reason: values.reason as LoyaltyTransactionReason,
        notes: blankToUndefined(values.notes),
      });
      navigate(`/loyalty-transactions/${transaction.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Log a loyalty transaction</h1>
        <p className="mt-1 text-sm text-slate-500">
          Points must be positive for earned reasons and negative for redemptions - manual adjustments can go either way.
        </p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <Select
          label="Client"
          placeholder="Select a contact"
          options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
          error={errors.contactId?.message}
          {...register("contactId")}
        />

        {selectedContactId && balance != null && (
          <p className="text-xs text-slate-500">Current balance: <span className="font-medium text-slate-700">{balance} points</span></p>
        )}

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

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/loyalty-transactions")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Log transaction
          </Button>
        </div>
      </form>
    </div>
  );
}
