import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteClientGoal, getClientGoal, updateClientGoal, updateClientGoalStatus } from "../../api/clientGoals";
import { listContacts } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, toOptionalNumber, updateClientGoalSchema, type UpdateClientGoalFormValues } from "../../lib/validation";
import {
  CLIENT_GOAL_STATUSES,
  CLIENT_GOAL_TYPES,
  type ClientGoalDto,
  type ClientGoalStatus,
  type ClientGoalType,
  type ContactDto,
} from "../../types/api";
import { ClientGoalStatusBadge } from "./ClientGoalListPage";

export default function ClientGoalDetailPage() {
  const { clientGoalId } = useParams<{ clientGoalId: string }>();
  const navigate = useNavigate();
  const [goal, setGoal] = useState<ClientGoalDto | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!clientGoalId) return;
    let cancelled = false;
    getClientGoal(clientGoalId)
      .then((data) => {
        if (!cancelled) setGoal(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this goal.");
      });
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => {
        if (!cancelled) setContacts(res.content);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [clientGoalId]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<UpdateClientGoalFormValues>({ resolver: zodResolver(updateClientGoalSchema) });

  useEffect(() => {
    if (!goal) return;
    reset({
      title: goal.title,
      goalType: goal.goalType,
      metricUnit: goal.metricUnit ?? "",
      startValue: goal.startValue != null ? String(goal.startValue) : "",
      targetValue: goal.targetValue != null ? String(goal.targetValue) : "",
      currentValue: goal.currentValue != null ? String(goal.currentValue) : "",
      targetDate: goal.targetDate ?? "",
      notes: goal.notes ?? "",
    });
  }, [goal, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!clientGoalId) return;
    setEditError(null);
    try {
      const updated = await updateClientGoal(clientGoalId, {
        title: values.title,
        goalType: values.goalType as ClientGoalType,
        metricUnit: blankToUndefined(values.metricUnit),
        startValue: toOptionalNumber(values.startValue),
        targetValue: toOptionalNumber(values.targetValue),
        currentValue: toOptionalNumber(values.currentValue),
        targetDate: blankToUndefined(values.targetDate),
        notes: blankToUndefined(values.notes),
      });
      setGoal(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!clientGoalId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateClientGoalStatus(clientGoalId, { status: status as ClientGoalStatus });
      setGoal(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!clientGoalId || !window.confirm("Delete this goal? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteClientGoal(clientGoalId);
      navigate("/client-goals");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this goal.");
      setIsDeleting(false);
    }
  }

  if (error && !goal) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!goal) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const linkedContact = contacts.find((contact) => contact.id === goal.contactId);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/client-goals" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Client Goals
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{goal.title}</h1>
            <ClientGoalStatusBadge status={goal.status} />
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
            <Row label="Type" value={goal.goalType.replace("_", " ")} />
            <Row
              label="Progress"
              value={
                goal.startValue != null && goal.targetValue != null
                  ? `${goal.currentValue ?? goal.startValue} -> ${goal.targetValue} ${goal.metricUnit ?? ""}`.trim()
                  : undefined
              }
            />
            <Row label="Target date" value={goal.targetDate ?? undefined} />
            <Row label="Achieved at" value={goal.achievedAt ? new Date(goal.achievedAt).toLocaleString() : undefined} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">Goals move freely between statuses - correcting a mistaken "abandoned" is normal.</p>
          <div className="mt-3">
            <Select
              label="Status"
              options={CLIENT_GOAL_STATUSES.map((status) => ({ value: status, label: status.replace("_", " ") }))}
              value={goal.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <form onSubmit={onSaveEdits} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit goal</h2>

        {editError && <Alert variant="error">{editError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Title" error={errors.title?.message} {...register("title")} />
          <Select
            label="Goal type"
            options={CLIENT_GOAL_TYPES.map((type) => ({ value: type, label: type.replace("_", " ") }))}
            error={errors.goalType?.message}
            {...register("goalType")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Metric unit" error={errors.metricUnit?.message} {...register("metricUnit")} />
          <TextField label="Target date" type="date" error={errors.targetDate?.message} {...register("targetDate")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Start value" type="number" step="any" error={errors.startValue?.message} {...register("startValue")} />
          <TextField label="Target value" type="number" step="any" error={errors.targetValue?.message} {...register("targetValue")} />
          <TextField label="Current value" type="number" step="any" error={errors.currentValue?.message} {...register("currentValue")} />
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

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right text-slate-900">{value ?? "—"}</dd>
    </div>
  );
}
