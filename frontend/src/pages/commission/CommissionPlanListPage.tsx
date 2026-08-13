import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { createCommissionPlan, listCommissionPlans } from "../../api/commission";
import { listTeams } from "../../api/teams";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { createCommissionPlanSchema, type CreateCommissionPlanFormValues } from "../../lib/validation";
import {
  COMMISSION_RATE_TYPES,
  type CommissionPlanDto,
  type CommissionRateType,
  type PageResponse,
  type TeamDto,
  type UserDto,
} from "../../types/api";

const PAGE_SIZE = 20;

const RATE_TYPE_LABELS: Record<CommissionRateType, string> = { PERCENTAGE: "% of deal", FLAT_PER_DEAL: "Flat per deal" };

export default function CommissionPlanListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<CommissionPlanDto> | null>(null);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [teams, setTeams] = useState<TeamDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  function reload() {
    setIsLoading(true);
    listCommissionPlans({ page, size: PAGE_SIZE })
      .then((res) => setResult(res))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load commission plans."))
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

  function assigneeLabel(plan: CommissionPlanDto): string {
    if (plan.ownerUserId) {
      return users.find((u) => u.id === plan.ownerUserId)?.fullName ?? "Unknown teammate";
    }
    if (plan.teamId) {
      return teams.find((t) => t.id === plan.teamId)?.name ?? "Unknown team";
    }
    return "—";
  }

  function rateLabel(plan: CommissionPlanDto): string {
    return plan.rateType === "PERCENTAGE" ? `${plan.rate}%` : formatCurrency(plan.rate);
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Commission Plans</h1>
        <p className="mt-1 text-sm text-slate-500">
          A rate rule for an individual or a whole team. The moment a rep's Opportunity is marked Closed Won, a Commission
          Record is created automatically using the individual plan for that rep if one exists, otherwise their team's plan.
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="flex flex-col gap-3">
        {isLoading && <p className="text-sm text-slate-400">Loading...</p>}
        {!isLoading && result?.content.length === 0 && (
          <p className="rounded-lg border border-dashed border-slate-200 px-4 py-6 text-center text-sm text-slate-400">
            No commission plans yet.
          </p>
        )}
        {result?.content.map((plan) => (
          <Link
            key={plan.id}
            to={`/commission-plans/${plan.id}`}
            className="flex items-center justify-between rounded-lg border border-slate-200 bg-white p-4 hover:border-slate-300"
          >
            <div>
              <span className="font-medium text-slate-900">{plan.name}</span>
              <span className="ml-2 text-xs text-slate-400">
                {assigneeLabel(plan)} · {RATE_TYPE_LABELS[plan.rateType]}
                {!plan.active && " · inactive"}
              </span>
            </div>
            <span className="text-sm font-medium text-slate-600">{rateLabel(plan)}</span>
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

      <CreateCommissionPlanForm users={users} teams={teams} onCreated={reload} />
    </div>
  );
}

function CreateCommissionPlanForm({ users, teams, onCreated }: { users: UserDto[]; teams: TeamDto[]; onCreated: () => void }) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CreateCommissionPlanFormValues>({
    resolver: zodResolver(createCommissionPlanSchema),
    defaultValues: { name: "", assignToType: "USER", assignToId: "", rateType: "", rate: "" },
  });

  const assignToType = watch("assignToType");

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await createCommissionPlan({
        name: values.name,
        ownerUserId: values.assignToType === "USER" ? values.assignToId : undefined,
        teamId: values.assignToType === "TEAM" ? values.assignToId : undefined,
        rateType: values.rateType as CommissionRateType,
        rate: Number(values.rate),
      });
      reset({ name: "", assignToType: "USER", assignToId: "", rateType: "", rate: "" });
      onCreated();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
      <h2 className="text-sm font-medium text-slate-900">New commission plan</h2>

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
          label="Rate type"
          placeholder="Choose a rate type"
          options={COMMISSION_RATE_TYPES.map((t) => ({ value: t, label: RATE_TYPE_LABELS[t] }))}
          error={errors.rateType?.message}
          {...register("rateType")}
        />
        <TextField label="Rate" type="number" min={0} step="0.01" error={errors.rate?.message} {...register("rate")} />
      </div>

      <div className="flex justify-end">
        <Button type="submit" isLoading={isSubmitting}>
          Create plan
        </Button>
      </div>
    </form>
  );
}

function formatCurrency(value: number): string {
  return value.toLocaleString(undefined, { style: "currency", currency: "USD" });
}
