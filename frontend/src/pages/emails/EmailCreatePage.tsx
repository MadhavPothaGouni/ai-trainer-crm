import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { logEmailMessage } from "../../api/emailMessages";
import { RelatedToPicker } from "../../components/crm/RelatedToPicker";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, logEmailSchema, type LogEmailFormValues } from "../../lib/validation";
import { EMAIL_DIRECTIONS, type CrmRecordType, type EmailDirection } from "../../types/api";

export default function EmailCreatePage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LogEmailFormValues>({
    resolver: zodResolver(logEmailSchema),
    defaultValues: { direction: "OUTBOUND", relatedToType: "", relatedToId: "" },
  });

  const relatedToType = watch("relatedToType") ?? "";
  const relatedToId = watch("relatedToId") ?? "";

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const email = await logEmailMessage({
        direction: values.direction as EmailDirection,
        subject: values.subject,
        body: blankToUndefined(values.body),
        fromAddress: values.fromAddress,
        toAddresses: values.toAddresses,
        ccAddresses: blankToUndefined(values.ccAddresses),
        relatedToType: values.relatedToType as CrmRecordType,
        relatedToId: values.relatedToId,
        // datetime-local inputs report local time with no offset - ActivityTimeline's dueAt
        // field established this same new Date(...).toISOString() conversion before this.
        sentAt: values.sentAt ? new Date(values.sentAt).toISOString() : undefined,
      });
      navigate(`/emails/${email.id}`);
    } catch (error) {
      setFormError(applyServerErrors(error, setError));
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Log an email</h1>
        <p className="mt-1 text-sm text-slate-500">Record an email you sent or received against an Account, Contact, Opportunity, Lead, or Ticket.</p>
      </div>

      <form onSubmit={onSubmit} noValidate className="flex max-w-2xl flex-col gap-4 rounded-lg border border-slate-200 bg-white p-6">
        {formError && <Alert variant="error">{formError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-3">
          <Select
            label="Direction"
            options={EMAIL_DIRECTIONS.map((direction) => ({ value: direction, label: direction }))}
            error={errors.direction?.message}
            {...register("direction")}
          />
          <TextField label="From" error={errors.fromAddress?.message} {...register("fromAddress")} />
          <TextField label="To" placeholder="comma-separated" error={errors.toAddresses?.message} {...register("toAddresses")} />
        </div>

        <TextField label="Cc" placeholder="comma-separated, optional" error={errors.ccAddresses?.message} {...register("ccAddresses")} />

        <TextField label="Subject" error={errors.subject?.message} {...register("subject")} />

        <RelatedToPicker
          relatedToType={relatedToType}
          relatedToId={relatedToId}
          onChange={(type, id) => {
            setValue("relatedToType", type, { shouldValidate: true });
            setValue("relatedToId", id, { shouldValidate: true });
          }}
          typeError={errors.relatedToType?.message}
          idError={errors.relatedToId?.message}
        />

        <TextField label="Sent at" type="datetime-local" error={errors.sentAt?.message} {...register("sentAt")} />

        <TextArea label="Body" error={errors.body?.message} {...register("body")} />

        <div className="flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate("/emails")}>
            Cancel
          </Button>
          <Button type="submit" isLoading={isSubmitting}>
            Log email
          </Button>
        </div>
      </form>
    </div>
  );
}
