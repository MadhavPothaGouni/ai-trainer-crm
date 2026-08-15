import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteGiftCard, getGiftCard, redeemGiftCard, updateGiftCard, updateGiftCardStatus } from "../../api/giftCards";
import { getContact } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateGiftCardSchema, type UpdateGiftCardFormValues } from "../../lib/validation";
import { GIFT_CARD_STATUSES, type ContactDto, type GiftCardDto, type GiftCardStatus } from "../../types/api";
import { GiftCardStatusBadge } from "./GiftCardListPage";

export default function GiftCardDetailPage() {
  const { giftCardId } = useParams<{ giftCardId: string }>();
  const navigate = useNavigate();
  const [giftCard, setGiftCard] = useState<GiftCardDto | null>(null);
  const [contact, setContact] = useState<ContactDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [redeemAmount, setRedeemAmount] = useState("");
  const [redeemError, setRedeemError] = useState<string | null>(null);
  const [isRedeeming, setIsRedeeming] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateGiftCardFormValues>({ resolver: zodResolver(updateGiftCardSchema) });

  useEffect(() => {
    if (!giftCardId) return;
    let cancelled = false;
    getGiftCard(giftCardId)
      .then((data) => {
        if (cancelled) return;
        setGiftCard(data);
        reset({ expiresAt: data.expiresAt ?? "", notes: data.notes ?? "" });
        getContact(data.contactId).then(setContact).catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this gift card.");
      });
    return () => {
      cancelled = true;
    };
  }, [giftCardId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!giftCardId) return;
    setFormError(null);
    try {
      const updated = await updateGiftCard(giftCardId, {
        expiresAt: blankToUndefined(values.expiresAt),
        notes: blankToUndefined(values.notes),
      });
      setGiftCard(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!giftCardId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateGiftCardStatus(giftCardId, { status: status as GiftCardStatus });
      setGiftCard(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleRedeem() {
    if (!giftCardId) return;
    const amount = Number(redeemAmount);
    if (!redeemAmount || Number.isNaN(amount) || amount <= 0) {
      setRedeemError("Enter an amount greater than zero.");
      return;
    }
    setIsRedeeming(true);
    setRedeemError(null);
    try {
      const updated = await redeemGiftCard(giftCardId, { amount });
      setGiftCard(updated);
      setRedeemAmount("");
    } catch (err) {
      setRedeemError(err instanceof ApiError ? err.message : "Could not redeem this gift card.");
    } finally {
      setIsRedeeming(false);
    }
  }

  async function handleDelete() {
    if (!giftCardId || !window.confirm("Delete this gift card? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteGiftCard(giftCardId);
      navigate("/gift-cards");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this gift card.");
      setIsDeleting(false);
    }
  }

  if (error && !giftCard) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!giftCard) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/gift-cards" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Gift Cards
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{giftCard.code}</h1>
            <GiftCardStatusBadge status={giftCard.status} />
          </div>
          {contact && (
            <p className="mt-1 text-sm text-slate-500">
              Issued to{" "}
              <Link to={`/contacts/${contact.id}`} className="text-slate-700 hover:underline">
                {contact.fullName}
              </Link>
            </p>
          )}
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Balance</h2>
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <Row label="Initial" value={`$${giftCard.initialBalance.toFixed(2)}`} />
            <Row label="Remaining" value={`$${giftCard.currentBalance.toFixed(2)}`} />
            <Row label="Issued" value={new Date(giftCard.issuedAt).toLocaleString()} />
            <Row label="Redeemed" value={giftCard.redeemedAt ? new Date(giftCard.redeemedAt).toLocaleString() : "Not yet"} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">Cards move freely between statuses - reactivating a cancelled or expired card is a normal correction.</p>
          <div className="mt-3">
            <Select
              label="Status"
              options={GIFT_CARD_STATUSES.map((status) => ({ value: status, label: status }))}
              value={giftCard.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Redeem balance</h2>
        <p className="text-xs text-slate-400">
          Deducts from the remaining balance. Rejected if the card isn't ACTIVE, is expired, or the amount exceeds what's left.
        </p>
        {redeemError && <Alert variant="error">{redeemError}</Alert>}
        <div className="flex max-w-sm items-end gap-3">
          <TextField
            label="Amount"
            type="number"
            min={0.01}
            step="0.01"
            value={redeemAmount}
            onChange={(event) => setRedeemAmount(event.target.value)}
          />
          <Button onClick={() => void handleRedeem()} isLoading={isRedeeming}>
            Redeem
          </Button>
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit gift card</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Expires on" type="date" error={errors.expiresAt?.message} {...register("expiresAt")} />
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

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right text-slate-900">{value ?? "—"}</dd>
    </div>
  );
}
