import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { createRoomBooking } from "../../api/roomBookings";
import { listRooms } from "../../api/rooms";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createRoomBookingSchema, type CreateRoomBookingFormValues } from "../../lib/validation";
import type { RoomDto } from "../../types/api";

export default function RoomBookingCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedRoomId = searchParams.get("roomId") ?? "";
  const [formError, setFormError] = useState<string | null>(null);
  const [rooms, setRooms] = useState<RoomDto[]>([]);

  useEffect(() => {
    listRooms({ size: 100, sort: "label,asc" })
      .then((res) => setRooms(res.content.filter((room) => room.status === "ACTIVE")))
      .catch(() => setRooms([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateRoomBookingFormValues>({
    resolver: zodResolver(createRoomBookingSchema),
    defaultValues: { roomId: preselectedRoomId },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const booking = await createRoomBooking({
        roomId: values.roomId,
        purpose: values.purpose,
        startsAt: new Date(values.startsAt).toISOString(),
        endsAt: new Date(values.endsAt).toISOString(),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/room-bookings/${booking.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Book a room</h1>
        <p className="mt-1 text-sm text-slate-500">Assigned to you by default - overlapping CONFIRMED bookings for the same room are rejected.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Room"
            placeholder="Select a room"
            options={rooms.map((room) => ({ value: room.id, label: room.label }))}
            error={errors.roomId?.message}
            {...register("roomId")}
          />
          <TextField label="Purpose" placeholder="HIIT class" error={errors.purpose?.message} {...register("purpose")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Starts at" type="datetime-local" error={errors.startsAt?.message} {...register("startsAt")} />
          <TextField label="Ends at" type="datetime-local" error={errors.endsAt?.message} {...register("endsAt")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/room-bookings")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Book room
          </Button>
        </div>
      </form>
    </div>
  );
}
