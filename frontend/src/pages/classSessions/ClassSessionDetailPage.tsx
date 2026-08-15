import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { getGroupClass } from "../../api/groupClasses";
import {
  createClassAttendance,
  deleteClassAttendance,
  listClassAttendances,
  updateClassAttendanceStatus,
} from "../../api/classAttendances";
import { deleteClassSession, getClassSession, updateClassSession, updateClassSessionStatus } from "../../api/classSessions";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  createClassAttendanceSchema,
  toOptionalNumber,
  updateClassSessionSchema,
  type CreateClassAttendanceFormValues,
  type UpdateClassSessionFormValues,
} from "../../lib/validation";
import {
  CLASS_ATTENDANCE_STATUSES,
  CLASS_SESSION_STATUSES,
  type ClassAttendanceDto,
  type ClassAttendanceStatus,
  type ClassSessionDto,
  type ClassSessionStatus,
  type ContactDto,
  type GroupClassDto,
} from "../../types/api";
import { ClassSessionStatusBadge } from "./ClassSessionListPage";

/** datetime-local wants "YYYY-MM-DDTHH:mm" in LOCAL time - same conversion TrainingSessionDetailPage/CalendarEventDetailPage's edit forms already establish. */
function toDatetimeLocalValue(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export default function ClassSessionDetailPage() {
  const { classSessionId } = useParams<{ classSessionId: string }>();
  const navigate = useNavigate();
  const [session, setSession] = useState<ClassSessionDto | null>(null);
  const [groupClass, setGroupClass] = useState<GroupClassDto | null>(null);
  const [roster, setRoster] = useState<ClassAttendanceDto[]>([]);
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [rosterError, setRosterError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  function reloadRoster(sessionId: string) {
    listClassAttendances({ size: 100, sort: "registeredAt,asc" })
      .then((res) => setRoster(res.content.filter((attendance) => attendance.classSessionId === sessionId)))
      .catch(() => undefined);
  }

  useEffect(() => {
    if (!classSessionId) return;
    let cancelled = false;
    getClassSession(classSessionId)
      .then((data) => {
        if (cancelled) return;
        setSession(data);
        getGroupClass(data.groupClassId).then(setGroupClass).catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this session.");
      });
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => {
        if (!cancelled) setContacts(res.content);
      })
      .catch(() => undefined);
    reloadRoster(classSessionId);
    return () => {
      cancelled = true;
    };
  }, [classSessionId]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<UpdateClassSessionFormValues>({ resolver: zodResolver(updateClassSessionSchema) });

  useEffect(() => {
    if (!session) return;
    reset({
      startsAt: toDatetimeLocalValue(session.startsAt),
      endsAt: toDatetimeLocalValue(session.endsAt),
      capacityOverride: session.capacityOverride != null ? String(session.capacityOverride) : "",
      notes: session.notes ?? "",
    });
  }, [session, reset]);

  const {
    register: registerAttendee,
    handleSubmit: handleAttendeeSubmit,
    reset: resetAttendeeForm,
    formState: { errors: attendeeErrors, isSubmitting: isAddingAttendee },
  } = useForm<CreateClassAttendanceFormValues>({ resolver: zodResolver(createClassAttendanceSchema) });

  const onSaveEdits = handleSubmit(async (values) => {
    if (!classSessionId) return;
    setEditError(null);
    try {
      const updated = await updateClassSession(classSessionId, {
        startsAt: new Date(values.startsAt).toISOString(),
        endsAt: new Date(values.endsAt).toISOString(),
        capacityOverride: toOptionalNumber(values.capacityOverride),
        notes: blankToUndefined(values.notes),
      });
      setSession(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  const onAddAttendee = handleAttendeeSubmit(async (values) => {
    if (!classSessionId) return;
    setRosterError(null);
    try {
      await createClassAttendance({ classSessionId, contactId: values.contactId, notes: blankToUndefined(values.notes) });
      resetAttendeeForm({ contactId: "", notes: "" });
      reloadRoster(classSessionId);
    } catch (err) {
      setRosterError(err instanceof ApiError ? err.message : "Could not add this attendee.");
    }
  });

  async function handleAttendanceStatusChange(attendanceId: string, status: string) {
    if (!classSessionId) return;
    setRosterError(null);
    try {
      await updateClassAttendanceStatus(attendanceId, { status: status as ClassAttendanceStatus });
      reloadRoster(classSessionId);
    } catch (err) {
      setRosterError(err instanceof ApiError ? err.message : "Could not update this attendee's status.");
    }
  }

  async function handleRemoveAttendee(attendanceId: string) {
    if (!classSessionId || !window.confirm("Remove this attendee from the roster?")) return;
    try {
      await deleteClassAttendance(attendanceId);
      reloadRoster(classSessionId);
    } catch (err) {
      setRosterError(err instanceof ApiError ? err.message : "Could not remove this attendee.");
    }
  }

  async function handleStatusChange(status: string) {
    if (!classSessionId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateClassSessionStatus(classSessionId, { status: status as ClassSessionStatus });
      setSession(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!classSessionId || !window.confirm("Delete this session? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteClassSession(classSessionId);
      navigate("/class-sessions");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this session.");
      setIsDeleting(false);
    }
  }

  if (error && !session) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!session) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const capacity = session.capacityOverride ?? groupClass?.capacity ?? null;
  const activeRosterSize = roster.filter((a) => a.status === "REGISTERED" || a.status === "ATTENDED").length;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/class-sessions" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Class Sessions
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">
              {groupClass ? (
                <Link to={`/group-classes/${groupClass.id}`} className="hover:underline">
                  {groupClass.name}
                </Link>
              ) : (
                "Class session"
              )}
            </h1>
            <ClassSessionStatusBadge status={session.status} />
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
            <Row label="Starts" value={new Date(session.startsAt).toLocaleString()} />
            <Row label="Ends" value={new Date(session.endsAt).toLocaleString()} />
            <Row label="Roster" value={`${activeRosterSize} / ${capacity ?? "Unlimited"}`} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">Sessions move freely between statuses - reinstating a cancelled session is a normal correction.</p>
          <div className="mt-3">
            <Select
              label="Status"
              options={CLASS_SESSION_STATUSES.map((status) => ({ value: status, label: status }))}
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
          <TextField label="Starts at" type="datetime-local" error={errors.startsAt?.message} {...register("startsAt")} />
          <TextField label="Ends at" type="datetime-local" error={errors.endsAt?.message} {...register("endsAt")} />
        </div>

        <TextField
          label="Capacity override"
          type="number"
          min={0}
          step={1}
          placeholder="Leave blank to use the class type's capacity"
          error={errors.capacityOverride?.message}
          {...register("capacityOverride")}
        />

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Roster</h2>

        {rosterError && (
          <div className="mt-3">
            <Alert variant="error">{rosterError}</Alert>
          </div>
        )}

        <div className="mt-3 overflow-hidden rounded-lg border border-slate-200">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-2 font-medium">Client</th>
                <th className="px-4 py-2 font-medium">Status</th>
                <th className="px-4 py-2 font-medium">Checked in</th>
                <th className="px-4 py-2 font-medium"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {roster.length === 0 && (
                <tr>
                  <td className="px-4 py-4 text-center text-slate-400" colSpan={4}>
                    No one is registered yet.
                  </td>
                </tr>
              )}
              {roster.map((attendance) => {
                const contact = contacts.find((c) => c.id === attendance.contactId);
                return (
                  <tr key={attendance.id}>
                    <td className="px-4 py-2 text-slate-900">{contact?.fullName ?? "Client"}</td>
                    <td className="px-4 py-2">
                      <select
                        className="rounded border border-slate-300 px-2 py-1 text-sm"
                        value={attendance.status}
                        onChange={(event) => void handleAttendanceStatusChange(attendance.id, event.target.value)}
                      >
                        {CLASS_ATTENDANCE_STATUSES.map((status) => (
                          <option key={status} value={status}>
                            {status.replace("_", " ")}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td className="px-4 py-2 text-slate-600">{attendance.checkedInAt ? new Date(attendance.checkedInAt).toLocaleString() : "—"}</td>
                    <td className="px-4 py-2 text-right">
                      <button
                        type="button"
                        className="text-xs font-medium text-rose-600 hover:underline"
                        onClick={() => void handleRemoveAttendee(attendance.id)}
                      >
                        Remove
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <form onSubmit={onAddAttendee} noValidate className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <Select
              label="Add client"
              placeholder="Select a contact"
              options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
              error={attendeeErrors.contactId?.message}
              {...registerAttendee("contactId")}
            />
          </div>
          <div className="flex-1">
            <TextField label="Notes" error={attendeeErrors.notes?.message} {...registerAttendee("notes")} />
          </div>
          <Button type="submit" isLoading={isAddingAttendee}>
            Add to roster
          </Button>
        </form>
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
