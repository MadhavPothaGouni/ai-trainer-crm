import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteRoomBooking, getRoomBooking, updateRoomBooking, updateRoomBookingStatus } from "../../api/roomBookings";
import { getRoom } from "../../api/rooms";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateRoomBookingSchema, type UpdateRoomBookingFormValues } from "../../lib/validation";
import { ROOM_BOOKING_STATUSES, type RoomBookingDto, type RoomBookingStatus, type RoomDto } from "../../types/api";
import { RoomBookingStatusBadge } from "./RoomBookingListPage";

/** datetime-local wants "YYYY-MM-DDTHH:mm" in LOCAL time - same conversion ShiftDetailPage's edit form establishes. */
function toDatetimeLocalValue(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export default function RoomBookingDetailPage() {
  const { roomBookingId } = useParams<{ roomBookingId: string }>();
  const navigate = useNavigate();
  const [booking, setBooking] = useState<RoomBookingDto | null>(null);
  const [room, setRoom] = useState<RoomDto | null>(null);
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
  } = useForm<UpdateRoomBookingFormValues>({ resolver: zodResolver(updateRoomBookingSchema) });

  useEffect(() => {
    if (!roomBookingId) return;
    let cancelled = false;
    getRoomBooking(roomBookingId)
      .then((data) => {
        if (cancelled) return;
        setBooking(data);
        reset({
          purpose: data.purpose,
          startsAt: toDatetimeLocalValue(data.startsAt),
          endsAt: toDatetimeLocalValue(data.endsAt),
          notes: data.notes ?? "",
        });
        getRoom(data.roomId).then(setRoom).catch(() => undefined);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this room booking.");
      });
    return () => {
      cancelled = true;
    };
  }, [roomBookingId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!roomBookingId) return;
    setFormError(null);
    try {
      const updated = await updateRoomBooking(roomBookingId, {
        purpose: values.purpose,
        startsAt: new Date(values.startsAt).toISOString(),
        endsAt: new Date(values.endsAt).toISOString(),
        notes: blankToUndefined(values.notes),
      });
      setBooking(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!roomBookingId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateRoomBookingStatus(roomBookingId, { status: status as RoomBookingStatus });
      setBooking(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!roomBookingId || !window.confirm("Delete this room booking? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteRoomBooking(roomBookingId);
      navigate("/room-bookings");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this room booking.");
      setIsDeleting(false);
    }
  }

  if (error && !booking) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!booking) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/room-bookings" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Room Bookings
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{booking.purpose}</h1>
            <RoomBookingStatusBadge status={booking.status} />
          </div>
          {room && (
            <p className="mt-1 text-sm text-slate-500">
              In{" "}
              <Link to={`/rooms/${room.id}`} className="text-slate-700 hover:underline">
                {room.label}
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
            <Row label="Starts" value={new Date(booking.startsAt).toLocaleString()} />
            <Row label="Ends" value={new Date(booking.endsAt).toLocaleString()} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">
            Re-confirming a cancelled booking re-checks for a scheduling conflict - it's rejected if the room has since been booked over this window.
          </p>
          <div className="mt-3">
            <Select
              label="Status"
              options={ROOM_BOOKING_STATUSES.map((status) => ({ value: status, label: status }))}
              value={booking.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit booking</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Purpose" error={errors.purpose?.message} {...register("purpose")} />

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
