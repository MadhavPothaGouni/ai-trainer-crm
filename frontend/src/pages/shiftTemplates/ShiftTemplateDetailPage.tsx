import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteShiftTemplate, getShiftTemplate, updateShiftTemplate } from "../../api/shiftTemplates";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createShiftTemplateSchema, type CreateShiftTemplateFormValues } from "../../lib/validation";
import { SHIFT_TEMPLATE_DAYS_OF_WEEK, type ShiftTemplateDayOfWeek, type ShiftTemplateDto } from "../../types/api";

export default function ShiftTemplateDetailPage() {
  const { shiftTemplateId } = useParams<{ shiftTemplateId: string }>();
  const navigate = useNavigate();
  const [template, setTemplate] = useState<ShiftTemplateDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateShiftTemplateFormValues>({ resolver: zodResolver(createShiftTemplateSchema) });

  useEffect(() => {
    if (!shiftTemplateId) return;
    let cancelled = false;
    getShiftTemplate(shiftTemplateId)
      .then((data) => {
        if (cancelled) return;
        setTemplate(data);
        reset({ name: data.name, dayOfWeek: data.dayOfWeek, startTime: data.startTime.slice(0, 5), endTime: data.endTime.slice(0, 5), role: data.role ?? "" });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this shift template.");
      });
    return () => {
      cancelled = true;
    };
  }, [shiftTemplateId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!shiftTemplateId || !template) return;
    setFormError(null);
    try {
      const updated = await updateShiftTemplate(shiftTemplateId, {
        name: values.name,
        dayOfWeek: values.dayOfWeek as ShiftTemplateDayOfWeek,
        startTime: values.startTime,
        endTime: values.endTime,
        role: blankToUndefined(values.role),
        active: template.active,
      });
      setTemplate(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function toggleActive() {
    if (!shiftTemplateId || !template) return;
    try {
      const updated = await updateShiftTemplate(shiftTemplateId, {
        name: template.name,
        dayOfWeek: template.dayOfWeek,
        startTime: template.startTime,
        endTime: template.endTime,
        role: template.role ?? undefined,
        active: !template.active,
      });
      setTemplate(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this template.");
    }
  }

  async function handleDelete() {
    if (!shiftTemplateId || !window.confirm("Delete this shift template? Existing shifts keep their own record, so this is safe.")) return;
    setIsDeleting(true);
    try {
      await deleteShiftTemplate(shiftTemplateId);
      navigate("/shift-templates");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this template.");
      setIsDeleting(false);
    }
  }

  if (error && !template) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!template) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/shift-templates" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Shift Templates
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{template.name}</h1>
            {template.active ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <Link to={`/shifts/new?shiftTemplateId=${template.id}`}>
            <Button variant="secondary">Schedule shift</Button>
          </Link>
          <Button variant="secondary" onClick={() => void toggleActive()}>
            {template.active ? "Deactivate" : "Activate"}
          </Button>
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-3">
          <Select
            label="Day of week"
            options={SHIFT_TEMPLATE_DAYS_OF_WEEK.map((day) => ({ value: day, label: day.charAt(0) + day.slice(1).toLowerCase() }))}
            error={errors.dayOfWeek?.message}
            {...register("dayOfWeek")}
          />
          <TextField label="Start time" type="time" error={errors.startTime?.message} {...register("startTime")} />
          <TextField label="End time" type="time" error={errors.endTime?.message} {...register("endTime")} />
        </div>

        <TextField label="Role" error={errors.role?.message} {...register("role")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
