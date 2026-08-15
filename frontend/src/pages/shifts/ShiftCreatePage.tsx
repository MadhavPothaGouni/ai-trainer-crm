import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { listShiftTemplates } from "../../api/shiftTemplates";
import { createShift } from "../../api/shifts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createShiftSchema, type CreateShiftFormValues } from "../../lib/validation";
import type { ShiftTemplateDto } from "../../types/api";

export default function ShiftCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedTemplateId = searchParams.get("shiftTemplateId") ?? "";
  const [formError, setFormError] = useState<string | null>(null);
  const [templates, setTemplates] = useState<ShiftTemplateDto[]>([]);

  useEffect(() => {
    listShiftTemplates({ size: 100, sort: "name,asc" })
      .then((res) => setTemplates(res.content.filter((template) => template.active)))
      .catch(() => setTemplates([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateShiftFormValues>({
    resolver: zodResolver(createShiftSchema),
    defaultValues: { shiftTemplateId: preselectedTemplateId },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const shift = await createShift({
        shiftTemplateId: blankToUndefined(values.shiftTemplateId),
        shiftDate: values.shiftDate,
        startsAt: new Date(values.startsAt).toISOString(),
        endsAt: new Date(values.endsAt).toISOString(),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/shifts/${shift.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Schedule a shift</h1>
        <p className="mt-1 text-sm text-slate-500">Assigned to you by default - clock-in/out is tracked from its status.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <Select
          label="Shift template"
          placeholder="No template - one-off shift"
          options={templates.map((template) => ({ value: template.id, label: template.name }))}
          error={errors.shiftTemplateId?.message}
          {...register("shiftTemplateId")}
        />

        <TextField label="Shift date" type="date" error={errors.shiftDate?.message} {...register("shiftDate")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Starts at" type="datetime-local" error={errors.startsAt?.message} {...register("startsAt")} />
          <TextField label="Ends at" type="datetime-local" error={errors.endsAt?.message} {...register("endsAt")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/shifts")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Schedule shift
          </Button>
        </div>
      </form>
    </div>
  );
}
