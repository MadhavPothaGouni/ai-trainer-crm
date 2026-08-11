import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createDashboard } from "../../api/dashboards";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createDashboardSchema, type CreateDashboardFormValues } from "../../lib/validation";

export default function DashboardCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateDashboardFormValues>({ resolver: zodResolver(createDashboardSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const dashboard = await createDashboard({ name: values.name, description: blankToUndefined(values.description) });
      navigate(`/dashboards/${dashboard.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New dashboard</h1>
        <p className="mt-1 text-sm text-slate-500">Add widgets once it's created.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />
        <TextArea label="Description" rows={2} error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/dashboards")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create dashboard
          </Button>
        </div>
      </form>
    </div>
  );
}
