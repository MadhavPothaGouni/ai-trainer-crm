import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { listMembershipPlans } from "../../api/membershipPlans";
import { deleteMembership, getMembership, updateMembership, updateMembershipStatus } from "../../api/memberships";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, toOptionalNumber, updateMembershipSchema, type UpdateMembershipFormValues } from "../../lib/validation";
import {
  MEMBERSHIP_STATUSES,
  type ContactDto,
  type MembershipDto,
  type MembershipPlanDto,
  type MembershipStatus,
} from "../../types/api";
import { MembershipStatusBadge } from "./MembershipListPage";

export default function MembershipDetailPage() {
  const { membershipId } = useParams<{ membershipId: string }>();
  const navigate = useNavigate();
  const [membership, setMembership] = useState<MembershipDto | null>(null);
  const [contacts, setContacts] = useState<ContactDto[]>([]);
  const [plans, setPlans] = useState<MembershipPlanDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!membershipId) return;
    let cancelled = false;
    getMembership(membershipId)
      .then((data) => {
        if (!cancelled) setMembership(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this membership.");
      });
    listContacts({ size: 100, sort: "lastName,asc" })
      .then((res) => {
        if (!cancelled) setContacts(res.content);
      })
      .catch(() => undefined);
    listMembershipPlans({ size: 100, sort: "name,asc" })
      .then((res) => {
        if (!cancelled) setPlans(res.content);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [membershipId]);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<UpdateMembershipFormValues>({ resolver: zodResolver(updateMembershipSchema) });

  useEffect(() => {
    if (!membership) return;
    reset({
      endDate: membership.endDate ?? "",
      nextBillingDate: membership.nextBillingDate ?? "",
      autoRenew: membership.autoRenew,
      remainingCredits: membership.remainingCredits != null ? String(membership.remainingCredits) : "",
      notes: membership.notes ?? "",
    });
  }, [membership, reset]);

  const onSaveEdits = handleSubmit(async (values) => {
    if (!membershipId) return;
    setEditError(null);
    try {
      const updated = await updateMembership(membershipId, {
        endDate: blankToUndefined(values.endDate),
        nextBillingDate: blankToUndefined(values.nextBillingDate),
        autoRenew: values.autoRenew ?? true,
        remainingCredits: toOptionalNumber(values.remainingCredits),
        notes: blankToUndefined(values.notes),
      });
      setMembership(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!membershipId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateMembershipStatus(membershipId, { status: status as MembershipStatus });
      setMembership(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!membershipId || !window.confirm("Delete this membership? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteMembership(membershipId);
      navigate("/memberships");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this membership.");
      setIsDeleting(false);
    }
  }

  if (error && !membership) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!membership) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  const linkedContact = contacts.find((contact) => contact.id === membership.contactId);
  const linkedPlan = plans.find((plan) => plan.id === membership.membershipPlanId);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/memberships" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Memberships
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">
              {linkedContact?.fullName ?? "Membership"} {linkedPlan ? `- ${linkedPlan.name}` : ""}
            </h1>
            <MembershipStatusBadge status={membership.status} />
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
            <Row
              label="Plan"
              value={
                linkedPlan && (
                  <Link to={`/membership-plans/${linkedPlan.id}`} className="text-slate-900 hover:underline">
                    {linkedPlan.name}
                  </Link>
                )
              }
            />
            <Row label="Billing cycle price" value={`${membership.billingCyclePrice.toLocaleString()}`} />
            <Row label="Start date" value={membership.startDate} />
            <Row label="End date" value={membership.endDate ?? undefined} />
            <Row label="Next billing date" value={membership.nextBillingDate ?? undefined} />
            <Row label="Remaining credits" value={membership.remainingCredits != null ? String(membership.remainingCredits) : "Unlimited"} />
            <Row label="Paused at" value={membership.pausedAt ? new Date(membership.pausedAt).toLocaleString() : undefined} />
            <Row label="Cancelled at" value={membership.cancelledAt ? new Date(membership.cancelledAt).toLocaleString() : undefined} />
          </dl>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Status</h2>
          <p className="mt-1 text-xs text-slate-400">Memberships move freely between statuses - reactivating a paused or even cancelled membership is a normal correction.</p>
          <div className="mt-3">
            <Select
              label="Status"
              options={MEMBERSHIP_STATUSES.map((status) => ({ value: status, label: status }))}
              value={membership.status}
              disabled={isUpdatingStatus}
              onChange={(event) => void handleStatusChange(event.target.value)}
            />
          </div>
        </div>
      </div>

      <form onSubmit={onSaveEdits} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit membership</h2>

        {editError && <Alert variant="error">{editError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-3">
          <TextField label="End date" type="date" error={errors.endDate?.message} {...register("endDate")} />
          <TextField label="Next billing date" type="date" error={errors.nextBillingDate?.message} {...register("nextBillingDate")} />
          <TextField label="Remaining credits" type="number" min={0} step={1} error={errors.remainingCredits?.message} {...register("remainingCredits")} />
        </div>

        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" className="h-4 w-4 rounded border-slate-300" {...register("autoRenew")} />
          Auto-renews
        </label>

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
