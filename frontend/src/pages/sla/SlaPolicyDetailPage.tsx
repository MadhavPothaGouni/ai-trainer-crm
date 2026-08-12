import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteSlaPolicy, getSlaPolicy, updateSlaPolicy } from "../../api/sla";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, toRequiredNumber, updateSlaPolicySchema, type UpdateSlaPolicyFormValues } from "../../lib/validation";
import type { SlaPolicyDto, UserDto } from "../../types/api";

export default function SlaPolicyDetailPage() {
  const { policyId } = useParams<{ policyId: string }>();
  const navigate = useNavigate();
  const [policy, setPolicy] = useState<SlaPolicyDto | null>(null);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!policyId) return;
    let cancelled = false;
    getSlaPolicy(policyId)
      .then((data) => {
        if (!cancelled) setPolicy(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this SLA policy.");
      });
    return () => {
      cancelled = true;
    };
  }, [policyId]);

  useEffect(() => {
    listUsers({ size: 200 })
      .then((res) => setUsers(res.content))
      .catch(() => undefined);
  }, []);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<UpdateSlaPolicyFormValues>({ resolver: zodResolver(updateSlaPolicySchema) });

  useEffect(() => {
    if (!policy) return;
    reset({
      name: policy.name,
      responseTargetMinutes: String(policy.responseTargetMinutes),
      resolutionTargetMinutes: String(policy.resolutionTargetMinutes),
      escalateToUserId: policy.escalateToUserId ?? "",
      active: policy.active,
    });
  }, [policy, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!policyId) return;
    setEditError(null);
    try {
      const updated = await updateSlaPolicy(policyId, {
        name: values.name,
        responseTargetMinutes: toRequiredNumber(values.responseTargetMinutes),
        resolutionTargetMinutes: toRequiredNumber(values.resolutionTargetMinutes),
        escalateToUserId: blankToUndefined(values.escalateToUserId),
        active: values.active,
      });
      setPolicy(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleDelete() {
    if (!policyId || !window.confirm("Delete this SLA policy?")) return;
    setIsDeleting(true);
    try {
      await deleteSlaPolicy(policyId);
      navigate("/sla-policies");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this SLA policy.");
      setIsDeleting(false);
    }
  }

  if (error && !policy) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!policy) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/sla-policies" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; SLA Policies
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{policy.name}</h1>
          <p className="mt-1 text-xs text-slate-400">{policy.priority} priority - not editable after creation</p>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <form onSubmit={onSaveEdits} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit</h2>

        {editError && <Alert variant="error">{editError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label="Response target (minutes)"
            type="number"
            min={1}
            error={errors.responseTargetMinutes?.message}
            {...register("responseTargetMinutes")}
          />
          <TextField
            label="Resolution target (minutes)"
            type="number"
            min={1}
            error={errors.resolutionTargetMinutes?.message}
            {...register("resolutionTargetMinutes")}
          />
        </div>

        <Select
          label="Escalate to (optional)"
          placeholder="No escalation"
          options={users.map((u) => ({ value: u.id, label: u.fullName }))}
          error={errors.escalateToUserId?.message}
          {...register("escalateToUserId")}
        />

        <label className="flex w-fit items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" className="h-4 w-4 rounded border-slate-300" {...register("active")} />
          Active
        </label>

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
