import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteSalesGoal, getSalesGoal, updateSalesGoal } from "../../api/salesGoals";
import { listTeams } from "../../api/teams";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { ProgressBar } from "../../components/ui/ProgressBar";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { updateSalesGoalSchema, type UpdateSalesGoalFormValues } from "../../lib/validation";
import { SALES_GOAL_METRICS, type SalesGoalDto, type SalesGoalMetric, type TeamDto, type UserDto } from "../../types/api";

const METRIC_LABELS: Record<SalesGoalMetric, string> = { REVENUE: "Revenue", DEAL_COUNT: "Deal count" };

export default function SalesGoalDetailPage() {
  const { goalId } = useParams<{ goalId: string }>();
  const navigate = useNavigate();
  const [goal, setGoal] = useState<SalesGoalDto | null>(null);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [teams, setTeams] = useState<TeamDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!goalId) return;
    let cancelled = false;
    getSalesGoal(goalId)
      .then((data) => {
        if (!cancelled) setGoal(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this sales goal.");
      });
    return () => {
      cancelled = true;
    };
  }, [goalId]);

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
  } = useForm<UpdateSalesGoalFormValues>({ resolver: zodResolver(updateSalesGoalSchema) });

  const assignToType = watch("assignToType");

  useEffect(() => {
    if (!goal) return;
    reset({
      name: goal.name,
      assignToType: goal.teamId ? "TEAM" : "USER",
      assignToId: goal.teamId ?? goal.ownerUserId ?? "",
      metric: goal.metric,
      targetValue: String(goal.targetValue),
      periodStart: goal.periodStart,
      periodEnd: goal.periodEnd,
    });
  }, [goal, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!goalId) return;
    setEditError(null);
    try {
      const updated = await updateSalesGoal(goalId, {
        name: values.name,
        ownerUserId: values.assignToType === "USER" ? values.assignToId : undefined,
        teamId: values.assignToType === "TEAM" ? values.assignToId : undefined,
        metric: values.metric as SalesGoalMetric,
        targetValue: Number(values.targetValue),
        periodStart: values.periodStart,
        periodEnd: values.periodEnd,
      });
      setGoal(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleDelete() {
    if (!goalId || !window.confirm("Delete this sales goal?")) return;
    setIsDeleting(true);
    try {
      await deleteSalesGoal(goalId);
      navigate("/sales-goals");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this sales goal.");
      setIsDeleting(false);
    }
  }

  if (error && !goal) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!goal) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/sales-goals" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Sales Goals
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{goal.name}</h1>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <div className="flex items-center justify-between text-sm">
          <span className="text-slate-500">Progress</span>
          <span className="font-medium text-slate-900">
            {goal.actualValue} / {goal.targetValue} ({METRIC_LABELS[goal.metric]})
          </span>
        </div>
        <div className="mt-2">
          <ProgressBar percent={goal.percentComplete} />
        </div>
      </div>

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
            label="Metric"
            options={SALES_GOAL_METRICS.map((m) => ({ value: m, label: METRIC_LABELS[m] }))}
            error={errors.metric?.message}
            {...register("metric")}
          />
          <TextField label="Target" type="number" min={0} step="0.01" error={errors.targetValue?.message} {...register("targetValue")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Period start" type="date" error={errors.periodStart?.message} {...register("periodStart")} />
          <TextField label="Period end" type="date" error={errors.periodEnd?.message} {...register("periodEnd")} />
        </div>

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
