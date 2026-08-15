import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { listMembershipPlans } from "../../api/membershipPlans";
import { createMembership } from "../../api/memberships";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createMembershipSchema, type CreateMembershipFormValues } from "../../lib/validation";
import type { ContactDto, MembershipPlanDto } from "../../types/api";

export default function MembershipCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [plans, setPlans] = useState<MembershipPlanDto[]>([]);

  useEffect(() => {
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => setContacts(res.content))
      .catch(() => setContacts([]));
    listMembershipPlans({ size: 100, sort: "name,asc" })
      .then((res) => setPlans(res.content.filter((plan) => plan.active)))
      .catch(() => setPlans([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateMembershipFormValues>({ resolver: zodResolver(createMembershipSchema), defaultValues: { autoRenew: true } });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const membership = await createMembership({
        contactId: values.contactId,
        membershipPlanId: values.membershipPlanId,
        startDate: values.startDate,
        nextBillingDate: blankToUndefined(values.nextBillingDate),
        autoRenew: values.autoRenew ?? true,
        notes: blankToUndefined(values.notes),
      });
      navigate(`/memberships/${membership.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New membership</h1>
        <p className="mt-1 text-sm text-slate-500">Sign a client up for a membership plan. The plan's current price and credits are snapshotted at creation.</p>
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
            label="Membership plan"
            placeholder="Select a plan"
            options={plans.map((plan) => ({ value: plan.id, label: `${plan.name} (${plan.price} ${plan.currency ?? ""})`.trim() }))}
            error={errors.membershipPlanId?.message}
            {...register("membershipPlanId")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Start date" type="date" error={errors.startDate?.message} {...register("startDate")} />
          <TextField label="Next billing date" type="date" error={errors.nextBillingDate?.message} {...register("nextBillingDate")} />
        </div>

        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" className="h-4 w-4 rounded border-slate-300" {...register("autoRenew")} />
          Auto-renews
        </label>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/memberships")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create membership
          </Button>
        </div>
      </form>
    </div>
  );
}
