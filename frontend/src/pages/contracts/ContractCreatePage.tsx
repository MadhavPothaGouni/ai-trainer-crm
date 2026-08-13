import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { listAccounts } from "../../api/accounts";
import { createContract } from "../../api/contracts";
import { listOpportunities } from "../../api/opportunities";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createContractSchema, toOptionalNumber, type CreateContractFormValues } from "../../lib/validation";
import type { AccountDto, OpportunityDto } from "../../types/api";

export default function ContractCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [accounts, setAccounts] = useState<AccountDto[]>([]);
  const [opportunities, setOpportunities] = useState<OpportunityDto[]>([]);

  useEffect(() => {
    listAccounts({ size: 100, sort: "name,asc" })
      .then((res) => setAccounts(res.content))
      .catch(() => setAccounts([]));
    listOpportunities({ size: 100, sort: "createdAt,desc" })
      .then((res) => setOpportunities(res.content))
      .catch(() => setOpportunities([]));
  }, []);

  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateContractFormValues>({ resolver: zodResolver(createContractSchema), defaultValues: { autoRenew: false } });

  const autoRenew = watch("autoRenew");

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const contract = await createContract({
        accountId: values.accountId,
        opportunityId: blankToUndefined(values.opportunityId),
        contractNumber: values.contractNumber,
        title: values.title,
        startDate: values.startDate,
        endDate: values.endDate,
        totalValue: toOptionalNumber(values.totalValue),
        autoRenew: values.autoRenew ?? false,
        renewalTermMonths: values.renewalTermMonths ? Number(values.renewalTermMonths) : undefined,
        terms: blankToUndefined(values.terms),
      });
      navigate(`/contracts/${contract.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New contract</h1>
        <p className="mt-1 text-sm text-slate-500">The ongoing agreement with a customer, tracked after a deal closes.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Account"
            placeholder="Select an account"
            options={accounts.map((account) => ({ value: account.id, label: account.name }))}
            error={errors.accountId?.message}
            {...register("accountId")}
          />
          <Select
            label="Opportunity"
            placeholder="None (e.g. a renewal)"
            options={opportunities.map((opportunity) => ({ value: opportunity.id, label: opportunity.name }))}
            error={errors.opportunityId?.message}
            {...register("opportunityId")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Contract number" error={errors.contractNumber?.message} {...register("contractNumber")} />
          <TextField label="Title" error={errors.title?.message} {...register("title")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Start date" type="date" error={errors.startDate?.message} {...register("startDate")} />
          <TextField label="End date" type="date" error={errors.endDate?.message} {...register("endDate")} />
          <TextField label="Total value" type="number" min={0} step="any" error={errors.totalValue?.message} {...register("totalValue")} />
        </div>

        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" className="h-4 w-4 rounded border-slate-300" {...register("autoRenew")} />
          Auto-renews
        </label>

        {autoRenew && (
          <TextField
            label="Renewal term (months)"
            type="number"
            min={1}
            step={1}
            error={errors.renewalTermMonths?.message}
            {...register("renewalTermMonths")}
          />
        )}

        <TextArea label="Terms" error={errors.terms?.message} {...register("terms")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/contracts")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create contract
          </Button>
        </div>
      </form>
    </div>
  );
}
