import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { listActiveExercises } from "../../api/exercises";
import {
  addTrainingSessionExercise,
  deleteTrainingSession,
  getTrainingSession,
  removeTrainingSessionExercise,
  updateTrainingSession,
  updateTrainingSessionExercise,
  updateTrainingSessionStatus,
} from "../../api/trainingSessions";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  toOptionalNumber,
  toRequiredNumber,
  trainingSessionExerciseSchema,
  updateTrainingSessionSchema,
  type TrainingSessionExerciseFormValues,
  type UpdateTrainingSessionFormValues,
} from "../../lib/validation";
import {
  TRAINING_SESSION_STATUSES,
  TRAINING_SESSION_TYPES,
  type ContactDto,
  type ExerciseDto,
  type TrainingSessionDto,
  type TrainingSessionExerciseDto,
  type TrainingSessionStatus,
  type TrainingSessionType,
} from "../../types/api";
import { TrainingSessionStatusBadge } from "./TrainingSessionListPage";

/** datetime-local wants "YYYY-MM-DDTHH:mm" in LOCAL time - same conversion CalendarEventDetailPage's edit form already establishes for startAt/endAt. */
function toDatetimeLocalValue(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export default function TrainingSessionDetailPage() {
  const { trainingSessionId } = useParams<{ trainingSessionId: string }>();
  const navigate = useNavigate();
  const [session, setSession] = useState<TrainingSessionDto | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [exerciseCatalog, setExerciseCatalog] = useState<ExerciseDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const [editingExerciseId, setEditingExerciseId] = useState<string | null>(null);
  const [pendingExerciseId, setPendingExerciseId] = useState<string | null>(null);

  function reloadSession() {
    if (!trainingSessionId) return;
    getTrainingSession(trainingSessionId)
      .then((data) => setSession(data))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load this session."));
  }

  useEffect(() => {
    if (!trainingSessionId) return;
    reloadSession();
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => undefined);
    listActiveExercises()
      .then((res) => setExerciseCatalog(res))
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [trainingSessionId]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<UpdateTrainingSessionFormValues>({ resolver: zodResolver(updateTrainingSessionSchema) });

  useEffect(() => {
    if (!session) return;
    reset({
      startedAt: toDatetimeLocalValue(session.startedAt),
      durationMinutes: String(session.durationMinutes),
      sessionType: session.sessionType,
      focusArea: session.focusArea ?? "",
      clientRpe: session.clientRpe != null ? String(session.clientRpe) : "",
      coachNotes: session.coachNotes ?? "",
    });
  }, [session, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!trainingSessionId) return;
    setEditError(null);
    try {
      const updated = await updateTrainingSession(trainingSessionId, {
        startedAt: new Date(values.startedAt).toISOString(),
        durationMinutes: toRequiredNumber(values.durationMinutes),
        sessionType: values.sessionType as TrainingSessionType,
        focusArea: blankToUndefined(values.focusArea),
        clientRpe: values.clientRpe ? Number(values.clientRpe) : undefined,
        coachNotes: blankToUndefined(values.coachNotes),
      });
      setSession(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!trainingSessionId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateTrainingSessionStatus(trainingSessionId, { status: status as TrainingSessionStatus });
      setSession(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!trainingSessionId || !window.confirm("Delete this session log? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteTrainingSession(trainingSessionId);
      navigate("/training-sessions");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this session.");
      setIsDeleting(false);
    }
  }

  async function handleRemoveExercise(exerciseEntryId: string) {
    if (!trainingSessionId || !window.confirm("Remove this exercise entry?")) return;
    setPendingExerciseId(exerciseEntryId);
    try {
      await removeTrainingSessionExercise(trainingSessionId, exerciseEntryId);
      reloadSession();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove this exercise entry.");
    } finally {
      setPendingExerciseId(null);
    }
  }

  if (error && !session) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!session) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const linkedContact = contacts.find((contact) => contact.id === session.contactId);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/training-sessions" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Training Sessions
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{new Date(session.startedAt).toLocaleString()}</h1>
            <TrainingSessionStatusBadge status={session.status} />
          </div>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Overview</h2>
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <Row
              label="Client"
              value={
                linkedContact && (
                  <Link to={`/contacts/${linkedContact.id}`} className="text-slate-900 hover:underline">
                    {linkedContact.fullName}
                  </Link>
                )
              }
            />
            <Row label="Type" value={session.sessionType.replace("_", " ")} />
            <Row label="Duration" value={`${session.durationMinutes} min`} />
            <Row label="Client RPE" value={session.clientRpe != null ? `${session.clientRpe} / 10` : undefined} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">Sessions move freely between statuses - correcting a mistaken no-show is normal.</p>
          <div className="mt-3">
            <Select
              label="Status"
              options={TRAINING_SESSION_STATUSES.map((status) => ({ value: status, label: status.replace("_", " ") }))}
              value={session.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <form onSubmit={onSaveEdits} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit session</h2>

        {editError && <Alert variant="error">{editError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Started" type="datetime-local" error={errors.startedAt?.message} {...register("startedAt")} />
          <TextField
            label="Duration (minutes)"
            type="number"
            min={1}
            error={errors.durationMinutes?.message}
            {...register("durationMinutes")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Session type"
            options={TRAINING_SESSION_TYPES.map((type) => ({ value: type, label: type.replace("_", " ") }))}
            error={errors.sessionType?.message}
            {...register("sessionType")}
          />
          <TextField label="Focus area" error={errors.focusArea?.message} {...register("focusArea")} />
        </div>

        <TextField label="Client RPE (1-10)" type="number" min={1} max={10} error={errors.clientRpe?.message} {...register("clientRpe")} />

        <TextArea label="Coach notes" error={errors.coachNotes?.message} {...register("coachNotes")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Exercises</h2>
        <p className="mt-1 text-xs text-slate-400">What was actually performed this session - sets, reps, and weight per movement.</p>

        <div className="mt-3 flex flex-col gap-2">
          {session.exercises.length === 0 && <p className="text-sm text-slate-400">No exercises logged yet.</p>}
          {session.exercises.map((exerciseEntry) =>
            editingExerciseId === exerciseEntry.id ? (
              <ExerciseEntryForm
                key={exerciseEntry.id}
                trainingSessionId={session.id}
                exerciseCatalog={exerciseCatalog}
                exerciseEntry={exerciseEntry}
                onDone={() => {
                  setEditingExerciseId(null);
                  reloadSession();
                }}
                onCancel={() => setEditingExerciseId(null)}
              />
            ) : (
              <div
                key={exerciseEntry.id}
                className="flex items-center justify-between gap-4 border-t border-slate-100 pt-2 first:border-t-0 first:pt-0"
              >
                <div className="text-sm">
                  <p className="font-medium text-slate-900">{exerciseEntry.exerciseName}</p>
                  <p className="text-slate-500">
                    {exerciseEntry.setsCompleted} sets &times; {exerciseEntry.repsCompleted} reps
                    {exerciseEntry.weightValue != null && ` @ ${exerciseEntry.weightValue} ${exerciseEntry.weightUnit ?? ""}`}
                  </p>
                  {exerciseEntry.notes && <p className="text-slate-400">{exerciseEntry.notes}</p>}
                </div>
                <div className="flex shrink-0 gap-2">
                  <Button variant="secondary" onClick={() => setEditingExerciseId(exerciseEntry.id)}>
                    Edit
                  </Button>
                  <Button
                    variant="danger"
                    onClick={() => void handleRemoveExercise(exerciseEntry.id)}
                    isLoading={pendingExerciseId === exerciseEntry.id}
                  >
                    Remove
                  </Button>
                </div>
              </div>
            ),
          )}
        </div>

        <div className="mt-4 border-t border-slate-100 pt-4">
          <ExerciseEntryForm trainingSessionId={session.id} exerciseCatalog={exerciseCatalog} onDone={reloadSession} />
        </div>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right text-slate-900">{value ?? "—"}</dd>
    </div>
  );
}

/** Shared form for adding a new exercise entry (no `exerciseEntry` prop) or editing an existing one - mirrors QuoteDetailPage's LineItemForm. */
function ExerciseEntryForm({
  trainingSessionId,
  exerciseCatalog,
  exerciseEntry,
  onDone,
  onCancel,
}: {
  trainingSessionId: string;
  exerciseCatalog: ExerciseDto[];
  exerciseEntry?: TrainingSessionExerciseDto;
  onDone: () => void;
  onCancel?: () => void;
}) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<TrainingSessionExerciseFormValues>({
    resolver: zodResolver(trainingSessionExerciseSchema),
    defaultValues: exerciseEntry
      ? {
          exerciseId: exerciseEntry.exerciseId ?? "",
          exerciseName: exerciseEntry.exerciseName,
          setsCompleted: String(exerciseEntry.setsCompleted),
          repsCompleted: exerciseEntry.repsCompleted,
          weightValue: exerciseEntry.weightValue != null ? String(exerciseEntry.weightValue) : "",
          weightUnit: exerciseEntry.weightUnit ?? "",
          notes: exerciseEntry.notes ?? "",
        }
      : { setsCompleted: "1" },
  });

  function onExerciseChange(exerciseId: string) {
    const exercise = exerciseCatalog.find((candidate) => candidate.id === exerciseId);
    if (exercise) {
      setValue("exerciseName", exercise.name);
    }
  }

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const request = {
        exerciseId: blankToUndefined(values.exerciseId),
        exerciseName: values.exerciseName,
        setsCompleted: toRequiredNumber(values.setsCompleted),
        repsCompleted: values.repsCompleted,
        weightValue: toOptionalNumber(values.weightValue),
        weightUnit: blankToUndefined(values.weightUnit),
        notes: blankToUndefined(values.notes),
      };
      if (exerciseEntry) {
        await updateTrainingSessionExercise(trainingSessionId, exerciseEntry.id, request);
      } else {
        await addTrainingSessionExercise(trainingSessionId, request);
        reset({ setsCompleted: "1", exerciseId: "", exerciseName: "", repsCompleted: "", weightValue: "", weightUnit: "", notes: "" });
      }
      onDone();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-3">
      {formError && <Alert variant="error">{formError}</Alert>}
      <div className="grid gap-3 sm:grid-cols-6">
        <div className="sm:col-span-2">
          <Select
            label="Catalog exercise"
            placeholder="Custom / off-catalog"
            options={exerciseCatalog.map((exercise) => ({ value: exercise.id, label: exercise.name }))}
            error={errors.exerciseId?.message}
            {...register("exerciseId", { onChange: (event) => onExerciseChange(event.target.value) })}
          />
        </div>
        <TextField label="Sets" type="number" min={1} step={1} error={errors.setsCompleted?.message} {...register("setsCompleted")} />
        <TextField label="Reps" placeholder="12,10,8" error={errors.repsCompleted?.message} {...register("repsCompleted")} />
        <TextField label="Weight" type="number" min={0} step="any" error={errors.weightValue?.message} {...register("weightValue")} />
        <TextField label="Unit" placeholder="LB" error={errors.weightUnit?.message} {...register("weightUnit")} />
        <div className="flex items-end gap-2">
          <Button type="submit" isLoading={isSubmitting}>
            {exerciseEntry ? "Save" : "Add"}
          </Button>
          {onCancel && (
            <Button type="button" variant="secondary" onClick={onCancel}>
              Cancel
            </Button>
          )}
        </div>
      </div>
      <TextField label="Exercise name" error={errors.exerciseName?.message} {...register("exerciseName")} />
      <TextField label="Notes" error={errors.notes?.message} {...register("notes")} />
    </form>
  );
}
