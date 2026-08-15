import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createLocker } from "../../api/lockers";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createLockerSchema, type CreateLockerFormValues } from "../../lib/validation";
import { LOCKER_SIZES, type LockerSize } from "../../types/api";

export default function LockerCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateLockerFormValues>({ resolver: zodResolver(createLockerSchema), defaultValues: { size: "MEDIUM" } });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const locker = await createLocker({
        label: values.label,
        location: blankToUndefined(values.location),
        size: values.size as LockerSize,
        notes: blankToUndefined(values.notes),
      });
      navigate(`/lockers/${locker.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New locker</h1>
        <p className="mt-1 text-sm text-slate-500">Add a locker to the catalog - assignments get made against it.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Label" placeholder="A-12" error={errors.label?.message} {...register("label")} />
          <Select label="Size" options={LOCKER_SIZES.map((size) => ({ value: size, label: size }))} error={errors.size?.message} {...register("size")} />
        </div>

        <TextField label="Location" placeholder="Men's locker room" error={errors.location?.message} {...register("location")} />

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/lockers")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Add locker
          </Button>
        </div>
      </form>
    </div>
  );
}
