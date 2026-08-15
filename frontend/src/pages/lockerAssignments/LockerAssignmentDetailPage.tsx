import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  deleteLockerAssignment,
  getLockerAssignment,
  updateLockerAssignment,
  updateLockerAssignmentStatus,
} from "../../api/lockerAssignments";
import { getContact } from "../../api/contacts";
import { getLocker } from "../../api/lockers";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateLockerAssignmentSchema, type UpdateLockerAssignmentFormValues } from "../../lib/validation";
import {
  LOCKER_ASSIGNMENT_STATUSES,
  type ContactDto,
  type LockerAssignmentDto,
  type LockerAssignmentStatus,
  type LockerDto,
} from "../../types/api";
import { LockerAssignmentStatusBadge } from "./LockerAssignmentListPage";

export default function LockerAssignmentDetailPage() {
  const { lockerAssignmentId } = useParams<{ lockerAssignmentId: string }>();
  const navigate = useNavigate();
  const [assignment, setAssignment] = useState<LockerAssignmentDto | null>(null);
  const [locker, setLocker] = useState<LockerDto | null>(null);
  const [contact, setContact] = useState<ContactDto | null>(null);
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
  } = useForm<UpdateLockerAssignmentFormValues>({ resolver: zodResolver(updateLockerAssignmentSchema) });

  useEffect(() => {
    if (!lockerAssignmentId) return;
    let cancelled = false;
    getLockerAssignment(lockerAssignmentId)
      .then((data) => {
        if (cancelled) return;
        setAssignment(data);
        reset({ expiresAt: data.expiresAt ?? "", notes: data.notes ?? "" });
        getLocker(data.lockerId).then(setLocker).catch(() => undefined);
        getContact(data.contactId).then(setContact).catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this locker assignment.");
      });
    return () => {
      cancelled = true;
    };
  }, [lockerAssignmentId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!lockerAssignmentId) return;
    setFormError(null);
    try {
      const updated = await updateLockerAssignment(lockerAssignmentId, {
        expiresAt: blankToUndefined(values.expiresAt),
        notes: blankToUndefined(values.notes),
      });
      setAssignment(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!lockerAssignmentId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateLockerAssignmentStatus(lockerAssignmentId, { status: status as LockerAssignmentStatus });
      setAssignment(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!lockerAssignmentId || !window.confirm("Delete this locker assignment? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteLockerAssignment(lockerAssignmentId);
      navigate("/locker-assignments");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this locker assignment.");
      setIsDeleting(false);
    }
  }

  if (error && !assignment) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!assignment) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/locker-assignments" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Locker Assignments
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">
              {locker ? (
                <Link to={`/lockers/${locker.id}`} className="hover:underline">
                  {locker.label}
                </Link>
              ) : (
                "Locker assignment"
              )}
            </h1>
            <LockerAssignmentStatusBadge status={assignment.status} />
          </div>
          {contact && (
            <p className="mt-1 text-sm text-slate-500">
              Assigned to{" "}
              <Link to={`/contacts/${contact.id}`} className="text-slate-700 hover:underline">
                {contact.fullName}
              </Link>
            </p>
          )}
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
            <Row label="Assigned" value={new Date(assignment.assignedAt).toLocaleString()} />
            <Row label="Expires" value={assignment.expiresAt ?? "—"} />
            <Row label="Returned" value={assignment.returnedAt ? new Date(assignment.returnedAt).toLocaleString() : "Not yet"} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">
            Assignments move freely between statuses - reactivating a returned or expired assignment is a normal correction.
          </p>
          <div className="mt-3">
            <Select
              label="Status"
              options={LOCKER_ASSIGNMENT_STATUSES.map((status) => ({ value: status, label: status }))}
              value={assignment.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit assignment</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Expires on" type="date" error={errors.expiresAt?.message} {...register("expiresAt")} />
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
