import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { getEquipment } from "../../api/equipment";
import { deleteMaintenanceLog, getMaintenanceLog, updateMaintenanceLog } from "../../api/maintenanceLogs";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, toOptionalNumber, updateMaintenanceLogSchema, type UpdateMaintenanceLogFormValues } from "../../lib/validation";
import { MAINTENANCE_LOG_TYPES, type EquipmentDto, type MaintenanceLogDto, type MaintenanceLogType } from "../../types/api";

/** datetime-local wants "YYYY-MM-DDTHH:mm" in LOCAL time - same conversion ClassSessionDetailPage's edit form already establishes. */
function toDatetimeLocalValue(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export default function MaintenanceLogDetailPage() {
  const { maintenanceLogId } = useParams<{ maintenanceLogId: string }>();
  const navigate = useNavigate();
  const [log, setLog] = useState<MaintenanceLogDto | null>(null);
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
  } = useForm<UpdateMaintenanceLogFormValues>({ resolver: zodResolver(updateMaintenanceLogSchema) });

  useEffect(() => {
    if (!maintenanceLogId) return;
    let cancelled = false;
    getMaintenanceLog(maintenanceLogId)
      .then((data) => {
        if (cancelled) return;
        setLog(data);
        getEquipment(data.equipmentId).then(setEquipment).catch(() => undefined);
        reset({
          performedAt: toDatetimeLocalValue(data.performedAt),
          type: data.type,
          cost: data.cost != null ? String(data.cost) : "",
          notes: data.notes ?? "",
          nextDueDate: data.nextDueDate ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this log.");
      });
    return () => {
      cancelled = true;
    };
  }, [maintenanceLogId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!maintenanceLogId) return;
    setFormError(null);
    try {
      const updated = await updateMaintenanceLog(maintenanceLogId, {
        performedAt: new Date(values.performedAt).toISOString(),
        type: values.type as MaintenanceLogType,
        cost: toOptionalNumber(values.cost),
        notes: blankToUndefined(values.notes),
        nextDueDate: blankToUndefined(values.nextDueDate),
      });
      setLog(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleDelete() {
    if (!maintenanceLogId || !window.confirm("Delete this maintenance log? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteMaintenanceLog(maintenanceLogId);
      navigate("/maintenance-logs");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this log.");
      setIsDeleting(false);
    }
  }

  if (error && !log) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!log) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/maintenance-logs" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Maintenance Logs
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">
            {equipment ? (
              <Link to={`/equipment/${equipment.id}`} className="hover:underline">
                {equipment.name}
              </Link>
            ) : (
              "Maintenance log"
            )}
          </h1>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Performed at" type="datetime-local" error={errors.performedAt?.message} {...register("performedAt")} />
          <Select
            label="Type"
            options={MAINTENANCE_LOG_TYPES.map((type) => ({ value: type, label: type }))}
            error={errors.type?.message}
            {...register("type")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Cost" type="number" min={0} step="any" error={errors.cost?.message} {...register("cost")} />
          <TextField label="Next due date" type="date" error={errors.nextDueDate?.message} {...register("nextDueDate")} />
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
