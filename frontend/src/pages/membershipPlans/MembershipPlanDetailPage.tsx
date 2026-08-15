import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteMembershipPlan, getMembershipPlan, updateMembershipPlan } from "../../api/membershipPlans";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import {
  blankToUndefined,
  createMembershipPlanSchema,
  toOptionalNumber,
  toRequiredNumber,
  type CreateMembershipPlanFormValues,
} from "../../lib/validation";
import { MEMBERSHIP_BILLING_CYCLES, type MembershipBillingCycle, type MembershipPlanDto } from "../../types/api";

export default function MembershipPlanDetailPage() {
  const { membershipPlanId } = useParams<{ membershipPlanId: string }>();
  const navigate = useNavigate();
  const [plan, setPlan] = useState<MembershipPlanDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<CreateMembershipPlanFormValues>({ resolver: zodResolver(createMembershipPlanSchema) });

  useEffect(() => {
    if (!membershipPlanId) return;
    let cancelled = false;
    getMembershipPlan(membershipPlanId)
      .then((data) => {
        if (cancelled) return;
        setPlan(data);
        reset({
          name: data.name,
          description: data.description ?? "",
          billingCycle: data.billingCycle,
          price: String(data.price),
          currency: data.currency ?? "",
          sessionCredits: data.sessionCredits != null ? String(data.sessionCredits) : "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this plan.");
      });
    return () => {
      cancelled = true;
    };
  }, [membershipPlanId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!membershipPlanId || !plan) return;
    setFormError(null);
    try {
      const updated = await updateMembershipPlan(membershipPlanId, {
        name: values.name,
        description: blankToUndefined(values.description),
        billingCycle: values.billingCycle as MembershipBillingCycle,
        price: toRequiredNumber(values.price),
        currency: blankToUndefined(values.currency),
        sessionCredits: toOptionalNumber(values.sessionCredits),
        active: plan.active,
      });
      setPlan(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function toggleActive() {
    if (!membershipPlanId || !plan) return;
    try {
      const updated = await updateMembershipPlan(membershipPlanId, {
        name: plan.name,
        description: plan.description ?? undefined,
        billingCycle: plan.billingCycle,
        price: plan.price,
        currency: plan.currency ?? undefined,
        sessionCredits: plan.sessionCredits ?? undefined,
        active: !plan.active,
      });
      setPlan(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update this plan.");
    }
  }

  async function handleDelete() {
    if (!membershipPlanId || !window.confirm("Delete this plan? Existing memberships keep their own snapshot of the price, so this is safe.")) return;
    setIsDeleting(true);
    try {
      await deleteMembershipPlan(membershipPlanId);
      navigate("/membership-plans");
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

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/membership-plans" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Membership Plans
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{plan.name}</h1>
            {plan.active ? (
              <span className="inline-block rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-700">Active</span>
            ) : (
              <span className="inline-block rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-500">Inactive</span>
            )}
          </div>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" onClick={() => void toggleActive()}>
            {plan.active ? "Deactivate" : "Activate"}
          </Button>
          <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
            Delete
          </Button>
        </div>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

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

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}
