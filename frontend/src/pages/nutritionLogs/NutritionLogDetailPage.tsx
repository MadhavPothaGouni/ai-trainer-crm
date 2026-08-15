import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteNutritionLog, getNutritionLog, updateNutritionLog } from "../../api/nutritionLogs";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  toOptionalNumber,
  updateNutritionLogSchema,
  type UpdateNutritionLogFormValues,
} from "../../lib/validation";
import { NUTRITION_LOG_MEAL_TYPES, type NutritionLogDto } from "../../types/api";

function toDatetimeLocalValue(iso: string): string {
  const date = new Date(iso);
  const offsetMs = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16);
}

export default function NutritionLogDetailPage() {
  const { nutritionLogId } = useParams<{ nutritionLogId: string }>();
  const navigate = useNavigate();
  const [log, setLog] = useState<NutritionLogDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateNutritionLogFormValues>({ resolver: zodResolver(updateNutritionLogSchema) });

  useEffect(() => {
    if (!nutritionLogId) return;
    let cancelled = false;
    getNutritionLog(nutritionLogId)
      .then((data) => {
        if (cancelled) return;
        setLog(data);
        reset({
          loggedAt: toDatetimeLocalValue(data.loggedAt),
          mealType: data.mealType,
          calories: data.calories != null ? String(data.calories) : "",
          proteinGrams: data.proteinGrams != null ? String(data.proteinGrams) : "",
          carbGrams: data.carbGrams != null ? String(data.carbGrams) : "",
          fatGrams: data.fatGrams != null ? String(data.fatGrams) : "",
          notes: data.notes ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this nutrition log.");
      });
    return () => {
      cancelled = true;
    };
  }, [nutritionLogId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!nutritionLogId) return;
    setFormError(null);
    try {
      const updated = await updateNutritionLog(nutritionLogId, {
        loggedAt: new Date(values.loggedAt).toISOString(),
        mealType: values.mealType as NutritionLogDto["mealType"],
        calories: toOptionalNumber(values.calories),
        proteinGrams: toOptionalNumber(values.proteinGrams),
        carbGrams: toOptionalNumber(values.carbGrams),
        fatGrams: toOptionalNumber(values.fatGrams),
        notes: blankToUndefined(values.notes),
      });
      setLog(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleDelete() {
    if (!nutritionLogId || !window.confirm("Delete this nutrition log? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteNutritionLog(nutritionLogId);
      navigate("/nutrition-logs");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this nutrition log.");
      setIsDeleting(false);
    }
  }

  if (error && !log) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!log) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/nutrition-logs" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Nutrition Logs
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-slate-900">{new Date(log.loggedAt).toLocaleString()}</h1>
          <p className="mt-1 text-sm text-slate-500">{log.mealType}</p>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Macros</h2>
        <dl className="mt-3 grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
          <Stat label="Calories" value={log.calories != null ? `${log.calories} kcal` : "—"} />
          <Stat label="Protein" value={log.proteinGrams != null ? `${log.proteinGrams} g` : "—"} />
          <Stat label="Carbs" value={log.carbGrams != null ? `${log.carbGrams} g` : "—"} />
          <Stat label="Fat" value={log.fatGrams != null ? `${log.fatGrams} g` : "—"} />
        </dl>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit log</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Logged at" type="datetime-local" error={errors.loggedAt?.message} {...register("loggedAt")} />
          <Select
            label="Meal"
            options={NUTRITION_LOG_MEAL_TYPES.map((type) => ({ value: type, label: type }))}
            error={errors.mealType?.message}
            {...register("mealType")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-4">
          <TextField label="Calories" type="number" min={0} error={errors.calories?.message} {...register("calories")} />
          <TextField label="Protein (g)" type="number" min={0} step="0.1" error={errors.proteinGrams?.message} {...register("proteinGrams")} />
          <TextField label="Carbs (g)" type="number" min={0} step="0.1" error={errors.carbGrams?.message} {...register("carbGrams")} />
          <TextField label="Fat (g)" type="number" min={0} step="0.1" error={errors.fatGrams?.message} {...register("fatGrams")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-400">{label}</dt>
      <dd className="mt-0.5 font-medium text-slate-900">{value}</dd>
    </div>
  );
}
