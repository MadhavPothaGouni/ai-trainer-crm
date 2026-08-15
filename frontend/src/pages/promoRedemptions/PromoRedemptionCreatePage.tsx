import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { createPromoRedemption } from "../../api/promoRedemptions";
import { listPromoCodes } from "../../api/promoCodes";
import { listContacts } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createPromoRedemptionSchema, toOptionalNumber, type CreatePromoRedemptionFormValues } from "../../lib/validation";
import type { ContactDto, PromoCodeDto } from "../../types/api";

export default function PromoRedemptionCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedPromoCodeId = searchParams.get("promoCodeId") ?? "";
  const [formError, setFormError] = useState<string | null>(null);
  const [promoCodes, setPromoCodes] = useState<PromoCodeDto[]>([]);
  const [contacts, setContacts] = useState<ContactDto[]>([]);

  useEffect(() => {
    listPromoCodes({ size: 100, sort: "code,asc" })
      .then((res) => setPromoCodes(res.content.filter((promoCode) => promoCode.active)))
      .catch(() => setPromoCodes([]));
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreatePromoRedemptionFormValues>({
    resolver: zodResolver(createPromoRedemptionSchema),
    defaultValues: { promoCodeId: preselectedPromoCodeId },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const redemption = await createPromoRedemption({
        promoCodeId: values.promoCodeId,
        contactId: values.contactId,
        amountDiscounted: toOptionalNumber(values.amountDiscounted),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/promo-redemptions/${redemption.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Record a promo redemption</h1>
        <p className="mt-1 text-sm text-slate-500">A client has used a promo code.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Promo code"
            placeholder="Select a promo code"
            options={promoCodes.map((promoCode) => ({ value: promoCode.id, label: promoCode.code }))}
            error={errors.promoCodeId?.message}
            {...register("promoCodeId")}
          />
          <Select
            label="Client"
            placeholder="Select a contact"
            options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
            error={errors.contactId?.message}
            {...register("contactId")}
          />
        </div>

        <TextField label="Amount discounted" type="number" min={0} step="0.01" error={errors.amountDiscounted?.message} {...register("amountDiscounted")} />

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/promo-redemptions")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Record redemption
          </Button>
        </div>
      </form>
    </div>
  );
}
