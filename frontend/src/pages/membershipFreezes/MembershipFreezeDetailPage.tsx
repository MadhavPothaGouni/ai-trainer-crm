import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  deleteMembershipFreeze,
  getMembershipFreeze,
  updateMembershipFreeze,
  updateMembershipFreezeStatus,
} from "../../api/membershipFreezes";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, updateMembershipFreezeSchema, type UpdateMembershipFreezeFormValues } from "../../lib/validation";
import { MEMBERSHIP_FREEZE_STATUSES, type MembershipFreezeDto, type MembershipFreezeStatus } from "../../types/api";
import { MembershipFreezeStatusBadge } from "./MembershipFreezeListPage";

export default function MembershipFreezeDetailPage() {
  const { membershipFreezeId } = useParams<{ membershipFreezeId: string }>();
  const navigate = useNavigate();
  const [freeze, setFreeze] = useState<MembershipFreezeDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setError: setFieldError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateMembershipFreezeFormValues>({ resolver: zodResolver(updateMembershipFreezeSchema) });

  useEffect(() => {
    if (!membershipFreezeId) return;
    let cancelled = false;
    getMembershipFreeze(membershipFreezeId)
      .then((data) => {
        if (cancelled) return;
        setFreeze(data);
        reset({
          freezeStart: data.freezeStart,
          freezeEnd: data.freezeEnd,
          reason: data.reason ?? "",
          notes: data.notes ?? "",
        });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this membership freeze.");
      });
    return () => {
      cancelled = true;
    };
  }, [membershipFreezeId, reset]);

  const onSubmit = handleSubmit(async (values) => {
    if (!membershipFreezeId) return;
    setFormError(null);
    try {
      const updated = await updateMembershipFreeze(membershipFreezeId, {
        freezeStart: values.freezeStart,
        freezeEnd: values.freezeEnd,
        reason: blankToUndefined(values.reason),
        notes: blankToUndefined(values.notes),
      });
      setFreeze(updated);
    } catch (error) {
      setFormError(applyServerErrors(error, setFieldError));
    }
  });

  async function handleStatusChange(status: string) {
    if (!membershipFreezeId) return;
    setIsUpdatingStatus(true);
    setError(null);
    try {
      const updated = await updateMembershipFreezeStatus(membershipFreezeId, { status: status as MembershipFreezeStatus });
      setFreeze(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update the status.");
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function handleDelete() {
    if (!membershipFreezeId || !window.confirm("Delete this membership freeze? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteMembershipFreeze(membershipFreezeId);
      navigate("/membership-freezes");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this membership freeze.");
      setIsDeleting(false);
    }
  }

  if (error && !freeze) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!freeze) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/membership-freezes" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Membership Freezes
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">
              {freeze.freezeStart} &ndash; {freeze.freezeEnd}
            </h1>
            <MembershipFreezeStatusBadge status={freeze.status} />
          </div>
          {freeze.reason && <p className="mt-1 text-sm text-slate-500">{freeze.reason}</p>}
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Status</h2>
        <p className="mt-1 text-xs text-slate-400">
          Freezes move freely between statuses - moving an ENDED freeze back to ACTIVE re-checks for date conflicts.
        </p>
        <div className="mt-3 max-w-xs">
          <Select
            label="Status"
            options={MEMBERSHIP_FREEZE_STATUSES.map((status) => ({ value: status, label: status }))}
            value={freeze.status}
            disabled={isUpdatingStatus}
            onChange={(event) => void handleStatusChange(event.target.value)}
          />
        </div>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit freeze</h2>

        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Freeze start" type="date" error={errors.freezeStart?.message} {...register("freezeStart")} />
          <TextField label="Freeze end" type="date" error={errors.freezeEnd?.message} {...register("freezeEnd")} />
        </div>

        <TextField label="Reason" error={errors.reason?.message} {...register("reason")} />
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
