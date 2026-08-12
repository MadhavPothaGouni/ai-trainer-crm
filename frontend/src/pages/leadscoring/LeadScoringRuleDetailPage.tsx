import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteLeadScoringRule, getLeadScoringRule, updateLeadScoringRule } from "../../api/leadScoringRules";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { toRequiredNumber, updateLeadScoringRuleSchema, type UpdateLeadScoringRuleFormValues } from "../../lib/validation";
import {
  LEAD_SCORING_MATCH_FIELDS,
  LEAD_SCORING_MATCH_OPERATORS,
  LEAD_SOURCES,
  type LeadScoringMatchField,
  type LeadScoringMatchOperator,
  type LeadScoringRuleDto,
} from "../../types/api";

const MATCH_FIELD_LABELS: Record<LeadScoringMatchField, string> = {
  SOURCE: "Source",
  COMPANY_NAME: "Company name",
  TITLE: "Title",
  EMAIL_DOMAIN: "Email domain",
};

export default function LeadScoringRuleDetailPage() {
  const { ruleId } = useParams<{ ruleId: string }>();
  const navigate = useNavigate();
  const [rule, setRule] = useState<LeadScoringRuleDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!ruleId) return;
    let cancelled = false;
    getLeadScoringRule(ruleId)
      .then((data) => {
        if (!cancelled) setRule(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this scoring rule.");
      });
    return () => {
      cancelled = true;
    };
  }, [ruleId]);

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<UpdateLeadScoringRuleFormValues>({ resolver: zodResolver(updateLeadScoringRuleSchema) });

  const matchField = useWatch({ control, name: "matchField" }) as LeadScoringMatchField | undefined;

  useEffect(() => {
    if (!rule) return;
    reset({
      name: rule.name,
      matchField: rule.matchField,
      matchOperator: rule.matchOperator,
      matchValue: rule.matchValue,
      points: String(rule.points),
      active: rule.active,
    });
  }, [rule, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!ruleId) return;
    setEditError(null);
    try {
      const updated = await updateLeadScoringRule(ruleId, {
        name: values.name,
        matchField: values.matchField as LeadScoringMatchField,
        matchOperator: values.matchOperator as LeadScoringMatchOperator,
        matchValue: values.matchValue,
        points: toRequiredNumber(values.points),
        active: values.active,
      });
      setRule(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleDelete() {
    if (!ruleId || !window.confirm("Delete this scoring rule?")) return;
    setIsDeleting(true);
    try {
      await deleteLeadScoringRule(ruleId);
      navigate("/lead-scoring-rules");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this scoring rule.");
      setIsDeleting(false);
    }
  }

  if (error && !rule) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!rule) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/lead-scoring-rules" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Lead Scoring Rules
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{rule.name}</h1>
          <p className="mt-1 text-xs text-slate-400">
            Matched {rule.matchCount} time{rule.matchCount === 1 ? "" : "s"}
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
            options={LEAD_SCORING_MATCH_FIELDS.map((f) => ({ value: f, label: MATCH_FIELD_LABELS[f] }))}
            error={errors.matchField?.message}
            {...register("matchField")}
          />
          <Select
            label="Operator"
            options={LEAD_SCORING_MATCH_OPERATORS.map((o) => ({ value: o, label: o === "EQUALS" ? "Equals" : "Contains" }))}
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

        <TextField label="Points" type="number" error={errors.points?.message} {...register("points")} />

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
