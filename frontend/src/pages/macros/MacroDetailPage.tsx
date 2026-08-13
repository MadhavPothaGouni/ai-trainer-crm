import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteMacro, getMacro, updateMacro } from "../../api/macros";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createMacroSchema, type CreateMacroFormValues } from "../../lib/validation";
import { TICKET_STATUSES, type MacroDto, type TicketStatus } from "../../types/api";

export default function MacroDetailPage() {
  const { macroId } = useParams<{ macroId: string }>();
  const navigate = useNavigate();
  const [macro, setMacro] = useState<MacroDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateMacroFormValues>({ resolver: zodResolver(createMacroSchema) });

  useEffect(() => {
    if (!macroId) return;
    let cancelled = false;
    getMacro(macroId)
      .then((data) => {
        if (cancelled) return;
        setMacro(data);
        reset({ name: data.name, body: data.body, newStatus: data.newStatus ?? "" });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this macro.");
      });
    return () => {
      cancelled = true;
    };
  }, [macroId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!macroId || !macro) return;
    setFormError(null);
    try {
      const updated = await updateMacro(macroId, {
        name: values.name,
        body: values.body,
        newStatus: blankToUndefined(values.newStatus) as TicketStatus | undefined,
        active: macro.active,
      });
      setMacro(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function toggleActive() {
    if (!macroId || !macro) return;
    try {
      const updated = await updateMacro(macroId, {
        name: macro.name,
        body: macro.body,
        newStatus: macro.newStatus ?? undefined,
        active: !macro.active,
      });
      setMacro(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this macro.");
    }
  }

  async function handleDelete() {
    if (!macroId || !window.confirm("Delete this macro?")) return;
    setIsDeleting(true);
    try {
      await deleteMacro(macroId);
      navigate("/macros");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this macro.");
      setIsDeleting(false);
    }
  }

  if (error && !macro) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!macro) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/macros" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Macros
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{macro.name}</h1>
            {macro.active ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => void toggleActive()}>
            {macro.active ? "Deactivate" : "Activate"}
          </Button>
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit</h2>

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

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
