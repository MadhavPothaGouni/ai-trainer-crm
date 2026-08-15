import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteEquipment, getEquipment, updateEquipment } from "../../api/equipment";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, toOptionalNumber, updateEquipmentSchema, type UpdateEquipmentFormValues } from "../../lib/validation";
import { EQUIPMENT_STATUSES, type EquipmentDto, type EquipmentStatus } from "../../types/api";
import { EquipmentStatusBadge } from "./EquipmentListPage";

export default function EquipmentDetailPage() {
  const { equipmentId } = useParams<{ equipmentId: string }>();
  const navigate = useNavigate();
  const [equipment, setEquipment] = useState<EquipmentDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateEquipmentFormValues>({ resolver: zodResolver(updateEquipmentSchema) });

  useEffect(() => {
    if (!equipmentId) return;
    let cancelled = false;
    getEquipment(equipmentId)
      .then((data) => {
        if (cancelled) return;
        setEquipment(data);
        reset({
          name: data.name,
          category: data.category ?? "",
          serialNumber: data.serialNumber ?? "",
          location: data.location ?? "",
          status: data.status,
          purchaseDate: data.purchaseDate ?? "",
          purchasePrice: data.purchasePrice != null ? String(data.purchasePrice) : "",
          notes: data.notes ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this equipment.");
      });
    return () => {
      cancelled = true;
    };
  }, [equipmentId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!equipmentId) return;
    setFormError(null);
    try {
      const updated = await updateEquipment(equipmentId, {
        name: values.name,
        category: blankToUndefined(values.category),
        serialNumber: blankToUndefined(values.serialNumber),
        location: blankToUndefined(values.location),
        status: values.status as EquipmentStatus,
        purchaseDate: blankToUndefined(values.purchaseDate),
        purchasePrice: toOptionalNumber(values.purchasePrice),
        notes: blankToUndefined(values.notes),
      });
      setEquipment(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleDelete() {
    if (!equipmentId || !window.confirm("Delete this equipment record? Existing maintenance logs keep their own history, so this is safe.")) return;
    setIsDeleting(true);
    try {
      await deleteEquipment(equipmentId);
      navigate("/equipment");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this equipment.");
      setIsDeleting(false);
    }
  }

  if (error && !equipment) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!equipment) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/equipment" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Equipment
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{equipment.name}</h1>
            <EquipmentStatusBadge status={equipment.status} />
          </div>
        </div>
        <div className="flex gap-3">
          <Link to={`/maintenance-logs/new?equipmentId=${equipment.id}`}>
            <Button variant="secondary">Log maintenance</Button>
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
          <TextField label="Name" error={errors.name?.message} {...register("name")} />
          <Select
            label="Status"
            options={EQUIPMENT_STATUSES.map((status) => ({ value: status, label: status.replace("_", " ") }))}
            error={errors.status?.message}
            {...register("status")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Category" error={errors.category?.message} {...register("category")} />
          <TextField label="Serial number" error={errors.serialNumber?.message} {...register("serialNumber")} />
          <TextField label="Location" error={errors.location?.message} {...register("location")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Purchase date" type="date" error={errors.purchaseDate?.message} {...register("purchaseDate")} />
          <TextField label="Purchase price" type="number" min={0} step="any" error={errors.purchasePrice?.message} {...register("purchasePrice")} />
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
