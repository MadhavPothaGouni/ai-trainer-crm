import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createEquipment } from "../../api/equipment";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createEquipmentSchema, toOptionalNumber, type CreateEquipmentFormValues } from "../../lib/validation";

export default function EquipmentCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateEquipmentFormValues>({ resolver: zodResolver(createEquipmentSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const equipment = await createEquipment({
        name: values.name,
        category: blankToUndefined(values.category),
        serialNumber: blankToUndefined(values.serialNumber),
        location: blankToUndefined(values.location),
        purchaseDate: blankToUndefined(values.purchaseDate),
        purchasePrice: toOptionalNumber(values.purchasePrice),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/equipment/${equipment.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Add equipment</h1>
        <p className="mt-1 text-sm text-slate-500">Add a physical asset to the organization's inventory.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" placeholder="Treadmill #3" error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Category" placeholder="Cardio, Strength..." error={errors.category?.message} {...register("category")} />
          <TextField label="Serial number" error={errors.serialNumber?.message} {...register("serialNumber")} />
          <TextField label="Location" error={errors.location?.message} {...register("location")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Purchase date" type="date" error={errors.purchaseDate?.message} {...register("purchaseDate")} />
          <TextField label="Purchase price" type="number" min={0} step="any" error={errors.purchasePrice?.message} {...register("purchasePrice")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/equipment")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Add equipment
          </Button>
        </div>
      </form>
    </div>
  );
}
