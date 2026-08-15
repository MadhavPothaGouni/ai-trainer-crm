import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deletePromoCode, getPromoCode, updatePromoCode } from "../../api/promoCodes";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, toOptionalNumber, updatePromoCodeSchema, type UpdatePromoCodeFormValues } from "../../lib/validation";
import { PROMO_CODE_DISCOUNT_TYPES, type PromoCodeDiscountType, type PromoCodeDto } from "../../types/api";

export default function PromoCodeDetailPage() {
  const { promoCodeId } = useParams<{ promoCodeId: string }>();
  const navigate = useNavigate();
  const [promoCode, setPromoCode] = useState<PromoCodeDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdatePromoCodeFormValues>({ resolver: zodResolver(updatePromoCodeSchema) });

  useEffect(() => {
    if (!promoCodeId) return;
    let cancelled = false;
    getPromoCode(promoCodeId)
      .then((data) => {
        if (cancelled) return;
        setPromoCode(data);
        reset({
          code: data.code,
          description: data.description ?? "",
          discountType: data.discountType,
          discountValue: String(data.discountValue),
          maxRedemptions: data.maxRedemptions != null ? String(data.maxRedemptions) : "",
          expiresAt: data.expiresAt ?? "",
          notes: data.notes ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this promo code.");
      });
    return () => {
      cancelled = true;
    };
  }, [promoCodeId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!promoCodeId || !promoCode) return;
    setFormError(null);
    try {
      const updated = await updatePromoCode(promoCodeId, {
        code: values.code,
        description: blankToUndefined(values.description),
        discountType: values.discountType as PromoCodeDiscountType,
        discountValue: Number(values.discountValue),
        maxRedemptions: toOptionalNumber(values.maxRedemptions),
        active: promoCode.active,
        expiresAt: blankToUndefined(values.expiresAt),
        notes: blankToUndefined(values.notes),
      });
      setPromoCode(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function toggleActive() {
    if (!promoCodeId || !promoCode) return;
    try {
      const updated = await updatePromoCode(promoCodeId, {
        code: promoCode.code,
        description: promoCode.description ?? undefined,
        discountType: promoCode.discountType,
        discountValue: promoCode.discountValue,
        maxRedemptions: promoCode.maxRedemptions ?? undefined,
        active: !promoCode.active,
        expiresAt: promoCode.expiresAt ?? undefined,
        notes: promoCode.notes ?? undefined,
      });
      setPromoCode(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this promo code.");
    }
  }

  async function handleDelete() {
    if (!promoCodeId || !window.confirm("Delete this promo code? Existing redemptions keep their own record, so this is safe.")) return;
    setIsDeleting(true);
    try {
      await deletePromoCode(promoCodeId);
      navigate("/promo-codes");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this promo code.");
      setIsDeleting(false);
    }
  }

  if (error && !promoCode) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!promoCode) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/promo-codes" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Promo Codes
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{promoCode.code}</h1>
            {promoCode.active ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <Link to={`/promo-redemptions/new?promoCodeId=${promoCode.id}`}>
            <Button variant="secondary">Record redemption</Button>
          </Link>
          <Button variant="secondary" onClick={() => void toggleActive()}>
            {promoCode.active ? "Deactivate" : "Activate"}
          </Button>
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Code" error={errors.code?.message} {...register("code")} />
          <Select
            label="Discount type"
            options={PROMO_CODE_DISCOUNT_TYPES.map((type) => ({ value: type, label: type.replace("_", " ") }))}
            error={errors.discountType?.message}
            {...register("discountType")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Discount value" type="number" min={0} step="0.01" error={errors.discountValue?.message} {...register("discountValue")} />
          <TextField label="Max redemptions" type="number" min={1} step="1" error={errors.maxRedemptions?.message} {...register("maxRedemptions")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Description" error={errors.description?.message} {...register("description")} />
          <TextField label="Expires on" type="date" error={errors.expiresAt?.message} {...register("expiresAt")} />
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
