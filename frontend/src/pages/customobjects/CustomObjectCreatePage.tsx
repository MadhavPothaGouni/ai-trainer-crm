import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createCustomObject } from "../../api/customObjects";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createCustomObjectSchema, type CreateCustomObjectFormValues } from "../../lib/validation";

export default function CustomObjectCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateCustomObjectFormValues>({ resolver: zodResolver(createCustomObjectSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const object = await createCustomObject({
        apiName: values.apiName,
        label: values.label,
        pluralLabel: values.pluralLabel,
        description: blankToUndefined(values.description),
      });
      navigate(`/custom-objects/${object.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New custom object</h1>
        <p className="mt-1 text-sm text-slate-500">
          A generic entity with a Name field - attach custom fields to it afterward to shape what a record holds.
        </p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField
          label="API name"
          placeholder="project"
          error={errors.apiName?.message}
          {...register("apiName")}
        />
        <p className="-mt-2 text-xs text-slate-400">Lowercase letters, numbers, and underscores only - can't be changed later.</p>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Label" placeholder="Project" error={errors.label?.message} {...register("label")} />
          <TextField label="Plural label" placeholder="Projects" error={errors.pluralLabel?.message} {...register("pluralLabel")} />
        </div>

        <TextArea label="Description" rows={3} error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/custom-objects")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create custom object
          </Button>
        </div>
      </form>
    </div>
  );
}
