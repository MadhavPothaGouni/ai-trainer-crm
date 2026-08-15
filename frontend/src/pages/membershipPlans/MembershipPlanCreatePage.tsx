import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { createMembershipPlan } from "../../api/membershipPlans";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  createMembershipPlanSchema,
  toOptionalNumber,
  toRequiredNumber,
  type CreateMembershipPlanFormValues,
} from "../../lib/validation";
import { MEMBERSHIP_BILLING_CYCLES, type MembershipBillingCycle } from "../../types/api";

export default function MembershipPlanCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateMembershipPlanFormValues>({
    resolver: zodResolver(createMembershipPlanSchema),
    defaultValues: { billingCycle: "MONTHLY" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const plan = await createMembershipPlan({
        name: values.name,
        description: blankToUndefined(values.description),
        billingCycle: values.billingCycle as MembershipBillingCycle,
        price: toRequiredNumber(values.price),
        currency: blankToUndefined(values.currency),
        sessionCredits: toOptionalNumber(values.sessionCredits),
      });
      navigate(`/membership-plans/${plan.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New membership plan</h1>
        <p className="mt-1 text-sm text-slate-500">Add a recurring billing plan clients can subscribe to.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <TextField label="Name" error={errors.name?.message} {...register("name")} />

        <div className="grid gap-4 sm:grid-cols-3">
          <Select
            label="Billing cycle"
            options={MEMBERSHIP_BILLING_CYCLES.map((cycle) => ({ value: cycle, label: cycle.replace("_", " ") }))}
            error={errors.billingCycle?.message}
            {...register("billingCycle")}
          />
          <TextField label="Price" type="number" min={0} step="any" error={errors.price?.message} {...register("price")} />
          <TextField label="Currency" placeholder="USD" error={errors.currency?.message} {...register("currency")} />
        </div>

        <TextField
          label="Session credits"
          type="number"
          min={1}
          step={1}
          placeholder="Leave blank for unlimited"
          error={errors.sessionCredits?.message}
          {...register("sessionCredits")}
        />

        <TextArea label="Description" error={errors.description?.message} {...register("description")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/membership-plans")}>
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
