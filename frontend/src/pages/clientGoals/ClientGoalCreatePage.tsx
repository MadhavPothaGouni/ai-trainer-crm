import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createClientGoal } from "../../api/clientGoals";
import { listContacts } from "../../api/contacts";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createClientGoalSchema, toOptionalNumber, type CreateClientGoalFormValues } from "../../lib/validation";
import { CLIENT_GOAL_TYPES, type ClientGoalType, type ContactDto } from "../../types/api";

export default function ClientGoalCreatePage() {
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
  } = useForm<CreateClientGoalFormValues>({ resolver: zodResolver(createClientGoalSchema), defaultValues: { goalType: "CUSTOM" } });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const goal = await createClientGoal({
        contactId: values.contactId,
        title: values.title,
        goalType: values.goalType as ClientGoalType,
        metricUnit: blankToUndefined(values.metricUnit),
        startValue: toOptionalNumber(values.startValue),
        targetValue: toOptionalNumber(values.targetValue),
        currentValue: toOptionalNumber(values.currentValue),
        targetDate: blankToUndefined(values.targetDate),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/client-goals/${goal.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New client goal</h1>
        <p className="mt-1 text-sm text-slate-500">A measurable objective for one of your clients.</p>
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
            label="Goal type"
            options={CLIENT_GOAL_TYPES.map((type) => ({ value: type, label: type.replace("_", " ") }))}
            error={errors.goalType?.message}
            {...register("goalType")}
          />
        </div>

        <TextField label="Title" error={errors.title?.message} {...register("title")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Metric unit" placeholder="lbs, reps, 5k time..." error={errors.metricUnit?.message} {...register("metricUnit")} />
          <TextField label="Target date" type="date" error={errors.targetDate?.message} {...register("targetDate")} />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="Start value" type="number" step="any" error={errors.startValue?.message} {...register("startValue")} />
          <TextField label="Target value" type="number" step="any" error={errors.targetValue?.message} {...register("targetValue")} />
          <TextField label="Current value" type="number" step="any" error={errors.currentValue?.message} {...register("currentValue")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/client-goals")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create goal
          </Button>
        </div>
      </form>
    </div>
  );
}
