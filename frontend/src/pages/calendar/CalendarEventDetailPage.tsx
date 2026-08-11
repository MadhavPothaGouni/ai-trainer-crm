import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  addAttendee,
  deleteCalendarEvent,
  getCalendarEvent,
  listAttendees,
  removeAttendee,
  updateAttendeeResponse,
  updateCalendarEvent,
} from "../../api/calendarEvents";
import { listUsers } from "../../api/users";
import { RelatedToPicker } from "../../components/crm/RelatedToPicker";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import {
  addAttendeeSchema,
  blankToUndefined,
  createCalendarEventSchema,
  type AddAttendeeFormValues,
  type CreateCalendarEventFormValues,
} from "../../lib/validation";
import {
  CALENDAR_ATTENDEE_RESPONSE_STATUSES,
  type CalendarEventAttendeeDto,
  type CalendarEventDto,
  type CrmRecordType,
  type UserDto,
} from "../../types/api";

function toDatetimeLocal(iso: string): string {
  return new Date(iso).toISOString().slice(0, 16);
}

const RESPONSE_CLASSES: Record<string, string> = {
  NEEDS_ACTION: "bg-slate-100 text-slate-600",
  ACCEPTED: "bg-emerald-100 text-emerald-700",
  DECLINED: "bg-red-100 text-red-700",
  TENTATIVE: "bg-amber-100 text-amber-700",
};

export default function CalendarEventDetailPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const navigate = useNavigate();
  const [event, setEvent] = useState<CalendarEventDto | null>(null);
  const [attendees, setAttendees] = useState<CalendarEventAttendeeDto[]>([]);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const [attendeeError, setAttendeeError] = useState<string | null>(null);

  function loadAttendees() {
    if (!eventId) return;
    listAttendees(eventId)
      .then(setAttendees)
      .catch((err: unknown) => setAttendeeError(err instanceof ApiError ? err.message : "Could not load attendees."));
  }

  useEffect(() => {
    if (!eventId) return;
    let cancelled = false;
    getCalendarEvent(eventId)
      .then((data) => {
        if (!cancelled) setEvent(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this event.");
      });
    listUsers({ size: 100 })
      .then((res) => {
        if (!cancelled) setUsers(res.content);
      })
      .catch(() => undefined);
    loadAttendees();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<CreateCalendarEventFormValues>({ resolver: zodResolver(createCalendarEventSchema) });

  useEffect(() => {
    if (!event) return;
    reset({
      title: event.title,
      description: event.description ?? "",
      location: event.location ?? "",
      startAt: toDatetimeLocal(event.startAt),
      endAt: toDatetimeLocal(event.endAt),
      allDay: event.allDay,
      relatedToType: event.relatedToType ?? "",
      relatedToId: event.relatedToId ?? "",
    });
  }, [event, reset]);

  const relatedToType = watch("relatedToType") ?? "";
  const relatedToId = watch("relatedToId") ?? "";

  const onSaveEdits = handleSubmit(async (values) => {
    if (!eventId) return;
    setEditError(null);
    try {
      const updated = await updateCalendarEvent(eventId, {
        title: values.title,
        description: blankToUndefined(values.description),
        location: blankToUndefined(values.location),
        startAt: new Date(values.startAt).toISOString(),
        endAt: new Date(values.endAt).toISOString(),
        allDay: values.allDay ?? false,
        relatedToType: blankToUndefined(values.relatedToType) as CrmRecordType | undefined,
        relatedToId: blankToUndefined(values.relatedToId),
      });
      setEvent(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  const {
    register: registerAttendee,
    handleSubmit: handleAttendeeSubmit,
    reset: resetAttendeeForm,
    formState: { errors: attendeeFormErrors, isSubmitting: isAddingAttendee },
  } = useForm<AddAttendeeFormValues>({ resolver: zodResolver(addAttendeeSchema) });

  const onAddAttendee = handleAttendeeSubmit(async (values) => {
    if (!eventId) return;
    setAttendeeError(null);
    try {
      await addAttendee(eventId, {
        userId: blankToUndefined(values.userId),
        externalEmail: blankToUndefined(values.externalEmail),
      });
      resetAttendeeForm({ userId: "", externalEmail: "" });
      loadAttendees();
    } catch (err) {
      setAttendeeError(err instanceof ApiError ? err.message : "Could not add this attendee.");
    }
  });

  async function handleResponseChange(attendeeId: string, responseStatus: string) {
    if (!eventId) return;
    try {
      await updateAttendeeResponse(eventId, attendeeId, { responseStatus: responseStatus as CalendarEventAttendeeDto["responseStatus"] });
      loadAttendees();
    } catch (err) {
      setAttendeeError(err instanceof ApiError ? err.message : "Could not update that response.");
    }
  }

  async function handleRemoveAttendee(attendeeId: string) {
    if (!eventId) return;
    try {
      await removeAttendee(eventId, attendeeId);
      loadAttendees();
    } catch (err) {
      setAttendeeError(err instanceof ApiError ? err.message : "Could not remove that attendee.");
    }
  }

  async function handleDelete() {
    if (!eventId || !window.confirm("Delete this event? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteCalendarEvent(eventId);
      navigate("/calendar");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this event.");
      setIsDeleting(false);
    }
  }

  if (error && !event) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!event) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/calendar" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Calendar
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{event.title}</h1>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Overview</h2>
        <dl className="mt-3 flex flex-col gap-2 text-sm">
          <Row label="Starts" value={event.allDay ? new Date(event.startAt).toLocaleDateString() : new Date(event.startAt).toLocaleString()} />
          <Row label="Ends" value={event.allDay ? new Date(event.endAt).toLocaleDateString() : new Date(event.endAt).toLocaleString()} />
          <Row label="Location" value={event.location} />
        </dl>
      </div>

      {event.description && (
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Description</h2>
          <p className="mt-3 whitespace-pre-wrap text-sm text-slate-900">{event.description}</p>
        </div>
      )}

      <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Attendees</h2>
        {attendeeError && <Alert variant="error">{attendeeError}</Alert>}

        <div className="overflow-hidden rounded-md border border-slate-200">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-3 py-2 font-medium">Attendee</th>
                <th className="px-3 py-2 font-medium">Response</th>
                <th className="px-3 py-2 font-medium"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {attendees.length === 0 && (
                <tr>
                  <td className="px-3 py-4 text-center text-slate-400" colSpan={3}>
                    No attendees yet.
                  </td>
                </tr>
              )}
              {attendees.map((attendee) => (
                <tr key={attendee.id}>
                  <td className="px-3 py-2 text-slate-900">
                    {attendee.userId ? users.find((u) => u.id === attendee.userId)?.fullName ?? "Teammate" : attendee.externalEmail}
                  </td>
                  <td className="px-3 py-2">
                    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${RESPONSE_CLASSES[attendee.responseStatus]}`}>
                      {attendee.responseStatus.replace("_", " ")}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-right">
                    <div className="flex items-center justify-end gap-2">
                      <select
                        className="rounded-md border border-slate-300 px-2 py-1 text-xs"
                        value={attendee.responseStatus}
                        onChange={(e) => void handleResponseChange(attendee.id, e.target.value)}
                      >
                        {CALENDAR_ATTENDEE_RESPONSE_STATUSES.map((status) => (
                          <option key={status} value={status}>
                            {status.replace("_", " ")}
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        onClick={() => void handleRemoveAttendee(attendee.id)}
                        className="text-xs text-red-600 hover:underline"
                      >
                        Remove
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <form onSubmit={onAddAttendee} noValidate className="grid gap-3 sm:grid-cols-3">
          <Select
            label="Teammate"
            placeholder="Choose a teammate"
            options={users.map((u) => ({ value: u.id, label: u.fullName }))}
            error={attendeeFormErrors.userId?.message}
            {...registerAttendee("userId")}
          />
          <TextField
            label="Or external email"
            placeholder="guest@example.com"
            error={attendeeFormErrors.externalEmail?.message}
            {...registerAttendee("externalEmail")}
          />
          <div className="flex items-end">
            <Button type="submit" isLoading={isAddingAttendee} className="w-full">
              Add attendee
            </Button>
          </div>
        </form>
      </div>

      <form onSubmit={onSaveEdits} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit event</h2>

        {editError && <Alert variant="error">{editError}</Alert>}

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

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right text-slate-900">{value ?? "—"}</dd>
    </div>
  );
}
