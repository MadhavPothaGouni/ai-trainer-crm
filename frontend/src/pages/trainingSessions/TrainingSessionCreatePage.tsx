import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { createTrainingSession } from "../../api/trainingSessions";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  createTrainingSessionSchema,
  toRequiredNumber,
  type CreateTrainingSessionFormValues,
} from "../../lib/validation";
import { TRAINING_SESSION_TYPES, type ContactDto, type TrainingSessionType } from "../../types/api";

export default function TrainingSessionCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);

  useEffect(() => {
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateTrainingSessionFormValues>({
    resolver: zodResolver(createTrainingSessionSchema),
    defaultValues: { sessionType: "IN_PERSON", durationMinutes: "60" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const session = await createTrainingSession({
        contactId: values.contactId,
        // datetime-local inputs report local time with no offset - same conversion
        // CalendarEventCreatePage's startAt field established.
        startedAt: new Date(values.startedAt).toISOString(),
        durationMinutes: toRequiredNumber(values.durationMinutes),
        sessionType: values.sessionType as TrainingSessionType,
        focusArea: blankToUndefined(values.focusArea),
        clientRpe: values.clientRpe ? Number(values.clientRpe) : undefined,
        coachNotes: blankToUndefined(values.coachNotes),
      });
      navigate(`/training-sessions/${session.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Log a training session</h1>
        <p className="mt-1 text-sm text-slate-500">Record what actually happened in a session with a client.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Client"
            placeholder="Select a contact"
            options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
            error={errors.contactId?.message}
            {...register("contactId")}
          />
          <Select
            label="Session type"
            options={TRAINING_SESSION_TYPES.map((type) => ({ value: type, label: type.replace("_", " ") }))}
            error={errors.sessionType?.message}
            {...register("sessionType")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Started" type="datetime-local" error={errors.startedAt?.message} {...register("startedAt")} />
          <TextField label="Duration (minutes)" type="number" min={1} error={errors.durationMinutes?.message} {...register("durationMinutes")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Focus area" placeholder="Lower body, cardio, mobility..." error={errors.focusArea?.message} {...register("focusArea")} />
          <TextField
            label="Client RPE (1-10)"
            type="number"
            min={1}
            max={10}
            error={errors.clientRpe?.message}
            {...register("clientRpe")}
          />
        </div>

        <TextArea label="Coach notes" error={errors.coachNotes?.message} {...register("coachNotes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/training-sessions")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Log session
          </Button>
        </div>
      </form>
    </div>
  );
}
