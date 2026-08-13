import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createCertification } from "../../api/certifications";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createCertificationSchema, type CreateCertificationFormValues } from "../../lib/validation";

export default function CertificationCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateCertificationFormValues>({ resolver: zodResolver(createCertificationSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const certification = await createCertification({
        name: values.name,
        issuingBody: blankToUndefined(values.issuingBody),
        description: blankToUndefined(values.description),
        validityMonths: values.validityMonths === "" || values.validityMonths === undefined ? undefined : Number(values.validityMonths),
      });
      navigate(`/certifications/${certification.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New certification</h1>
        <p className="mt-1 text-sm text-slate-500">Add a credential to the catalog. Leave validity blank for one that never expires.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Issuing body" error={errors.issuingBody?.message} {...register("issuingBody")} />
          <TextField
            label="Validity (months)"
            type="number"
            min={1}
            placeholder="Never expires"
            error={errors.validityMonths?.message}
            {...register("validityMonths")}
          />
        </div>

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/certifications")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create certification
          </Button>
        </div>
      </form>
    </div>
  );
}
