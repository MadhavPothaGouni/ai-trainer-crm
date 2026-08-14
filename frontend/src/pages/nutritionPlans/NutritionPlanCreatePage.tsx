import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { createNutritionPlan } from "../../api/nutritionPlans";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createNutritionPlanSchema, toOptionalNumber, type CreateNutritionPlanFormValues } from "../../lib/validation";
import type { ContactDto } from "../../types/api";

export default function NutritionPlanCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);

  useEffect(() => {
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateNutritionPlanFormValues>({ resolver: zodResolver(createNutritionPlanSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const plan = await createNutritionPlan({
        contactId: values.contactId,
        title: values.title,
        dailyCalorieTarget: toOptionalNumber(values.dailyCalorieTarget),
        proteinTargetGrams: toOptionalNumber(values.proteinTargetGrams),
        carbTargetGrams: toOptionalNumber(values.carbTargetGrams),
        fatTargetGrams: toOptionalNumber(values.fatTargetGrams),
        startDate: blankToUndefined(values.startDate),
        endDate: blankToUndefined(values.endDate),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/nutrition-plans/${plan.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New nutrition plan</h1>
        <p className="mt-1 text-sm text-slate-500">Dietary and macro guidance for one of your clients.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Client"
            placeholder="Select a contact"
            options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
            error={errors.contactId?.message}
            {...register("contactId")}
          />
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

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/nutrition-plans")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create plan
          </Button>
        </div>
      </form>
    </div>
  );
}
