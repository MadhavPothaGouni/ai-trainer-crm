import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createRoom } from "../../api/rooms";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createRoomSchema, toOptionalNumber, type CreateRoomFormValues } from "../../lib/validation";

export default function RoomCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateRoomFormValues>({ resolver: zodResolver(createRoomSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const room = await createRoom({
        label: values.label,
        location: blankToUndefined(values.location),
        capacity: toOptionalNumber(values.capacity),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/rooms/${room.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New room</h1>
        <p className="mt-1 text-sm text-slate-500">Add a bookable space to the catalog - bookings get made against it.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Label" placeholder="Studio A" error={errors.label?.message} {...register("label")} />
          <TextField label="Capacity" type="number" min={1} step="1" error={errors.capacity?.message} {...register("capacity")} />
        </div>

        <TextField label="Location" placeholder="Main floor" error={errors.location?.message} {...register("location")} />

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/rooms")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Add room
          </Button>
        </div>
      </form>
    </div>
  );
}
