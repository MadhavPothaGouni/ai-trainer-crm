import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  deleteEquipmentReservation,
  getEquipmentReservation,
  updateEquipmentReservation,
  updateEquipmentReservationStatus,
} from "../../api/equipmentReservations";
import { getEquipment } from "../../api/equipment";
import { getContact } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateEquipmentReservationSchema, type UpdateEquipmentReservationFormValues } from "../../lib/validation";
import {
  EQUIPMENT_RESERVATION_STATUSES,
  type ContactDto,
  type EquipmentDto,
  type EquipmentReservationDto,
  type EquipmentReservationStatus,
} from "../../types/api";
import { EquipmentReservationStatusBadge } from "./EquipmentReservationListPage";

/** datetime-local wants "YYYY-MM-DDTHH:mm" in LOCAL time - same conversion ShiftDetailPage's edit form establishes. */
function toDatetimeLocalValue(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export default function EquipmentReservationDetailPage() {
  const { equipmentReservationId } = useParams<{ equipmentReservationId: string }>();
  const navigate = useNavigate();
  const [reservation, setReservation] = useState<EquipmentReservationDto | null>(null);
  const [equipment, setEquipment] = useState<EquipmentDto | null>(null);
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
  } = useForm<UpdateEquipmentReservationFormValues>({ resolver: zodResolver(updateEquipmentReservationSchema) });

  useEffect(() => {
    if (!equipmentReservationId) return;
    let cancelled = false;
    getEquipmentReservation(equipmentReservationId)
      .then((data) => {
        if (cancelled) return;
        setReservation(data);
        reset({
          contactId: data.contactId ?? "",
          startsAt: toDatetimeLocalValue(data.startsAt),
          endsAt: toDatetimeLocalValue(data.endsAt),
          notes: data.notes ?? "",
        });
        getEquipment(data.equipmentId).then(setEquipment).catch(() => undefined);
        if (data.contactId) {
          getContact(data.contactId).then(setContact).catch(() => undefined);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this equipment reservation.");
      });
    return () => {
      cancelled = true;
    };
  }, [equipmentReservationId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!equipmentReservationId) return;
    setFormError(null);
    try {
      const updated = await updateEquipmentReservation(equipmentReservationId, {
        contactId: blankToUndefined(values.contactId),
        startsAt: new Date(values.startsAt).toISOString(),
        endsAt: new Date(values.endsAt).toISOString(),
        notes: blankToUndefined(values.notes),
      });
      setReservation(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!equipmentReservationId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateEquipmentReservationStatus(equipmentReservationId, { status: status as EquipmentReservationStatus });
      setReservation(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!equipmentReservationId || !window.confirm("Delete this equipment reservation? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteEquipmentReservation(equipmentReservationId);
      navigate("/equipment-reservations");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this equipment reservation.");
      setIsDeleting(false);
    }
  }

  if (error && !reservation) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!reservation) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/equipment-reservations" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Equipment Reservations
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">
              {equipment ? (
                <Link to={`/equipment/${equipment.id}`} className="hover:underline">
                  {equipment.name}
                </Link>
              ) : (
                "Equipment reservation"
              )}
            </h1>
            <EquipmentReservationStatusBadge status={reservation.status} />
          </div>
          {contact && (
            <p className="mt-1 text-sm text-slate-500">
              For{" "}
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
            <Row label="Starts" value={new Date(reservation.startsAt).toLocaleString()} />
            <Row label="Ends" value={new Date(reservation.endsAt).toLocaleString()} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">Reservations move freely between statuses - re-confirming a cancelled one is a normal correction.</p>
          <div className="mt-3">
            <Select
              label="Status"
              options={EQUIPMENT_RESERVATION_STATUSES.map((status) => ({ value: status, label: status }))}
              value={reservation.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit reservation</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

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
