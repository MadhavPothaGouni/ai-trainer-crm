import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createTimeOffRequest } from "../../api/timeOffRequests";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createTimeOffRequestSchema, type CreateTimeOffRequestFormValues } from "../../lib/validation";
import { TIME_OFF_REQUEST_TYPES, type TimeOffRequestType } from "../../types/api";

export default function TimeOffRequestCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateTimeOffRequestFormValues>({
    resolver: zodResolver(createTimeOffRequestSchema),
    defaultValues: { type: "VACATION" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const request = await createTimeOffRequest({
        startDate: values.startDate,
        endDate: values.endDate,
        type: values.type as TimeOffRequestType,
        reason: blankToUndefined(values.reason),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/time-off-requests/${request.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New time-off request</h1>
        <p className="mt-1 text-sm text-slate-500">Request PTO for yourself - a manager can approve or deny it afterward.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Start date" type="date" error={errors.startDate?.message} {...register("startDate")} />
          <TextField label="End date" type="date" error={errors.endDate?.message} {...register("endDate")} />
        </div>

        <Select
          label="Type"
          options={TIME_OFF_REQUEST_TYPES.map((type) => ({ value: type, label: type }))}
          error={errors.type?.message}
          {...register("type")}
        />

        <TextArea label="Reason" error={errors.reason?.message} {...register("reason")} />
        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/time-off-requests")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Submit request
          </Button>
        </div>
      </form>
    </div>
  );
}
