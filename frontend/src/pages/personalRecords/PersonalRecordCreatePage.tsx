import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { listExercises } from "../../api/exercises";
import { createPersonalRecord } from "../../api/personalRecords";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createPersonalRecordSchema, type CreatePersonalRecordFormValues } from "../../lib/validation";
import { PERSONAL_RECORD_TYPES, type ContactDto, type ExerciseDto, type PersonalRecordType } from "../../types/api";

export default function PersonalRecordCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [exercises, setExercises] = useState<ExerciseDto[]>([]);

  useEffect(() => {
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
    listExercises({ size: 200, sort: "name,asc" })
      .then((res) => setExercises(res.content))
      .catch(() => setExercises([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreatePersonalRecordFormValues>({
    resolver: zodResolver(createPersonalRecordSchema),
    defaultValues: { recordType: "ONE_REP_MAX" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const record = await createPersonalRecord({
        contactId: values.contactId,
        exerciseId: values.exerciseId,
        recordType: values.recordType as PersonalRecordType,
        value: Number(values.value),
        achievedAt: values.achievedAt ? new Date(values.achievedAt).toISOString() : undefined,
        notes: blankToUndefined(values.notes),
      });
      navigate(`/personal-records/${record.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Log a personal record</h1>
        <p className="mt-1 text-sm text-slate-500">The value must beat the client's current best for this exercise and record type.</p>
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
            label="Exercise"
            placeholder="Select an exercise"
            options={exercises.map((exercise) => ({ value: exercise.id, label: exercise.name }))}
            error={errors.exerciseId?.message}
            {...register("exerciseId")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Record type"
            options={PERSONAL_RECORD_TYPES.map((type) => ({ value: type, label: type }))}
            error={errors.recordType?.message}
            {...register("recordType")}
          />
          <TextField label="Value" type="number" min={0} step="0.01" error={errors.value?.message} {...register("value")} />
        </div>

        <TextField label="Achieved at" type="datetime-local" error={errors.achievedAt?.message} {...register("achievedAt")} />

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/personal-records")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Log record
          </Button>
        </div>
      </form>
    </div>
  );
}
