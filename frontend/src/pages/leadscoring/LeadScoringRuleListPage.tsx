import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Link } from "react-router-dom";
import { createLeadScoringRule, listLeadScoringRules } from "../../api/leadScoringRules";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Pagination } from "../../components/ui/Pagination";
import { Select } from "../../components/ui/Select";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { createLeadScoringRuleSchema, toRequiredNumber, type CreateLeadScoringRuleFormValues } from "../../lib/validation";
import {
  LEAD_SCORING_MATCH_FIELDS,
  LEAD_SCORING_MATCH_OPERATORS,
  LEAD_SOURCES,
  type LeadScoringMatchField,
  type LeadScoringMatchOperator,
  type LeadScoringRuleDto,
  type PageResponse,
} from "../../types/api";

const PAGE_SIZE = 20;

const MATCH_FIELD_LABELS: Record<LeadScoringMatchField, string> = {
  SOURCE: "Source",
  COMPANY_NAME: "Company name",
  TITLE: "Title",
  EMAIL_DOMAIN: "Email domain",
};

export default function LeadScoringRuleListPage() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<LeadScoringRuleDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  function reload() {
    setIsLoading(true);
    listLeadScoringRules({ page, size: PAGE_SIZE })
      .then((res) => setResult(res))
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : "Could not load lead scoring rules."))
      .finally(() => setIsLoading(false));
  }

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Lead Scoring Rules</h1>
        <p className="mt-1 text-sm text-slate-500">
          Every active rule that matches a Lead contributes its points to that Lead's score - unlike Territory Rules, there's
          no "first match wins": all matching rules add up. A Lead is rescored automatically whenever it's created or edited.
        </p>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Name</th>
              <th className="px-4 py-3 font-medium">Criterion</th>
              <th className="px-4 py-3 font-medium">Points</th>
              <th className="px-4 py-3 font-medium">Matches</th>
              <th className="px-4 py-3 font-medium">Active</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {isLoading && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={5}>
                  Loading...
                </td>
              </tr>
            )}
            {!isLoading && result?.content.length === 0 && (
              <tr>
                <td className="px-4 py-6 text-center text-slate-400" colSpan={5}>
                  No lead scoring rules yet.
                </td>
              </tr>
            )}
            {result?.content.map((rule) => (
              <tr key={rule.id} className="hover:bg-slate-50">
                <td className="px-4 py-3">
                  <Link to={`/lead-scoring-rules/${rule.id}`} className="font-medium text-slate-900 hover:underline">
                    {rule.name}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-500">
                  {MATCH_FIELD_LABELS[rule.matchField]} {rule.matchOperator === "EQUALS" ? "=" : "contains"} "{rule.matchValue}"
                </td>
                <td className={`px-4 py-3 font-medium ${rule.points < 0 ? "text-red-600" : "text-emerald-700"}`}>
                  {rule.points > 0 ? `+${rule.points}` : rule.points}
                </td>
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

      <CreateLeadScoringRuleForm onCreated={reload} />
    </div>
  );
}

function CreateLeadScoringRuleForm({ onCreated }: { onCreated: () => void }) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<CreateLeadScoringRuleFormValues>({
    resolver: zodResolver(createLeadScoringRuleSchema),
    defaultValues: { name: "", matchField: "", matchOperator: "", matchValue: "", points: "10" },
  });

  const matchField = useWatch({ control, name: "matchField" }) as LeadScoringMatchField | "";

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await createLeadScoringRule({
        name: values.name,
        matchField: values.matchField as LeadScoringMatchField,
        matchOperator: values.matchOperator as LeadScoringMatchOperator,
        matchValue: values.matchValue,
        points: toRequiredNumber(values.points),
      });
      reset({ name: "", matchField: "", matchOperator: "", matchValue: "", points: "10" });
      onCreated();
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
      <h2 className="text-sm font-medium text-slate-900">New scoring rule</h2>

      {formError && <Alert variant="error">{formError}</Alert>}

      <TextField label="Name" error={errors.name?.message} {...register("name")} />

      <div className="grid gap-4 sm:grid-cols-3">
        <Select
          label="Field"
          placeholder="Choose a field"
          options={LEAD_SCORING_MATCH_FIELDS.map((f) => ({ value: f, label: MATCH_FIELD_LABELS[f] }))}
          error={errors.matchField?.message}
          {...register("matchField")}
        />
        <Select
          label="Operator"
          placeholder="Choose an operator"
          options={LEAD_SCORING_MATCH_OPERATORS.map((o) => ({ value: o, label: o === "EQUALS" ? "Equals" : "Contains" }))}
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
          <TextField
            label="Value"
            placeholder={matchField === "EMAIL_DOMAIN" ? "e.g. acme.com" : undefined}
            error={errors.matchValue?.message}
            {...register("matchValue")}
          />
        )}
      </div>

      <div className="flex flex-col gap-1">
        <TextField label="Points" type="number" error={errors.points?.message} {...register("points")} />
        <p className="text-xs text-slate-400">Can be negative to penalize a score.</p>
      </div>

      <div className="flex justify-end">
        <Button type="submit" isLoading={isSubmitting}>
          Create rule
        </Button>
      </div>
    </form>
  );
}
