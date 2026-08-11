import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { listOpportunities } from "../../api/opportunities";
import { createQuote } from "../../api/quotes";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createQuoteSchema, toOptionalNumber, type CreateQuoteFormValues } from "../../lib/validation";
import type { OpportunityDto } from "../../types/api";

export default function QuoteCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedOpportunityId = searchParams.get("opportunityId") ?? "";
  const [opportunities, setOpportunities] = useState<OpportunityDto[]>([]);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    listOpportunities({ size: 100, sort: "name,asc" })
      .then((res) => setOpportunities(res.content))
      .catch(() => undefined);
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateQuoteFormValues>({
    resolver: zodResolver(createQuoteSchema),
    defaultValues: { opportunityId: preselectedOpportunityId },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const quote = await createQuote({
        opportunityId: values.opportunityId,
        name: values.name,
        currency: blankToUndefined(values.currency),
        validUntil: blankToUndefined(values.validUntil),
        discountAmount: toOptionalNumber(values.discountAmount),
        taxAmount: toOptionalNumber(values.taxAmount),
      });
      navigate(`/quotes/${quote.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New quote</h1>
        <p className="mt-1 text-sm text-slate-500">A priced proposal tied to one opportunity. Line items are added after creation.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <Select
          label="Opportunity"
          placeholder="Select an opportunity"
          options={opportunities.map((opportunity) => ({ value: opportunity.id, label: opportunity.name }))}
          error={errors.opportunityId?.message}
          {...register("opportunityId")}
        />
        <TextField label="Quote name" error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Currency" placeholder="USD" error={errors.currency?.message} {...register("currency")} />
          <TextField label="Valid until" type="date" error={errors.validUntil?.message} {...register("validUntil")} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Discount"
            type="number"
            min={0}
            step="any"
            error={errors.discountAmount?.message}
            {...register("discountAmount")}
          />
          <TextField label="Tax" type="number" min={0} step="any" error={errors.taxAmount?.message} {...register("taxAmount")} />
        </div>

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/quotes")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create quote
          </Button>
        </div>
      </form>
    </div>
  );
}
