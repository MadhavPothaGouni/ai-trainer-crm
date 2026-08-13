import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteCommissionPlan, getCommissionPlan, updateCommissionPlan } from "../../api/commission";
import { listTeams } from "../../api/teams";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { updateCommissionPlanSchema, type UpdateCommissionPlanFormValues } from "../../lib/validation";
import { COMMISSION_RATE_TYPES, type CommissionPlanDto, type CommissionRateType, type TeamDto, type UserDto } from "../../types/api";

const RATE_TYPE_LABELS: Record<CommissionRateType, string> = { PERCENTAGE: "% of deal", FLAT_PER_DEAL: "Flat per deal" };

export default function CommissionPlanDetailPage() {
  const { planId } = useParams<{ planId: string }>();
  const navigate = useNavigate();
  const [plan, setPlan] = useState<CommissionPlanDto | null>(null);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [teams, setTeams] = useState<TeamDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!planId) return;
    let cancelled = false;
    getCommissionPlan(planId)
      .then((data) => {
        if (!cancelled) setPlan(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this commission plan.");
      });
    return () => {
      cancelled = true;
    };
  }, [planId]);

  useEffect(() => {
    listUsers({ size: 200 })
      .then((res) => setUsers(res.content))
      .catch(() => undefined);
    listTeams({ size: 200 })
      .then((res) => setTeams(res.content))
      .catch(() => undefined);
  }, []);

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<UpdateCommissionPlanFormValues>({ resolver: zodResolver(updateCommissionPlanSchema) });

  const assignToType = watch("assignToType");

  useEffect(() => {
    if (!plan) return;
    reset({
      name: plan.name,
      assignToType: plan.teamId ? "TEAM" : "USER",
      assignToId: plan.teamId ?? plan.ownerUserId ?? "",
      rateType: plan.rateType,
      rate: String(plan.rate),
      active: plan.active,
    });
  }, [plan, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!planId) return;
    setEditError(null);
    try {
      const updated = await updateCommissionPlan(planId, {
        name: values.name,
        ownerUserId: values.assignToType === "USER" ? values.assignToId : undefined,
        teamId: values.assignToType === "TEAM" ? values.assignToId : undefined,
        rateType: values.rateType as CommissionRateType,
        rate: Number(values.rate),
        active: values.active,
      });
      setPlan(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleDelete() {
    if (!planId || !window.confirm("Delete this commission plan?")) return;
    setIsDeleting(true);
    try {
      await deleteCommissionPlan(planId);
      navigate("/commission-plans");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this commission plan.");
      setIsDeleting(false);
    }
  }

  if (error && !plan) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!plan) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/commission-plans" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Commission Plans
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{plan.name}</h1>
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
          <Select
            label="Assign to"
            options={[
              { value: "USER", label: "A specific person" },
              { value: "TEAM", label: "A whole team" },
            ]}
            {...register("assignToType")}
          />
          {assignToType === "TEAM" ? (
            <Select
              label="Team"
              placeholder="Choose a team"
              options={teams.map((t) => ({ value: t.id, label: t.name }))}
              error={errors.assignToId?.message}
              {...register("assignToId")}
            />
          ) : (
            <Select
              label="Person"
              placeholder="Choose a person"
              options={users.map((u) => ({ value: u.id, label: u.fullName }))}
              error={errors.assignToId?.message}
              {...register("assignToId")}
            />
          )}
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Rate type"
            options={COMMISSION_RATE_TYPES.map((t) => ({ value: t, label: RATE_TYPE_LABELS[t] }))}
            error={errors.rateType?.message}
            {...register("rateType")}
          />
          <TextField label="Rate" type="number" min={0} step="0.01" error={errors.rate?.message} {...register("rate")} />
        </div>

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
