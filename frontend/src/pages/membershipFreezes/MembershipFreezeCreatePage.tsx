import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate, useSearchParams } from "react-router-dom";
import { createMembershipFreeze } from "../../api/membershipFreezes";
import { listMemberships } from "../../api/memberships";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createMembershipFreezeSchema, type CreateMembershipFreezeFormValues } from "../../lib/validation";
import type { MembershipDto } from "../../types/api";

export default function MembershipFreezeCreatePage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const preselectedMembershipId = searchParams.get("membershipId") ?? undefined;
  const [formError, setFormError] = useState<string | null>(null);
  const [memberships, setMemberships] = useState<MembershipDto[]>([]);

  useEffect(() => {
    listMemberships({ size: 100, sort: "startDate,desc" })
      .then((res) => setMemberships(res.content))
      .catch(() => setMemberships([]));
  }, []);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateMembershipFreezeFormValues>({
    resolver: zodResolver(createMembershipFreezeSchema),
    defaultValues: { membershipId: preselectedMembershipId ?? "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const freeze = await createMembershipFreeze({
        membershipId: values.membershipId,
        freezeStart: values.freezeStart,
        freezeEnd: values.freezeEnd,
        reason: blankToUndefined(values.reason),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/membership-freezes/${freeze.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Request a freeze</h1>
        <p className="mt-1 text-sm text-slate-500">A membership can't hold two overlapping freezes - the dates you pick must be free.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <Select
          label="Membership"
          placeholder="Select a membership"
          options={memberships.map((membership) => ({
            value: membership.id,
            label: `${membership.startDate} (${membership.status})`,
          }))}
          error={errors.membershipId?.message}
          {...register("membershipId")}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Freeze start" type="date" error={errors.freezeStart?.message} {...register("freezeStart")} />
          <TextField label="Freeze end" type="date" error={errors.freezeEnd?.message} {...register("freezeEnd")} />
        </div>

        <TextField label="Reason" error={errors.reason?.message} {...register("reason")} />
        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/membership-freezes")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Request freeze
          </Button>
        </div>
      </form>
    </div>
  );
}
