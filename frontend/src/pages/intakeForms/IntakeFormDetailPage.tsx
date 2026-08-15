import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteIntakeForm, getIntakeForm, updateIntakeForm } from "../../api/intakeForms";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateIntakeFormSchema, type UpdateIntakeFormFormValues } from "../../lib/validation";
import { INTAKE_FORM_TYPES, type IntakeFormDto, type IntakeFormType } from "../../types/api";

export default function IntakeFormDetailPage() {
  const { intakeFormId } = useParams<{ intakeFormId: string }>();
  const navigate = useNavigate();
  const [form, setForm] = useState<IntakeFormDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateIntakeFormFormValues>({ resolver: zodResolver(updateIntakeFormSchema) });

  useEffect(() => {
    if (!intakeFormId) return;
    let cancelled = false;
    getIntakeForm(intakeFormId)
      .then((data) => {
        if (cancelled) return;
        setForm(data);
        reset({ title: data.title, formType: data.formType, active: data.active, notes: data.notes ?? "" });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this intake form.");
      });
    return () => {
      cancelled = true;
    };
  }, [intakeFormId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!intakeFormId || !form) return;
    setFormError(null);
    try {
      const updated = await updateIntakeForm(intakeFormId, {
        title: values.title,
        formType: values.formType as IntakeFormType,
        active: form.active,
        notes: blankToUndefined(values.notes),
      });
      setForm(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function toggleActive() {
    if (!intakeFormId || !form) return;
    try {
      const updated = await updateIntakeForm(intakeFormId, {
        title: form.title,
        formType: form.formType,
        active: !form.active,
        notes: form.notes ?? undefined,
      });
      setForm(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this intake form.");
    }
  }

  async function handleDelete() {
    if (!intakeFormId || !window.confirm("Delete this intake form? Existing submissions keep their own record, so this is safe.")) return;
    setIsDeleting(true);
    try {
      await deleteIntakeForm(intakeFormId);
      navigate("/intake-forms");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this intake form.");
      setIsDeleting(false);
    }
  }

  if (error && !form) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!form) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/intake-forms" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Intake Forms
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{form.title}</h1>
            {form.active ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <Link to={`/intake-form-submissions/new?formId=${form.id}`}>
            <Button variant="secondary">Record submission</Button>
          </Link>
          <Button variant="secondary" onClick={() => void toggleActive()}>
            {form.active ? "Deactivate" : "Activate"}
          </Button>
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit form</h2>

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

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
