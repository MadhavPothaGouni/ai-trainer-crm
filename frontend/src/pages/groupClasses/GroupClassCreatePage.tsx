import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createGroupClass } from "../../api/groupClasses";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createGroupClassSchema, toOptionalNumber, type CreateGroupClassFormValues } from "../../lib/validation";

export default function GroupClassCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateGroupClassFormValues>({ resolver: zodResolver(createGroupClassSchema), defaultValues: { durationMinutes: "60" } });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const groupClass = await createGroupClass({
        name: values.name,
        description: blankToUndefined(values.description),
        durationMinutes: Number(values.durationMinutes),
        capacity: toOptionalNumber(values.capacity),
        location: blankToUndefined(values.location),
      });
      navigate(`/group-classes/${groupClass.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New group class</h1>
        <p className="mt-1 text-sm text-slate-500">Add a class type to the catalog - sessions get scheduled from it.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" placeholder="Spin 45, Sunrise Yoga..." error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Duration (minutes)" type="number" min={5} step={1} error={errors.durationMinutes?.message} {...register("durationMinutes")} />
          <TextField label="Capacity" type="number" min={1} step={1} placeholder="Leave blank for unlimited" error={errors.capacity?.message} {...register("capacity")} />
          <TextField label="Location" error={errors.location?.message} {...register("location")} />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/group-classes")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create class
          </Button>
        </div>
      </form>
    </div>
  );
}
