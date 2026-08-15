import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteRoom, getRoom, updateRoom } from "../../api/rooms";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, toOptionalNumber, updateRoomSchema, type UpdateRoomFormValues } from "../../lib/validation";
import { ROOM_STATUSES, type RoomDto, type RoomStatus } from "../../types/api";

export default function RoomDetailPage() {
  const { roomId } = useParams<{ roomId: string }>();
  const navigate = useNavigate();
  const [room, setRoom] = useState<RoomDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateRoomFormValues>({ resolver: zodResolver(updateRoomSchema) });

  useEffect(() => {
    if (!roomId) return;
    let cancelled = false;
    getRoom(roomId)
      .then((data) => {
        if (cancelled) return;
        setRoom(data);
        reset({
          label: data.label,
          location: data.location ?? "",
          capacity: data.capacity != null ? String(data.capacity) : "",
          status: data.status,
          notes: data.notes ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this room.");
      });
    return () => {
      cancelled = true;
    };
  }, [roomId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!roomId) return;
    setFormError(null);
    try {
      const updated = await updateRoom(roomId, {
        label: values.label,
        location: blankToUndefined(values.location),
        capacity: toOptionalNumber(values.capacity),
        status: values.status as RoomStatus,
        notes: blankToUndefined(values.notes),
      });
      setRoom(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleDelete() {
    if (!roomId || !window.confirm("Delete this room? Existing bookings keep their own record, so this is safe.")) return;
    setIsDeleting(true);
    try {
      await deleteRoom(roomId);
      navigate("/rooms");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this room.");
      setIsDeleting(false);
    }
  }

  if (error && !room) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!room) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/rooms" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Rooms
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{room.label}</h1>
            {room.status === "ACTIVE" ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Out of service</span>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <Link to={`/room-bookings/new?roomId=${room.id}`}>
            <Button variant="secondary">Book room</Button>
          </Link>
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Label" error={errors.label?.message} {...register("label")} />
          <TextField label="Capacity" type="number" min={1} step="1" error={errors.capacity?.message} {...register("capacity")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Location" error={errors.location?.message} {...register("location")} />
          <Select
            label="Status"
            options={ROOM_STATUSES.map((status) => ({ value: status, label: status.replace("_", " ") }))}
            error={errors.status?.message}
            {...register("status")}
          />
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
