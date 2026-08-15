import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deletePromoRedemption, getPromoRedemption, updatePromoRedemption } from "../../api/promoRedemptions";
import { getContact } from "../../api/contacts";
import { getPromoCode } from "../../api/promoCodes";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, toOptionalNumber, updatePromoRedemptionSchema, type UpdatePromoRedemptionFormValues } from "../../lib/validation";
import type { ContactDto, PromoCodeDto, PromoRedemptionDto } from "../../types/api";

export default function PromoRedemptionDetailPage() {
  const { promoRedemptionId } = useParams<{ promoRedemptionId: string }>();
  const navigate = useNavigate();
  const [redemption, setRedemption] = useState<PromoRedemptionDto | null>(null);
  const [promoCode, setPromoCode] = useState<PromoCodeDto | null>(null);
  const [contact, setContact] = useState<ContactDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdatePromoRedemptionFormValues>({ resolver: zodResolver(updatePromoRedemptionSchema) });

  useEffect(() => {
    if (!promoRedemptionId) return;
    let cancelled = false;
    getPromoRedemption(promoRedemptionId)
      .then((data) => {
        if (cancelled) return;
        setRedemption(data);
        reset({ amountDiscounted: data.amountDiscounted != null ? String(data.amountDiscounted) : "", notes: data.notes ?? "" });
        getPromoCode(data.promoCodeId).then(setPromoCode).catch(() => undefined);
        getContact(data.contactId).then(setContact).catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this promo redemption.");
      });
    return () => {
      cancelled = true;
    };
  }, [promoRedemptionId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!promoRedemptionId || !redemption) return;
    setFormError(null);
    try {
      const updated = await updatePromoRedemption(promoRedemptionId, {
        orderId: redemption.orderId ?? undefined,
        amountDiscounted: toOptionalNumber(values.amountDiscounted),
        notes: blankToUndefined(values.notes),
      });
      setRedemption(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleDelete() {
    if (!promoRedemptionId || !window.confirm("Delete this promo redemption? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deletePromoRedemption(promoRedemptionId);
      navigate("/promo-redemptions");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this promo redemption.");
      setIsDeleting(false);
    }
  }

  if (error && !redemption) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!redemption) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/promo-redemptions" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Promo Redemptions
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">
              {promoCode ? (
                <Link to={`/promo-codes/${promoCode.id}`} className="hover:underline">
                  {promoCode.code}
                </Link>
              ) : (
                "Promo redemption"
              )}
            </h1>
          </div>
          {contact && (
            <p className="mt-1 text-sm text-slate-500">
              Redeemed by{" "}
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

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Overview</h2>
        <dl className="mt-3 flex flex-col gap-2 text-sm">
          <Row label="Redeemed" value={new Date(redemption.redeemedAt).toLocaleString()} />
          <Row label="Amount discounted" value={redemption.amountDiscounted != null ? `$${redemption.amountDiscounted.toFixed(2)}` : "—"} />
          <Row label="Order reference" value={redemption.orderId ?? "—"} />
        </dl>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit redemption</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Amount discounted" type="number" min={0} step="0.01" error={errors.amountDiscounted?.message} {...register("amountDiscounted")} />
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
