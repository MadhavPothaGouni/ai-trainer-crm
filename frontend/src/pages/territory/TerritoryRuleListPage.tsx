import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Link } from "react-router-dom";
import { listTeams } from "../../api/teams";
import { createTerritoryRule, listTerritoryRules } from "../../api/territory";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { createTerritoryRuleSchema, toRequiredNumber, type CreateTerritoryRuleFormValues } from "../../lib/validation";
import {
  LEAD_SOURCES,
  TERRITORY_MATCH_FIELDS_BY_RESOURCE,
  TERRITORY_MATCH_OPERATORS,
  TERRITORY_TARGET_RESOURCES,
  type PageResponse,
  type TeamDto,
  type TerritoryMatchField,
  type TerritoryMatchOperator,
  type TerritoryRuleDto,
  type TerritoryTargetResource,
  type UserDto,
} from "../../types/api";

const PAGE_SIZE = 20;

const MATCH_FIELD_LABELS: Record<TerritoryMatchField, string> = {
  SOURCE: "Source",
  COMPANY_NAME: "Company name",
  INDUSTRY: "Industry",
  BILLING_COUNTRY: "Billing country",
  BILLING_STATE: "Billing state",
};

export default function TerritoryRuleListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<TerritoryRuleDto> | null>(null);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [teams, setTeams] = useState<TeamDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  function reload() {
    setIsLoading(true);
    listTerritoryRules({ page, size: PAGE_SIZE })
      .then((res) => setResult(res))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load territory rules."))
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

  function assigneeLabel(rule: TerritoryRuleDto): string {
    if (rule.assignToUserId) {
      return users.find((u) => u.id === rule.assignToUserId)?.fullName ?? "Unknown teammate";
    }
    if (rule.assignToTeamId) {
      const team = teams.find((t) => t.id === rule.assignToTeamId);
      return team ? `${team.name} (round-robin)` : "Unknown team (round-robin)";
    }
    return "—";
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Territory Rules</h1>
        <p className="mt-1 text-sm text-slate-500">
          Auto-route newly created Leads and Accounts to an owner. Rules run in priority order (lower first) and the first
          match wins.
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Applies to</th>
              <th className="px-4 py-3 font-medium">Criterion</th>
              <th className="px-4 py-3 font-medium">Priority</th>
              <th className="px-4 py-3 font-medium">Assigns to</th>
              <th className="px-4 py-3 font-medium">Matches</th>
              <th className="px-4 py-3 font-medium">Active</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={7}>
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={7}>
                  No territory rules yet.
                </td>
              </tr>
            )}
            {result?.content.map((rule) => (
              <tr key={rule.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/territory-rules/${rule.id}`} className="font-medium text-slate-900 hover:underline">
                    {rule.name}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-500">{rule.targetResource === "LEAD" ? "Lead" : "Account"}</td>
                <td className="px-4 py-3 text-slate-500">
                  {MATCH_FIELD_LABELS[rule.matchField]} {rule.matchOperator === "EQUALS" ? "=" : "contains"} "{rule.matchValue}"
                </td>
                <td className="px-4 py-3 text-slate-500">{rule.priority}</td>
                <td className="px-4 py-3 text-slate-500">{assigneeLabel(rule)}</td>
                <td className="px-4 py-3 text-slate-500">{rule.matchCount}</td>
                <td className="px-4 py-3">
                  {rule.active ? (
                    <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-800">
                      Active
                    </span>
                  ) : (
                    <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">
                      Inactive
                    </span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
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

      <CreateTerritoryRuleForm users={users} teams={teams} onCreated={reload} />
    </div>
  );
}

function CreateTerritoryRuleForm({ users, teams, onCreated }: { users: UserDto[]; teams: TeamDto[]; onCreated: () => void }) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CreateTerritoryRuleFormValues>({
    resolver: zodResolver(createTerritoryRuleSchema),
    defaultValues: {
      name: "",
      targetResource: "",
      matchField: "",
      matchOperator: "",
      matchValue: "",
      priority: "100",
      assignToType: "USER",
      assignToId: "",
    },
  });

  const targetResource = useWatch({ control, name: "targetResource" }) as TerritoryTargetResource | "";
  const matchField = useWatch({ control, name: "matchField" }) as TerritoryMatchField | "";
  const assignToType = useWatch({ control, name: "assignToType" });

  const matchFieldOptions = targetResource ? TERRITORY_MATCH_FIELDS_BY_RESOURCE[targetResource] : [];

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await createTerritoryRule({
        name: values.name,
        targetResource: values.targetResource as TerritoryTargetResource,
        matchField: values.matchField as TerritoryMatchField,
        matchOperator: values.matchOperator as TerritoryMatchOperator,
        matchValue: values.matchValue,
        priority: toRequiredNumber(values.priority),
        assignToUserId: values.assignToType === "USER" ? values.assignToId : undefined,
        assignToTeamId: values.assignToType === "TEAM" ? values.assignToId : undefined,
      });
      reset({
        name: "",
        targetResource: "",
        matchField: "",
        matchOperator: "",
        matchValue: "",
        priority: "100",
        assignToType: "USER",
        assignToId: "",
      });
      onCreated();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
      <h2 className="text-sm font-medium text-slate-900">New territory rule</h2>

      {formError && <Alert variant="error">{formError}</Alert>}

      <TextField label="Name" error={errors.name?.message} {...register("name")} />

      <div className="grid gap-4 sm:grid-cols-2">
        <Select
          label="Applies to"
          placeholder="Choose Lead or Account"
          options={TERRITORY_TARGET_RESOURCES.map((r) => ({ value: r, label: r === "LEAD" ? "Lead" : "Account" }))}
          error={errors.targetResource?.message}
          {...register("targetResource")}
        />
        <TextField label="Priority" type="number" min={0} error={errors.priority?.message} {...register("priority")} />
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Select
          label="Field"
          placeholder={targetResource ? "Choose a field" : "Choose 'Applies to' first"}
          options={matchFieldOptions.map((f) => ({ value: f, label: MATCH_FIELD_LABELS[f] }))}
          disabled={!targetResource}
          error={errors.matchField?.message}
          {...register("matchField")}
        />
        <Select
          label="Operator"
          placeholder="Choose an operator"
          options={TERRITORY_MATCH_OPERATORS.map((o) => ({ value: o, label: o === "EQUALS" ? "Equals" : "Contains" }))}
          error={errors.matchOperator?.message}
          {...register("matchOperator")}
        />
        {matchField === "SOURCE" ? (
          <Select
            label="Value"
            placeholder="Choose a source"
            options={LEAD_SOURCES.map((s) => ({ value: s, label: s }))}
            error={errors.matchValue?.message}
            {...register("matchValue")}
          />
        ) : (
          <TextField label="Value" error={errors.matchValue?.message} {...register("matchValue")} />
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Select
          label="Assign to"
          options={[
            { value: "USER", label: "A specific person" },
            { value: "TEAM", label: "A team (round-robin)" },
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

      <div className="flex justify-end">
        <Button type="submit" isLoading={isSubmitting}>
          Create rule
        </Button>
      </div>
    </form>
  );
}
