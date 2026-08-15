import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { createNutritionLog } from "../../api/nutritionLogs";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createNutritionLogSchema, toOptionalNumber, type CreateNutritionLogFormValues } from "../../lib/validation";
import { NUTRITION_LOG_MEAL_TYPES, type ContactDto, type NutritionLogMealType } from "../../types/api";

export default function NutritionLogCreatePage() {
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
  } = useForm<CreateNutritionLogFormValues>({
    resolver: zodResolver(createNutritionLogSchema),
    defaultValues: { mealType: "BREAKFAST" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const log = await createNutritionLog({
        contactId: values.contactId,
        loggedAt: new Date(values.loggedAt).toISOString(),
        mealType: values.mealType as NutritionLogMealType,
        calories: toOptionalNumber(values.calories),
        proteinGrams: toOptionalNumber(values.proteinGrams),
        carbGrams: toOptionalNumber(values.carbGrams),
        fatGrams: toOptionalNumber(values.fatGrams),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/nutrition-logs/${log.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Log a meal</h1>
        <p className="mt-1 text-sm text-slate-500">Macros are optional - leave any of them blank if the client didn't track them.</p>
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
          <Select
            label="Meal"
            options={NUTRITION_LOG_MEAL_TYPES.map((type) => ({ value: type, label: type }))}
            error={errors.mealType?.message}
            {...register("mealType")}
          />
        </div>

        <TextField label="Logged at" type="datetime-local" error={errors.loggedAt?.message} {...register("loggedAt")} />

        <div className="grid gap-4 sm:grid-cols-4">
          <TextField label="Calories" type="number" min={0} error={errors.calories?.message} {...register("calories")} />
          <TextField label="Protein (g)" type="number" min={0} step="0.1" error={errors.proteinGrams?.message} {...register("proteinGrams")} />
          <TextField label="Carbs (g)" type="number" min={0} step="0.1" error={errors.carbGrams?.message} {...register("carbGrams")} />
          <TextField label="Fat (g)" type="number" min={0} step="0.1" error={errors.fatGrams?.message} {...register("fatGrams")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/nutrition-logs")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Log meal
          </Button>
        </div>
      </form>
    </div>
  );
}
