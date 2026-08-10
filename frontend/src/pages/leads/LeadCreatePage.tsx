import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createLead } from "../../api/leads";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createLeadSchema, type CreateLeadFormValues } from "../../lib/validation";
import { LEAD_SOURCES, type LeadSource } from "../../types/api";

export default function LeadCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateLeadFormValues>({ resolver: zodResolver(createLeadSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const lead = await createLead({
        firstName: values.firstName,
        lastName: values.lastName,
        email: blankToUndefined(values.email),
        phone: blankToUndefined(values.phone),
        companyName: blankToUndefined(values.companyName),
        title: blankToUndefined(values.title),
        source: values.source as LeadSource,
        description: blankToUndefined(values.description),
      });
      navigate(`/leads/${lead.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New lead</h1>
        <p className="mt-1 text-sm text-slate-500">Capture someone who isn&apos;t yet a qualified account.</p>
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
          <TextField label="Company" error={errors.companyName?.message} {...register("companyName")} />
          <TextField label="Title" error={errors.title?.message} {...register("title")} />
          <Select
            label="Source"
            placeholder="Select a source"
            options={LEAD_SOURCES.map((source) => ({ value: source, label: source.replace("_", " ") }))}
            error={errors.source?.message}
            {...register("source")}
          />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/leads")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create lead
          </Button>
        </div>
      </form>
    </div>
  );
}
