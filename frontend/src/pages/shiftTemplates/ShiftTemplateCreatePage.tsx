import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createShiftTemplate } from "../../api/shiftTemplates";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createShiftTemplateSchema, type CreateShiftTemplateFormValues } from "../../lib/validation";
import { SHIFT_TEMPLATE_DAYS_OF_WEEK, type ShiftTemplateDayOfWeek } from "../../types/api";

export default function ShiftTemplateCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateShiftTemplateFormValues>({
    resolver: zodResolver(createShiftTemplateSchema),
    defaultValues: { dayOfWeek: "MONDAY" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const template = await createShiftTemplate({
        name: values.name,
        dayOfWeek: values.dayOfWeek as ShiftTemplateDayOfWeek,
        startTime: values.startTime,
        endTime: values.endTime,
        role: blankToUndefined(values.role),
      });
      navigate(`/shift-templates/${template.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New shift template</h1>
        <p className="mt-1 text-sm text-slate-500">A recurring weekly pattern - actual shifts get scheduled from it.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" placeholder="Front Desk - Weekday Mornings" error={errors.name?.message} {...register("name")} />

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

        <TextField label="Role" placeholder="Front Desk, Instructor..." error={errors.role?.message} {...register("role")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/shift-templates")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create template
          </Button>
        </div>
      </form>
    </div>
  );
}
