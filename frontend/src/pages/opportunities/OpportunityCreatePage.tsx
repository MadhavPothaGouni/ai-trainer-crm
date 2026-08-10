import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { listAccounts } from "../../api/accounts";
import { listContacts } from "../../api/contacts";
import { createOpportunity } from "../../api/opportunities";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createOpportunitySchema, toOptionalNumber, type CreateOpportunityFormValues } from "../../lib/validation";
import type { AccountDto, ContactDto } from "../../types/api";

export default function OpportunityCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [accounts, setAccounts] = useState<AccountDto[]>([]);
  const [contacts, setContacts] = useState<ContactDto[]>([]);

  useEffect(() => {
    listAccounts({ size: 100, sort: "name,asc" })
      .then((res) => setAccounts(res.content))
      .catch(() => setAccounts([]));
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateOpportunityFormValues>({ resolver: zodResolver(createOpportunitySchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const opportunity = await createOpportunity({
        accountId: values.accountId,
        primaryContactId: blankToUndefined(values.primaryContactId),
        name: values.name,
        amount: toOptionalNumber(values.amount),
        currency: blankToUndefined(values.currency),
        expectedCloseDate: blankToUndefined(values.expectedCloseDate),
        description: blankToUndefined(values.description),
      });
      navigate(`/opportunities/${opportunity.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New opportunity</h1>
        <p className="mt-1 text-sm text-slate-500">Track a deal against one of your accounts.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Opportunity name" error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Account"
            placeholder="Select an account"
            options={accounts.map((account) => ({ value: account.id, label: account.name }))}
            error={errors.accountId?.message}
            {...register("accountId")}
          />
          <Select
            label="Primary contact"
            placeholder="No contact"
            options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
            error={errors.primaryContactId?.message}
            {...register("primaryContactId")}
          />
          <TextField label="Amount" type="number" min={0} step="any" error={errors.amount?.message} {...register("amount")} />
          <TextField label="Currency" placeholder="USD" maxLength={3} error={errors.currency?.message} {...register("currency")} />
          <TextField
            label="Expected close date"
            type="date"
            error={errors.expectedCloseDate?.message}
            {...register("expectedCloseDate")}
          />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/opportunities")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create opportunity
          </Button>
        </div>
      </form>
    </div>
  );
}
