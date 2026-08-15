import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { getContact } from "../../api/contacts";
import { getExercise } from "../../api/exercises";
import { deletePersonalRecord, getPersonalRecord, updatePersonalRecord } from "../../api/personalRecords";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updatePersonalRecordSchema, type UpdatePersonalRecordFormValues } from "../../lib/validation";
import type { ContactDto, ExerciseDto, PersonalRecordDto } from "../../types/api";

export default function PersonalRecordDetailPage() {
  const { personalRecordId } = useParams<{ personalRecordId: string }>();
  const navigate = useNavigate();
  const [record, setRecord] = useState<PersonalRecordDto | null>(null);
  const [contact, setContact] = useState<ContactDto | null>(null);
  const [exercise, setExercise] = useState<ExerciseDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdatePersonalRecordFormValues>({ resolver: zodResolver(updatePersonalRecordSchema) });

  useEffect(() => {
    if (!personalRecordId) return;
    let cancelled = false;
    getPersonalRecord(personalRecordId)
      .then((data) => {
        if (cancelled) return;
        setRecord(data);
        reset({ value: String(data.value), achievedAt: "", notes: data.notes ?? "" });
        getContact(data.contactId).then(setContact).catch(() => undefined);
        getExercise(data.exerciseId).then(setExercise).catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this personal record.");
      });
    return () => {
      cancelled = true;
    };
  }, [personalRecordId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!personalRecordId) return;
    setFormError(null);
    try {
      const updated = await updatePersonalRecord(personalRecordId, {
        value: Number(values.value),
        achievedAt: values.achievedAt ? new Date(values.achievedAt).toISOString() : undefined,
        notes: blankToUndefined(values.notes),
      });
      setRecord(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleDelete() {
    if (!personalRecordId || !window.confirm("Delete this personal record? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deletePersonalRecord(personalRecordId);
      navigate("/personal-records");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this personal record.");
      setIsDeleting(false);
    }
  }

  if (error && !record) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!record) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/personal-records" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Personal Records
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">
            {record.recordType}: {record.value}
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            {contact ? contact.fullName : "Loading client..."} &middot; {exercise ? exercise.name : "Loading exercise..."}
          </p>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Overview</h2>
        <dl className="mt-3 flex flex-col gap-2 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="text-slate-500">Achieved</dt>
            <dd className="text-right text-slate-900">{new Date(record.achievedAt).toLocaleString()}</dd>
          </div>
        </dl>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit record</h2>
        <p className="text-xs text-slate-400">A new value must still beat the client's other records for this exercise and record type.</p>

        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Value" type="number" min={0} step="0.01" error={errors.value?.message} {...register("value")} />
        <TextField label="Re-date achieved at (optional)" type="datetime-local" error={errors.achievedAt?.message} {...register("achievedAt")} />
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
