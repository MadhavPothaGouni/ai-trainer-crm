import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { createEquipmentReservation } from "../../api/equipmentReservations";
import { listEquipment } from "../../api/equipment";
import { listContacts } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createEquipmentReservationSchema, type CreateEquipmentReservationFormValues } from "../../lib/validation";
import type { ContactDto, EquipmentDto } from "../../types/api";

export default function EquipmentReservationCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedEquipmentId = searchParams.get("equipmentId") ?? "";
  const [formError, setFormError] = useState<string | null>(null);
  const [equipment, setEquipment] = useState<EquipmentDto[]>([]);
  const [contacts, setContacts] = useState<ContactDto[]>([]);

  useEffect(() => {
    listEquipment({ size: 100, sort: "name,asc" })
      .then((res) => setEquipment(res.content.filter((item) => item.status === "ACTIVE")))
      .catch(() => setEquipment([]));
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateEquipmentReservationFormValues>({
    resolver: zodResolver(createEquipmentReservationSchema),
    defaultValues: { equipmentId: preselectedEquipmentId },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const reservation = await createEquipmentReservation({
        equipmentId: values.equipmentId,
        contactId: blankToUndefined(values.contactId),
        startsAt: new Date(values.startsAt).toISOString(),
        endsAt: new Date(values.endsAt).toISOString(),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/equipment-reservations/${reservation.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Reserve equipment</h1>
        <p className="mt-1 text-sm text-slate-500">Assigned to you by default. The client is optional - equipment can also be reserved for internal use.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Equipment"
            placeholder="Select equipment"
            options={equipment.map((item) => ({ value: item.id, label: item.name }))}
            error={errors.equipmentId?.message}
            {...register("equipmentId")}
          />
          <Select
            label="Client (optional)"
            placeholder="No client"
            options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
            error={errors.contactId?.message}
            {...register("contactId")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Starts at" type="datetime-local" error={errors.startsAt?.message} {...register("startsAt")} />
          <TextField label="Ends at" type="datetime-local" error={errors.endsAt?.message} {...register("endsAt")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/equipment-reservations")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Reserve equipment
          </Button>
        </div>
      </form>
    </div>
  );
}
