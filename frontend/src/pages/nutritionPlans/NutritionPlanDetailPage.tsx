import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { deleteNutritionPlan, getNutritionPlan, updateNutritionPlan, updateNutritionPlanStatus } from "../../api/nutritionPlans";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, toOptionalNumber, updateNutritionPlanSchema, type UpdateNutritionPlanFormValues } from "../../lib/validation";
import { NUTRITION_PLAN_STATUSES, type ContactDto, type NutritionPlanDto, type NutritionPlanStatus } from "../../types/api";
import { NutritionPlanStatusBadge } from "./NutritionPlanListPage";

export default function NutritionPlanDetailPage() {
  const { nutritionPlanId } = useParams<{ nutritionPlanId: string }>();
  const navigate = useNavigate();
  const [plan, setPlan] = useState<NutritionPlanDto | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!nutritionPlanId) return;
    let cancelled = false;
    getNutritionPlan(nutritionPlanId)
      .then((data) => {
        if (!cancelled) setPlan(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this plan.");
      });
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => {
        if (!cancelled) setContacts(res.content);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [nutritionPlanId]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<UpdateNutritionPlanFormValues>({ resolver: zodResolver(updateNutritionPlanSchema) });

  useEffect(() => {
    if (!plan) return;
    reset({
      title: plan.title,
      dailyCalorieTarget: plan.dailyCalorieTarget != null ? String(plan.dailyCalorieTarget) : "",
      proteinTargetGrams: plan.proteinTargetGrams != null ? String(plan.proteinTargetGrams) : "",
      carbTargetGrams: plan.carbTargetGrams != null ? String(plan.carbTargetGrams) : "",
      fatTargetGrams: plan.fatTargetGrams != null ? String(plan.fatTargetGrams) : "",
      startDate: plan.startDate ?? "",
      endDate: plan.endDate ?? "",
      notes: plan.notes ?? "",
    });
  }, [plan, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!nutritionPlanId) return;
    setEditError(null);
    try {
      const updated = await updateNutritionPlan(nutritionPlanId, {
        title: values.title,
        dailyCalorieTarget: toOptionalNumber(values.dailyCalorieTarget),
        proteinTargetGrams: toOptionalNumber(values.proteinTargetGrams),
        carbTargetGrams: toOptionalNumber(values.carbTargetGrams),
        fatTargetGrams: toOptionalNumber(values.fatTargetGrams),
        startDate: blankToUndefined(values.startDate),
        endDate: blankToUndefined(values.endDate),
        notes: blankToUndefined(values.notes),
      });
      setPlan(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!nutritionPlanId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateNutritionPlanStatus(nutritionPlanId, { status: status as NutritionPlanStatus });
      setPlan(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!nutritionPlanId || !window.confirm("Delete this plan? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteNutritionPlan(nutritionPlanId);
      navigate("/nutrition-plans");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this plan.");
      setIsDeleting(false);
    }
  }

  if (error && !plan) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!plan) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const linkedContact = contacts.find((contact) => contact.id === plan.contactId);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/nutrition-plans" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Nutrition Plans
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{plan.title}</h1>
            <NutritionPlanStatusBadge status={plan.status} />
          </div>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Overview</h2>
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <Row
              label="Client"
              value={
                linkedContact && (
                  <Link to={`/contacts/${linkedContact.id}`} className="text-slate-900 hover:underline">
                    {linkedContact.fullName}
                  </Link>
                )
              }
            />
            <Row label="Calories" value={plan.dailyCalorieTarget != null ? `${plan.dailyCalorieTarget} kcal` : undefined} />
            <Row
              label="Macros (P/C/F)"
              value={
                plan.proteinTargetGrams != null || plan.carbTargetGrams != null || plan.fatTargetGrams != null
                  ? `${plan.proteinTargetGrams ?? "?"}g / ${plan.carbTargetGrams ?? "?"}g / ${plan.fatTargetGrams ?? "?"}g`
                  : undefined
              }
            />
            <Row label="Start date" value={plan.startDate ?? undefined} />
            <Row label="End date" value={plan.endDate ?? undefined} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">Plans move freely between statuses - reactivating an archived plan is normal.</p>
          <div className="mt-3">
            <Select
              label="Status"
              options={NUTRITION_PLAN_STATUSES.map((status) => ({ value: status, label: status }))}
              value={plan.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <form onSubmit={onSaveEdits} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit plan</h2>

        {editError && <Alert variant="error">{editError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Title" error={errors.title?.message} {...register("title")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Start date" type="date" error={errors.startDate?.message} {...register("startDate")} />
          <TextField label="End date" type="date" error={errors.endDate?.message} {...register("endDate")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-4">
          <TextField label="Calories" type="number" step="any" error={errors.dailyCalorieTarget?.message} {...register("dailyCalorieTarget")} />
          <TextField label="Protein (g)" type="number" step="any" error={errors.proteinTargetGrams?.message} {...register("proteinTargetGrams")} />
          <TextField label="Carbs (g)" type="number" step="any" error={errors.carbTargetGrams?.message} {...register("carbTargetGrams")} />
          <TextField label="Fat (g)" type="number" step="any" error={errors.fatTargetGrams?.message} {...register("fatTargetGrams")} />
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

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right text-slate-900">{value ?? "—"}</dd>
    </div>
  );
}
