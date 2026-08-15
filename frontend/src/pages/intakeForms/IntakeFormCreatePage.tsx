import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createIntakeForm } from "../../api/intakeForms";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createIntakeFormSchema, type CreateIntakeFormFormValues } from "../../lib/validation";
import { INTAKE_FORM_TYPES, type IntakeFormType } from "../../types/api";

export default function IntakeFormCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateIntakeFormFormValues>({
    resolver: zodResolver(createIntakeFormSchema),
    defaultValues: { formType: "OTHER" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const form = await createIntakeForm({
        title: values.title,
        formType: values.formType as IntakeFormType,
        notes: blankToUndefined(values.notes),
      });
      navigate(`/intake-forms/${form.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Add an intake form</h1>
        <p className="mt-1 text-sm text-slate-500">A questionnaire clients complete before or during onboarding.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Title" error={errors.title?.message} {...register("title")} />
          <Select
            label="Type"
            options={INTAKE_FORM_TYPES.map((type) => ({ value: type, label: type }))}
            error={errors.formType?.message}
            {...register("formType")}
          />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/intake-forms")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Add form
          </Button>
        </div>
      </form>
    </div>
  );
}
