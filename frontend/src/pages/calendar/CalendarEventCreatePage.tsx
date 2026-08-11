import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createCalendarEvent } from "../../api/calendarEvents";
import { RelatedToPicker } from "../../components/crm/RelatedToPicker";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createCalendarEventSchema, type CreateCalendarEventFormValues } from "../../lib/validation";
import type { CrmRecordType } from "../../types/api";

export default function CalendarEventCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateCalendarEventFormValues>({
    resolver: zodResolver(createCalendarEventSchema),
    defaultValues: { allDay: false, relatedToType: "", relatedToId: "" },
  });

  const relatedToType = watch("relatedToType") ?? "";
  const relatedToId = watch("relatedToId") ?? "";

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const event = await createCalendarEvent({
        title: values.title,
        description: blankToUndefined(values.description),
        location: blankToUndefined(values.location),
        // datetime-local inputs report local time with no offset - same conversion
        // ActivityTimeline's dueAt field established.
        startAt: new Date(values.startAt).toISOString(),
        endAt: new Date(values.endAt).toISOString(),
        allDay: values.allDay ?? false,
        relatedToType: blankToUndefined(values.relatedToType) as CrmRecordType | undefined,
        relatedToId: blankToUndefined(values.relatedToId),
      });
      navigate(`/calendar/${event.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New event</h1>
        <p className="mt-1 text-sm text-slate-500">Schedule a meeting, optionally tied to an Account, Contact, Opportunity, Lead, or Ticket.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Title" error={errors.title?.message} {...register("title")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Starts" type="datetime-local" error={errors.startAt?.message} {...register("startAt")} />
          <TextField label="Ends" type="datetime-local" error={errors.endAt?.message} {...register("endAt")} />
        </div>

        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" className="h-4 w-4 rounded border-slate-300" {...register("allDay")} />
          All day
        </label>

        <TextField label="Location" error={errors.location?.message} {...register("location")} />

        <RelatedToPicker
          allowEmpty
          relatedToType={relatedToType}
          relatedToId={relatedToId}
          onChange={(type, id) => {
            setValue("relatedToType", type, { shouldValidate: true });
            setValue("relatedToId", id, { shouldValidate: true });
          }}
          typeError={errors.relatedToType?.message}
          idError={errors.relatedToId?.message}
        />

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/calendar")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create event
          </Button>
        </div>
      </form>
    </div>
  );
}
