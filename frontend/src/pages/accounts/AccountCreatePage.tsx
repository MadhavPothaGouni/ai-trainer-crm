import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createAccount } from "../../api/accounts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createAccountSchema, toOptionalNumber, type CreateAccountFormValues } from "../../lib/validation";

export default function AccountCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateAccountFormValues>({ resolver: zodResolver(createAccountSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const account = await createAccount({
        name: values.name,
        industry: blankToUndefined(values.industry),
        website: blankToUndefined(values.website),
        phone: blankToUndefined(values.phone),
        billingStreet: blankToUndefined(values.billingStreet),
        billingCity: blankToUndefined(values.billingCity),
        billingState: blankToUndefined(values.billingState),
        billingPostalCode: blankToUndefined(values.billingPostalCode),
        billingCountry: blankToUndefined(values.billingCountry),
        annualRevenue: toOptionalNumber(values.annualRevenue),
        employeeCount: toOptionalNumber(values.employeeCount),
        description: blankToUndefined(values.description),
      });
      navigate(`/accounts/${account.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New account</h1>
        <p className="mt-1 text-sm text-slate-500">Add a company your team is working with.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Account name" error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Industry" error={errors.industry?.message} {...register("industry")} />
          <TextField label="Website" error={errors.website?.message} {...register("website")} />
          <TextField label="Phone" error={errors.phone?.message} {...register("phone")} />
          <TextField
            label="Annual revenue"
            type="number"
            min={0}
            step="any"
            error={errors.annualRevenue?.message}
            {...register("annualRevenue")}
          />
          <TextField
            label="Employee count"
            type="number"
            min={0}
            error={errors.employeeCount?.message}
            {...register("employeeCount")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Billing street" error={errors.billingStreet?.message} {...register("billingStreet")} />
          <TextField label="Billing city" error={errors.billingCity?.message} {...register("billingCity")} />
          <TextField label="Billing state" error={errors.billingState?.message} {...register("billingState")} />
          <TextField label="Billing postal code" error={errors.billingPostalCode?.message} {...register("billingPostalCode")} />
          <TextField label="Billing country" error={errors.billingCountry?.message} {...register("billingCountry")} />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/accounts")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create account
          </Button>
        </div>
      </form>
    </div>
  );
}
