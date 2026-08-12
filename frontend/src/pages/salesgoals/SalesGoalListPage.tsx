import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { createSalesGoal, listSalesGoals } from "../../api/salesGoals";
import { listTeams } from "../../api/teams";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { createSalesGoalSchema, type CreateSalesGoalFormValues } from "../../lib/validation";
import { ProgressBar } from "../../components/ui/ProgressBar";
import { SALES_GOAL_METRICS, type PageResponse, type SalesGoalDto, type SalesGoalMetric, type TeamDto, type UserDto } from "../../types/api";

const PAGE_SIZE = 20;

const METRIC_LABELS: Record<SalesGoalMetric, string> = { REVENUE: "Revenue", DEAL_COUNT: "Deal count" };

export default function SalesGoalListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<SalesGoalDto> | null>(null);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [teams, setTeams] = useState<TeamDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  function reload() {
    setIsLoading(true);
    listSalesGoals({ page, size: PAGE_SIZE })
      .then((res) => setResult(res))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load sales goals."))
      .finally(() => setIsLoading(false));
  }

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  useEffect(() => {
    listUsers({ size: 200 })
      .then((res) => setUsers(res.content))
      .catch(() => undefined);
    listTeams({ size: 200 })
      .then((res) => setTeams(res.content))
      .catch(() => undefined);
  }, []);

  function assigneeLabel(goal: SalesGoalDto): string {
    if (goal.ownerUserId) {
      return users.find((u) => u.id === goal.ownerUserId)?.fullName ?? "Unknown teammate";
    }
    if (goal.teamId) {
      return teams.find((t) => t.id === goal.teamId)?.name ?? "Unknown team";
    }
    return "—";
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Sales Goals</h1>
        <p className="mt-1 text-sm text-slate-500">
          Revenue or deal-count quotas for a period, assigned to a person or a whole team. Progress is computed live from won
          deals - a rep can always see their own goals under "My Goals" without needing this page's permission.
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="flex flex-col gap-3">
        {isLoading && <p className="text-sm text-slate-400">Loading...</p>}
        {!isLoading && result?.content.length === 0 && (
          <p className="rounded-lg border border-dashed border-slate-200 px-4 py-6 text-center text-sm text-slate-400">
            No sales goals yet.
          </p>
        )}
        {result?.content.map((goal) => (
          <Link
            key={goal.id}
            to={`/sales-goals/${goal.id}`}
            className="flex flex-col gap-2 rounded-lg border border-slate-200 bg-white p-4 hover:border-slate-300"
          >
            <div className="flex items-center justify-between">
              <div>
                <span className="font-medium text-slate-900">{goal.name}</span>
                <span className="ml-2 text-xs text-slate-400">
                  {assigneeLabel(goal)} · {METRIC_LABELS[goal.metric]} · {goal.periodStart} to {goal.periodEnd}
                </span>
              </div>
              <span className="text-sm font-medium text-slate-600">
                {goal.actualValue} / {goal.targetValue}
              </span>
            </div>
            <ProgressBar percent={goal.percentComplete} />
          </Link>
        ))}
      </div>

      {result && (
        <Pagination
          pageNumber={result.pageNumber}
          totalPages={result.totalPages}
          first={result.first}
          last={result.last}
          totalElements={result.totalElements}
          onPageChange={setPage}
        />
      )}

      <CreateSalesGoalForm users={users} teams={teams} onCreated={reload} />
    </div>
  );
}

function CreateSalesGoalForm({ users, teams, onCreated }: { users: UserDto[]; teams: TeamDto[]; onCreated: () => void }) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CreateSalesGoalFormValues>({
    resolver: zodResolver(createSalesGoalSchema),
    defaultValues: {
      name: "",
      assignToType: "USER",
      assignToId: "",
      metric: "",
      targetValue: "",
      periodStart: "",
      periodEnd: "",
    },
  });

  const assignToType = watch("assignToType");

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await createSalesGoal({
        name: values.name,
        ownerUserId: values.assignToType === "USER" ? values.assignToId : undefined,
        teamId: values.assignToType === "TEAM" ? values.assignToId : undefined,
        metric: values.metric as SalesGoalMetric,
        targetValue: Number(values.targetValue),
        periodStart: values.periodStart,
        periodEnd: values.periodEnd,
      });
      reset({ name: "", assignToType: "USER", assignToId: "", metric: "", targetValue: "", periodStart: "", periodEnd: "" });
      onCreated();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
      <h2 className="text-sm font-medium text-slate-900">New sales goal</h2>

      {formError && <Alert variant="error">{formError}</Alert>}

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
          placeholder="Choose a metric"
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
          Create goal
        </Button>
      </div>
    </form>
  );
}
