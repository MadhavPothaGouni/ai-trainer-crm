import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createPromoCode } from "../../api/promoCodes";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createPromoCodeSchema, toOptionalNumber, type CreatePromoCodeFormValues } from "../../lib/validation";
import { PROMO_CODE_DISCOUNT_TYPES, type PromoCodeDiscountType } from "../../types/api";

export default function PromoCodeCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreatePromoCodeFormValues>({ resolver: zodResolver(createPromoCodeSchema), defaultValues: { discountType: "PERCENTAGE" } });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const promoCode = await createPromoCode({
        code: values.code,
        description: blankToUndefined(values.description),
        discountType: values.discountType as PromoCodeDiscountType,
        discountValue: Number(values.discountValue),
        maxRedemptions: toOptionalNumber(values.maxRedemptions),
        expiresAt: blankToUndefined(values.expiresAt),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/promo-codes/${promoCode.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New promo code</h1>
        <p className="mt-1 text-sm text-slate-500">Add a discount code to the catalog - redemptions get recorded against it.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Code" placeholder="SUMMER10" error={errors.code?.message} {...register("code")} />
          <Select
            label="Discount type"
            options={PROMO_CODE_DISCOUNT_TYPES.map((type) => ({ value: type, label: type.replace("_", " ") }))}
            error={errors.discountType?.message}
            {...register("discountType")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Discount value" type="number" min={0} step="0.01" error={errors.discountValue?.message} {...register("discountValue")} />
          <TextField label="Max redemptions" type="number" min={1} step="1" placeholder="Unlimited" error={errors.maxRedemptions?.message} {...register("maxRedemptions")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Description" error={errors.description?.message} {...register("description")} />
          <TextField label="Expires on" type="date" error={errors.expiresAt?.message} {...register("expiresAt")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/promo-codes")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Add promo code
          </Button>
        </div>
      </form>
    </div>
  );
}
