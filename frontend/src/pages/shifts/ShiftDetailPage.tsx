import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteShift, getShift, updateShift, updateShiftStatus } from "../../api/shifts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateShiftSchema, type UpdateShiftFormValues } from "../../lib/validation";
import { SHIFT_STATUSES, type ShiftDto, type ShiftStatus } from "../../types/api";
import { ShiftStatusBadge } from "./ShiftListPage";

/** datetime-local wants "YYYY-MM-DDTHH:mm" in LOCAL time - same conversion ClassSessionDetailPage's edit form establishes. */
function toDatetimeLocalValue(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export default function ShiftDetailPage() {
  const { shiftId } = useParams<{ shiftId: string }>();
  const navigate = useNavigate();
  const [shift, setShift] = useState<ShiftDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateShiftFormValues>({ resolver: zodResolver(updateShiftSchema) });

  useEffect(() => {
    if (!shiftId) return;
    let cancelled = false;
    getShift(shiftId)
      .then((data) => {
        if (cancelled) return;
        setShift(data);
        reset({
          shiftDate: data.shiftDate,
          startsAt: toDatetimeLocalValue(data.startsAt),
          endsAt: toDatetimeLocalValue(data.endsAt),
          notes: data.notes ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this shift.");
      });
    return () => {
      cancelled = true;
    };
  }, [shiftId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!shiftId) return;
    setFormError(null);
    try {
      const updated = await updateShift(shiftId, {
        shiftDate: values.shiftDate,
        startsAt: new Date(values.startsAt).toISOString(),
        endsAt: new Date(values.endsAt).toISOString(),
        notes: blankToUndefined(values.notes),
      });
      setShift(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!shiftId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateShiftStatus(shiftId, { status: status as ShiftStatus });
      setShift(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!shiftId || !window.confirm("Delete this shift? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteShift(shiftId);
      navigate("/shifts");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this shift.");
      setIsDeleting(false);
    }
  }

  if (error && !shift) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!shift) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/shifts" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Shifts
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">Shift on {shift.shiftDate}</h1>
            <ShiftStatusBadge status={shift.status} />
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
            <Row label="Starts" value={new Date(shift.startsAt).toLocaleString()} />
            <Row label="Ends" value={new Date(shift.endsAt).toLocaleString()} />
            <Row label="Clocked in" value={shift.clockInAt ? new Date(shift.clockInAt).toLocaleString() : "—"} />
            <Row label="Clocked out" value={shift.clockOutAt ? new Date(shift.clockOutAt).toLocaleString() : "—"} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">
            Moving to In Progress or Completed stamps clock-in/out the first time only - later corrections won't move an already-set time.
          </p>
          <div className="mt-3">
            <Select
              label="Status"
              options={SHIFT_STATUSES.map((status) => ({ value: status, label: status.replace("_", " ") }))}
              value={shift.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit shift</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Shift date" type="date" error={errors.shiftDate?.message} {...register("shiftDate")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Starts at" type="datetime-local" error={errors.startsAt?.message} {...register("startsAt")} />
          <TextField label="Ends at" type="datetime-local" error={errors.endsAt?.message} {...register("endsAt")} />
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
