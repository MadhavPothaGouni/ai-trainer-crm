import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { deleteEmailMessage, getEmailMessage, updateEmailMessage } from "../../api/emailMessages";
import { RelatedToPicker } from "../../components/crm/RelatedToPicker";
import { Alert } from "../../components/ui/Alert";
import { Button } from "../../components/ui/Button";
import { Select } from "../../components/ui/Select";
import { TextArea } from "../../components/ui/TextArea";
import { TextField } from "../../components/ui/TextField";
import { ApiError } from "../../lib/apiClient";
import { applyServerErrors } from "../../lib/formErrors";
import { blankToUndefined, logEmailSchema, type LogEmailFormValues } from "../../lib/validation";
import { EMAIL_DIRECTIONS, type CrmRecordType, type EmailDirection, type EmailMessageDto } from "../../types/api";
import { EmailDirectionBadge } from "./EmailListPage";

/** Local yyyy-MM-ddThh:mm the <input type="datetime-local"> control expects - trims the seconds/offset an ISO Instant string carries. */
function toDatetimeLocal(iso: string): string {
  return new Date(iso).toISOString().slice(0, 16);
}

export default function EmailDetailPage() {
  const { emailId } = useParams<{ emailId: string }>();
  const navigate = useNavigate();
  const [email, setEmail] = useState<EmailMessageDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  useEffect(() => {
    if (!emailId) return;
    let cancelled = false;
    getEmailMessage(emailId)
      .then((data) => {
        if (!cancelled) setEmail(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : "Could not load this email.");
      });
    return () => {
      cancelled = true;
    };
  }, [emailId]);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting },
    setError: setEditFieldError,
  } = useForm<LogEmailFormValues>({ resolver: zodResolver(logEmailSchema) });

  useEffect(() => {
    if (!email) return;
    reset({
      direction: email.direction,
      subject: email.subject,
      body: email.body ?? "",
      fromAddress: email.fromAddress,
      toAddresses: email.toAddresses,
      ccAddresses: email.ccAddresses ?? "",
      relatedToType: email.relatedToType,
      relatedToId: email.relatedToId,
      sentAt: toDatetimeLocal(email.sentAt),
    });
  }, [email, reset]);

  const relatedToType = watch("relatedToType") ?? "";
  const relatedToId = watch("relatedToId") ?? "";

  const onSaveEdits = handleSubmit(async (values) => {
    if (!emailId) return;
    setEditError(null);
    try {
      const updated = await updateEmailMessage(emailId, {
        direction: values.direction as EmailDirection,
        subject: values.subject,
        body: blankToUndefined(values.body),
        fromAddress: values.fromAddress,
        toAddresses: values.toAddresses,
        ccAddresses: blankToUndefined(values.ccAddresses),
        relatedToType: values.relatedToType as CrmRecordType,
        relatedToId: values.relatedToId,
        sentAt: values.sentAt ? new Date(values.sentAt).toISOString() : undefined,
      });
      setEmail(updated);
    } catch (error) {
      setEditError(applyServerErrors(error, setEditFieldError));
    }
  });

  async function handleDelete() {
    if (!emailId || !window.confirm("Delete this logged email? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      await deleteEmailMessage(emailId);
      navigate("/emails");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete this email.");
      setIsDeleting(false);
    }
  }

  if (error && !email) {
    return <Alert variant="error">{error}</Alert>;
  }

  if (!email) {
    return <p className="text-sm text-slate-400">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/emails" className="text-sm text-slate-500 hover:text-slate-900 hover:underline">
            &larr; Emails
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{email.subject}</h1>
            <EmailDirectionBadge direction={email.direction} />
          </div>
        </div>
        <Button variant="danger" onClick={() => void handleDelete()} isLoading={isDeleting}>
          Delete
        </Button>
      </div>

      {error && <Alert variant="error">{error}</Alert>}

      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-500">Overview</h2>
        <dl className="mt-3 flex flex-col gap-2 text-sm">
          <Row label="From" value={email.fromAddress} />
          <Row label="To" value={email.toAddresses} />
          <Row label="Cc" value={email.ccAddresses} />
          <Row label="Sent at" value={new Date(email.sentAt).toLocaleString()} />
        </dl>
      </div>

      {email.body && (
        <div className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-medium text-slate-500">Body</h2>
          <p className="mt-3 whitespace-pre-wrap text-sm text-slate-900">{email.body}</p>
        </div>
      )}

      <form onSubmit={onSaveEdits} noValidate className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="text-sm font-medium text-slate-900">Edit</h2>

        {editError && <Alert variant="error">{editError}</Alert>}

        <div className="grid gap-4 sm:grid-cols-3">
          <Select
            label="Direction"
            options={EMAIL_DIRECTIONS.map((direction) => ({ value: direction, label: direction }))}
            error={errors.direction?.message}
            {...register("direction")}
          />
          <TextField label="From" error={errors.fromAddress?.message} {...register("fromAddress")} />
          <TextField label="To" error={errors.toAddresses?.message} {...register("toAddresses")} />
        </div>

        <TextField label="Cc" error={errors.ccAddresses?.message} {...register("ccAddresses")} />
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

        <div className="flex justify-end">
          <Button type="submit" isLoading={isSubmitting}>
            Save changes
          </Button>
        </div>
      </form>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right text-slate-900">{value ?? "—"}</dd>
    </div>
  );
}
