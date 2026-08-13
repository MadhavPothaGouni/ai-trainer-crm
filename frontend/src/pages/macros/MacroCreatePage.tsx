import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createMacro } from "../../api/macros";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createMacroSchema, type CreateMacroFormValues } from "../../lib/validation";
import { TICKET_STATUSES, type TicketStatus } from "../../types/api";

export default function MacroCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateMacroFormValues>({ resolver: zodResolver(createMacroSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const macro = await createMacro({
        name: values.name,
        body: values.body,
        newStatus: blankToUndefined(values.newStatus) as TicketStatus | undefined,
      });
      navigate(`/macros/${macro.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New macro</h1>
        <p className="mt-1 text-sm text-slate-500">A canned response reps can apply to any ticket.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />
        <TextArea label="Body" error={errors.body?.message} {...register("body")} />
        <Select
          label="Also set ticket status to"
          placeholder="No change"
          options={TICKET_STATUSES.map((status) => ({ value: status, label: status.replace("_", " ") }))}
          error={errors.newStatus?.message}
          {...register("newStatus")}
        />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/macros")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create macro
          </Button>
        </div>
      </form>
    </div>
  );
}
