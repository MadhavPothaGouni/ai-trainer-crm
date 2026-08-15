import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { listContacts } from "../../api/contacts";
import { createNoShowRecord } from "../../api/noShowRecords";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, createNoShowRecordSchema, toOptionalNumber, type CreateNoShowRecordFormValues } from "../../lib/validation";
import { NO_SHOW_RELATED_TYPES, type ContactDto, type NoShowRelatedType } from "../../types/api";

export default function NoShowRecordCreatePage() {
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
  } = useForm<CreateNoShowRecordFormValues>({
    resolver: zodResolver(createNoShowRecordSchema),
    defaultValues: { relatedType: "OTHER" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const record = await createNoShowRecord({
        contactId: values.contactId,
        occurredAt: new Date(values.occurredAt).toISOString(),
        relatedType: values.relatedType as NoShowRelatedType,
        feeAmount: toOptionalNumber(values.feeAmount),
        notes: blankToUndefined(values.notes),
      });
      navigate(`/no-show-records/${record.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Log a no-show</h1>
        <p className="mt-1 text-sm text-slate-500">Leave the fee blank if none applies - a fee has to be set before it can be waived.</p>
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
            label="Type"
            options={NO_SHOW_RELATED_TYPES.map((type) => ({ value: type, label: type }))}
            error={errors.relatedType?.message}
            {...register("relatedType")}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Occurred at" type="datetime-local" error={errors.occurredAt?.message} {...register("occurredAt")} />
          <TextField label="Fee amount" type="number" min={0} step="0.01" error={errors.feeAmount?.message} {...register("feeAmount")} />
        </div>

        <TextArea label="Notes" error={errors.notes?.message} {...register("notes")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/no-show-records")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Log no-show
          </Button>
        </div>
      </form>
    </div>
  );
}
