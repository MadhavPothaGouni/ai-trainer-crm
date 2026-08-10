import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { listAccounts } from "../../api/accounts";
import { createContact } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createContactSchema, type CreateContactFormValues } from "../../lib/validation";
import type { AccountDto } from "../../types/api";

export default function ContactCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [accounts, setAccounts] = useState<AccountDto[]>([]);

  useEffect(() => {
    listAccounts({ size: 100, sort: "name,asc" })
      .then((res) => setAccounts(res.content))
      .catch(() => setAccounts([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateContactFormValues>({ resolver: zodResolver(createContactSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const contact = await createContact({
        firstName: values.firstName,
        lastName: values.lastName,
        email: blankToUndefined(values.email),
        phone: blankToUndefined(values.phone),
        title: blankToUndefined(values.title),
        description: blankToUndefined(values.description),
        accountId: blankToUndefined(values.accountId),
      });
      navigate(`/contacts/${contact.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New contact</h1>
        <p className="mt-1 text-sm text-slate-500">Add a person at one of your accounts.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="First name" error={errors.firstName?.message} {...register("firstName")} />
          <TextField label="Last name" error={errors.lastName?.message} {...register("lastName")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Email" type="email" error={errors.email?.message} {...register("email")} />
          <TextField label="Phone" error={errors.phone?.message} {...register("phone")} />
          <TextField label="Title" error={errors.title?.message} {...register("title")} />
          <Select
            label="Account"
            placeholder="No account"
            options={accounts.map((account) => ({ value: account.id, label: account.name }))}
            error={errors.accountId?.message}
            {...register("accountId")}
          />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/contacts")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create contact
          </Button>
        </div>
      </form>
    </div>
  );
}
