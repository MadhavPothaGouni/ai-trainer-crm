import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listTeams } from "../../api/teams";
import { deleteTerritoryRule, getTerritoryRule, updateTerritoryRule } from "../../api/territory";
import { listUsers } from "../../api/users";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { toRequiredNumber, updateTerritoryRuleSchema, type UpdateTerritoryRuleFormValues } from "../../lib/validation";
import {
  LEAD_SOURCES,
  TERRITORY_MATCH_FIELDS_BY_RESOURCE,
  TERRITORY_MATCH_OPERATORS,
  type TeamDto,
  type TerritoryMatchField,
  type TerritoryMatchOperator,
  type TerritoryRuleDto,
  type UserDto,
} from "../../types/api";

const MATCH_FIELD_LABELS: Record<TerritoryMatchField, string> = {
  SOURCE: "Source",
  COMPANY_NAME: "Company name",
  INDUSTRY: "Industry",
  BILLING_COUNTRY: "Billing country",
  BILLING_STATE: "Billing state",
};

export default function TerritoryRuleDetailPage() {
  const { ruleId } = useParams<{ ruleId: string }>();
  const navigate = useNavigate();
  const [rule, setRule] = useState<TerritoryRuleDto | null>(null);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [teams, setTeams] = useState<TeamDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!ruleId) return;
    let cancelled = false;
    getTerritoryRule(ruleId)
      .then((data) => {
        if (!cancelled) setRule(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this territory rule.");
      });
    return () => {
      cancelled = true;
    };
  }, [ruleId]);

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
    control,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<UpdateTerritoryRuleFormValues>({ resolver: zodResolver(updateTerritoryRuleSchema) });

  const matchField = useWatch({ control, name: "matchField" }) as TerritoryMatchField | undefined;
  const assignToType = useWatch({ control, name: "assignToType" });

  useEffect(() => {
    if (!rule) return;
    reset({
      name: rule.name,
      matchField: rule.matchField,
      matchOperator: rule.matchOperator,
      matchValue: rule.matchValue,
      priority: String(rule.priority),
      assignToType: rule.assignToTeamId ? "TEAM" : "USER",
      assignToId: rule.assignToTeamId ?? rule.assignToUserId ?? "",
      active: rule.active,
    });
  }, [rule, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!ruleId) return;
    setEditError(null);
    try {
      const updated = await updateTerritoryRule(ruleId, {
        name: values.name,
        matchField: values.matchField as TerritoryMatchField,
        matchOperator: values.matchOperator as TerritoryMatchOperator,
        matchValue: values.matchValue,
        priority: toRequiredNumber(values.priority),
        assignToUserId: values.assignToType === "USER" ? values.assignToId : undefined,
        assignToTeamId: values.assignToType === "TEAM" ? values.assignToId : undefined,
        active: values.active,
      });
      setRule(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleDelete() {
    if (!ruleId || !window.confirm("Delete this territory rule?")) return;
    setIsDeleting(true);
    try {
      await deleteTerritoryRule(ruleId);
      navigate("/territory-rules");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this territory rule.");
      setIsDeleting(false);
    }
  }

  if (error && !rule) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!rule) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const matchFieldOptions = TERRITORY_MATCH_FIELDS_BY_RESOURCE[rule.targetResource];

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/territory-rules" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Territory Rules
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{rule.name}</h1>
          <p className="mt-1 text-xs text-slate-400">
            {rule.targetResource === "LEAD" ? "Lead" : "Account"} rule - not editable after creation. Matched {rule.matchCount}{" "}
            time{rule.matchCount === 1 ? "" : "s"}
            {rule.lastMatchedAt ? `, last on ${new Date(rule.lastMatchedAt).toLocaleString()}` : ""}.
          </p>
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

        <div className="grid gap-4 sm:grid-cols-3">
          <Select
            label="Field"
            options={matchFieldOptions.map((f) => ({ value: f, label: MATCH_FIELD_LABELS[f] }))}
            error={errors.matchField?.message}
            {...register("matchField")}
          />
          <Select
            label="Operator"
            options={TERRITORY_MATCH_OPERATORS.map((o) => ({ value: o, label: o === "EQUALS" ? "Equals" : "Contains" }))}
            error={errors.matchOperator?.message}
            {...register("matchOperator")}
          />
          {matchField === "SOURCE" ? (
            <Select
              label="Value"
              options={LEAD_SOURCES.map((s) => ({ value: s, label: s }))}
              error={errors.matchValue?.message}
              {...register("matchValue")}
            />
          ) : (
            <TextField label="Value" error={errors.matchValue?.message} {...register("matchValue")} />
          )}
        </div>

        <TextField label="Priority" type="number" min={0} error={errors.priority?.message} {...register("priority")} />

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
