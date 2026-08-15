import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { listEquipment } from "../../api/equipment";
import { createMaintenanceLog } from "../../api/maintenanceLogs";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createMaintenanceLogSchema, toOptionalNumber, type CreateMaintenanceLogFormValues } from "../../lib/validation";
import { MAINTENANCE_LOG_TYPES, type EquipmentDto, type MaintenanceLogType } from "../../types/api";

export default function MaintenanceLogCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedEquipmentId = searchParams.get("equipmentId") ?? "";
  const [formError, setFormError] = useState<string | null>(null);
  const [equipment, setEquipment] = useState<EquipmentDto[]>([]);

  useEffect(() => {
    listEquipment({ size: 100, sort: "name,asc" })
      .then((res) => setEquipment(res.content))
      .catch(() => setEquipment([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateMaintenanceLogFormValues>({
    resolver: zodResolver(createMaintenanceLogSchema),
    defaultValues: { equipmentId: preselectedEquipmentId, type: "ROUTINE" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const log = await createMaintenanceLog({
        equipmentId: values.equipmentId,
        performedAt: new Date(values.performedAt).toISOString(),
        type: values.type as MaintenanceLogType,
        cost: toOptionalNumber(values.cost),
        notes: blankToUndefined(values.notes),
        nextDueDate: blankToUndefined(values.nextDueDate),
      });
      navigate(`/maintenance-logs/${log.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Log maintenance</h1>
        <p className="mt-1 text-sm text-slate-500">Record a service event for a piece of equipment.</p>
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
            label="Type"
            options={MAINTENANCE_LOG_TYPES.map((type) => ({ value: type, label: type }))}
            error={errors.type?.message}
            {...register("type")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Performed at" type="datetime-local" error={errors.performedAt?.message} {...register("performedAt")} />
          <TextField label="Cost" type="number" min={0} step="any" error={errors.cost?.message} {...register("cost")} />
          <TextField label="Next due date" type="date" error={errors.nextDueDate?.message} {...register("nextDueDate")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/maintenance-logs")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Log maintenance
          </Button>
        </div>
      </form>
    </div>
  );
}
