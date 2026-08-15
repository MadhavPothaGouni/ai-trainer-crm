import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { createReferral } from "../../api/referrals";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createReferralSchema, toOptionalNumber, type CreateReferralFormValues } from "../../lib/validation";
import type { ContactDto } from "../../types/api";

export default function ReferralCreatePage() {
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
  } = useForm<CreateReferralFormValues>({ resolver: zodResolver(createReferralSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const referral = await createReferral({
        referrerContactId: values.referrerContactId,
        referredName: values.referredName,
        referredEmail: blankToUndefined(values.referredEmail),
        referredPhone: blankToUndefined(values.referredPhone),
        rewardAmount: toOptionalNumber(values.rewardAmount),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/referrals/${referral.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">New referral</h1>
        <p className="mt-1 text-sm text-slate-500">A client referring someone they know.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <Select
          label="Referred by"
          placeholder="Select the referring client"
          options={contacts.map((contact) => ({ value: contact.id, label: contact.fullName }))}
          error={errors.referrerContactId?.message}
          {...register("referrerContactId")}
        />

        <TextField label="Referred person's name" error={errors.referredName?.message} {...register("referredName")} />

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Email" type="email" error={errors.referredEmail?.message} {...register("referredEmail")} />
          <TextField label="Phone" error={errors.referredPhone?.message} {...register("referredPhone")} />
        </div>

        <TextField
          label="Reward amount"
          type="number"
          min={0}
          step="0.01"
          placeholder="Leave blank if there's no reward"
          error={errors.rewardAmount?.message}
          {...register("rewardAmount")}
        />

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/referrals")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Create referral
          </Button>
        </div>
      </form>
    </div>
  );
}
